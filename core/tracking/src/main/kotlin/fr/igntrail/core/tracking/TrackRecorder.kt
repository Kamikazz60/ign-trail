package fr.igntrail.core.tracking

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix

/**
 * Cœur de l'enregistrement : filtrage GPS (§5.3), pauses (§6), chronomètres,
 * distance, vitesses, dénivelé et segments du tracé.
 *
 * Déterministe et sans dépendance Android : la même suite de mesures et
 * d'événements donne toujours le même résultat. Les statistiques sont donc
 * recalculables à partir des données brutes sauvegardées ([replay], §13).
 *
 * Chronomètres : temps total = déplacement + pause + interruption.
 */
class TrackRecorder(private val params: TrackingParams, val startMillis: Long) {

    var state: RecordingState = RecordingState.MOVING
        private set

    private var clockMillis = startMillis
    private var movingMs = 0L
    private var pausedMs = 0L
    private var interruptedMs = 0L

    private val filter = PositionFilter(params.accelNoise)
    private var distanceM = 0.0

    /** Dernier point du tracé (position filtrée). */
    private var lastAccepted: GeoPoint? = null
    private var lastAcceptedMillis = 0L
    private var startNewSegment = true
    private val segmentTimes = mutableListOf<AppendOnlyList<TimedPoint>>()

    private var lastGoodFixMillis = startMillis
    private var stationarySince: Long? = null
    private var autoPauseAnchor: GeoPoint? = null
    private var resumeCandidates = 0
    private var consecutiveOutliers = 0

    /** Récepteur annonçant une vitesse nulle en se déplaçant : on cesse de croire son Doppler. */
    private var dopplerUnreliable = false
    private var dopplerContradictions = 0

    private var smoothedSpeed = 0.0
    private var maxSpeed = 0.0
    private val speedWindow = ArrayDeque<Pair<Long, Double>>() // (heure, distance cumulée)

    /** Vitesses Doppler récentes (heure, m/s) : base de la vitesse maximale. */
    private val dopplerWindow = ArrayDeque<Pair<Long, Float>>()
    private var dopplerSeen = false

    /**
     * Positions brutes récentes (heure, position) : contrôle de la vitesse Doppler par le
     * déplacement mesuré, indépendant du filtre (qui, lui, s'appuie sur le Doppler).
     */
    private val rawHistory = ArrayDeque<Pair<Long, GeoPoint>>()

    /** Après un retour du signal, la puce annonce souvent des vitesses fausses : on attend. */
    private var maxSpeedSettledAt = 0L

    private val elevation = ElevationTracker(params.elevationThresholdM, params.maxVerticalAccuracyM)

    /** Incrémenté à chaque modification du tracé (pour rafraîchir la carte). */
    var trackVersion = 0
        private set

    /** Copie des segments ; deux segments ne sont jamais reliés entre eux. */
    fun segments(): List<List<GeoPoint>> = segmentTimes.map { seg -> seg.snapshot().map { it.point } }

    /** Segments avec l'heure de chaque point (coloration selon la vitesse, export GPX). */
    fun timedSegments(): List<List<TimedPoint>> =
        segmentTimes.map { it.snapshot() }

    /** Heure de la dernière donnée prise en compte (mesure ou commande). */
    val lastDataMillis: Long get() = clockMillis

    fun process(fix: LocationFix): PointQuality {
        val t = fix.timeMillis
        if (state == RecordingState.FINISHED) return PointQuality.PAUSED
        if (t < clockMillis - OUT_OF_ORDER_TOLERANCE_MS) return PointQuality.OUT_OF_ORDER
        advanceClock(t)

        if (state == RecordingState.MANUAL_PAUSED || state == RecordingState.INTERRUPTED) {
            // Altitude affichée tenue à jour, sans compter de dénivelé.
            elevation.add(fix.altitudeM, fix.verticalAccuracyM, counting = false, fix.pressureHpa)
            return PointQuality.PAUSED
        }

        val accuracy = fix.horizontalAccuracyM
        if (accuracy == null || accuracy > params.maxAccuracyM) return PointQuality.LOW_ACCURACY

        val previousGoodFix = lastGoodFixMillis
        val previous = lastAccepted
        if (previous == null || startNewSegment || !filter.isInitialized) {
            lastGoodFixMillis = t
            return startSegment(fix)
        }
        if (t - previousGoodFix > params.gapMs) {
            // Perte de signal prolongée : on ne relie pas les deux positions (§5.3).
            // En pause automatique, l'utilisateur a forcément repris la marche entre-temps.
            if (state == RecordingState.AUTO_PAUSED) state = RecordingState.MOVING
            lastGoodFixMillis = t
            return startSegment(fix)
        }

        // Courte perte de signal : les vitesses annoncées au retour sont peu fiables.
        if (t - previousGoodFix > DOPPLER_MAX_SAMPLE_GAP_MS) maxSpeedSettledAt = t + SETTLE_AFTER_GAP_MS

        // Saut incompatible avec la vitesse maximale de l'activité : point aberrant.
        val dt = (t - previousGoodFix).coerceAtLeast(1) / 1000.0
        val jump = GeoMath.distanceM(GeoPoint(fix.latitude, fix.longitude), filter.position())
        val implied = (jump - accuracy) / dt
        // Saut confirmé par la vitesse mesurée par la puce GPS : déplacement réel (voiture,
        // vélo…), pas un point aberrant, même au-delà de la vitesse prévue pour l'activité.
        val dopplerConfirms = !dopplerUnreliable && (fix.speedMps ?: 0f) >= implied * 0.7 && (fix.speedMps ?: 0f) > 2f
        if (implied > params.maxSpeedMps && !dopplerConfirms) {
            consecutiveOutliers++
            // Plusieurs sauts cohérents : c'est la position précédente qui était fausse.
            if (consecutiveOutliers < OUTLIER_RESET_COUNT) return PointQuality.OUTLIER
            lastGoodFixMillis = t
            return startSegment(fix)
        }
        consecutiveOutliers = 0
        lastGoodFixMillis = t
        if (state == RecordingState.MOVING) recordDoppler(fix)

        val filtered = filter.update(trusted(fix))
        return when (state) {
            RecordingState.AUTO_PAUSED -> whileAutoPaused(fix, filtered)
            else -> whileMoving(fix, filtered, previous)
        }
    }

    private fun whileMoving(fix: LocationFix, filtered: GeoPoint, previous: GeoPoint): PointQuality {
        val d = GeoMath.distanceM(filtered, previous)
        val noiseRadius = (2.0 * filter.positionSigmaM).coerceIn(params.minStepM, params.maxStepM)
        if (d >= noiseRadius) {
            stationarySince = null
            return addPoint(fix, filtered, addDistance = true)
        }
        // Immobile, ou trop lent pour sortir du bruit : on attend de s'éloigner davantage.
        elevation.add(fix.altitudeM, fix.verticalAccuracyM, counting = true, fix.pressureHpa)
        smoothedSpeed = smooth(currentSpeed(fix))
        val since = stationarySince ?: lastAcceptedMillis.also { stationarySince = it }
        val delay = params.autoPauseDelayMs
        val dopplerStill = (fix.speedMps ?: 0f) < params.stationarySpeedMps
        if (delay != null && fix.timeMillis - since >= delay && dopplerStill) {
            enterAutoPause(fix.timeMillis, since, previous)
        }
        return PointQuality.STATIONARY
    }

    private fun whileAutoPaused(fix: LocationFix, filtered: GeoPoint): PointQuality {
        val anchor = autoPauseAnchor ?: return addPoint(fix, filtered, addDistance = true)
        val resumeRadius = maxOf(params.autoResumeMinDistanceM, 3.0 * filter.positionSigmaM)
        if (GeoMath.distanceM(filtered, anchor) < resumeRadius) {
            resumeCandidates = 0
            elevation.add(fix.altitudeM, fix.verticalAccuracyM, counting = false, fix.pressureHpa)
            return PointQuality.STATIONARY
        }
        resumeCandidates++
        if (resumeCandidates < params.autoResumeFixes) return PointQuality.STATIONARY
        // Mouvement réel confirmé par plusieurs mesures : reprise, distance depuis le point d'arrêt.
        state = RecordingState.MOVING
        autoPauseAnchor = null
        resumeCandidates = 0
        stationarySince = null
        return addPoint(fix, filtered, addDistance = true)
    }

    private fun enterAutoPause(now: Long, since: Long, anchor: GeoPoint) {
        state = RecordingState.AUTO_PAUSED
        // Le temps immobile avant la détection est requalifié en pause.
        val retro = (now - since).coerceIn(0, movingMs)
        movingMs -= retro
        pausedMs += retro
        autoPauseAnchor = anchor
        resumeCandidates = 0
        smoothedSpeed = 0.0
    }

    private fun startSegment(fix: LocationFix): PointQuality {
        filter.reset(trusted(fix))
        segmentTimes.add(AppendOnlyList())
        speedWindow.clear()
        dopplerWindow.clear()
        rawHistory.clear()
        maxSpeedSettledAt = fix.timeMillis + SETTLE_AFTER_GAP_MS
        startNewSegment = false
        stationarySince = null
        autoPauseAnchor = null
        return addPoint(fix, filter.position(), addDistance = false)
    }

    private fun addPoint(fix: LocationFix, point: GeoPoint, addDistance: Boolean): PointQuality {
        val previous = lastAccepted
        if (addDistance && previous != null) {
            val step = GeoMath.distanceM(point, previous)
            distanceM += step
            checkDoppler(fix, step / ((fix.timeMillis - lastAcceptedMillis).coerceAtLeast(1) / 1000.0))
        }
        if (segmentTimes.isEmpty()) segmentTimes.add(AppendOnlyList())
        segmentTimes.last().add(TimedPoint(point, fix.timeMillis, fix.altitudeM, fix.pressureHpa))
        trackVersion++
        consecutiveOutliers = 0
        lastAccepted = point
        lastAcceptedMillis = fix.timeMillis
        smoothedSpeed = smooth(currentSpeed(fix))
        updateMaxSpeed(fix.timeMillis)
        updateDopplerMax(fix)
        elevation.add(fix.altitudeM, fix.verticalAccuracyM, counting = state == RecordingState.MOVING, fix.pressureHpa)
        return PointQuality.ACCEPTED
    }

    /** Vitesse Doppler si elle est fiable, sinon vitesse estimée par le filtre. */
    private fun currentSpeed(fix: LocationFix): Double = trusted(fix).speedMps?.toDouble() ?: filter.speedMps

    /** Mesure sans sa vitesse et son cap si le Doppler a été jugé non fiable. */
    private fun trusted(fix: LocationFix): LocationFix =
        if (dopplerUnreliable) fix.copy(speedMps = null, bearingDeg = null, bearingAccuracyDeg = null) else fix

    /**
     * Certains récepteurs (et l'émulateur) annoncent une vitesse nulle en permanence.
     * Plusieurs déplacements nets contredisant un Doppler nul suffisent à l'écarter.
     */
    private fun checkDoppler(fix: LocationFix, stepSpeedMps: Double) {
        if (dopplerUnreliable) return
        val doppler = fix.speedMps ?: return
        when {
            doppler < IMPLAUSIBLE_STILL_DOPPLER && stepSpeedMps > 0.8 -> {
                if (++dopplerContradictions >= DOPPLER_CONTRADICTIONS_MAX) dopplerUnreliable = true
            }
            doppler >= 0.5f -> dopplerContradictions = 0
        }
    }

    private fun smooth(sample: Double): Double =
        if (smoothedSpeed == 0.0) sample else smoothedSpeed + SPEED_ALPHA * (sample - smoothedSpeed)

    /**
     * Vitesse maximale à partir de la vitesse Doppler, mesurée par la puce GPS et bien plus
     * fiable que la distance entre deux positions : médiane sur 5 s, insensible à une valeur
     * isolée. Les sauts de position (fréquents à vitesse élevée) n'ont ainsi aucun effet.
     */
    private fun recordDoppler(fix: LocationFix) {
        val speed = fix.speedMps ?: return
        if (dopplerUnreliable) return
        dopplerSeen = true
        dopplerWindow.addLast(fix.timeMillis to speed)
        while (dopplerWindow.isNotEmpty() && fix.timeMillis - dopplerWindow.first().first > DOPPLER_WINDOW_MS) dopplerWindow.removeFirst()
    }

    /**
     * Vitesse maximale candidate : médiane Doppler sur une fenêtre de mesures continue
     * (au moins [DOPPLER_MIN_SPAN_MS], sans trou), hors des secondes qui suivent un retour
     * du signal, et plafonnée par la distance réellement parcourue sur la même fenêtre :
     * une vitesse annoncée par la puce que le déplacement ne confirme pas est ignorée.
     */
    private fun updateDopplerMax(fix: LocationFix) {
        val t = fix.timeMillis
        val here = GeoPoint(fix.latitude, fix.longitude)
        rawHistory.addLast(t to here)
        while (rawHistory.size > 2 && t - rawHistory[1].first >= DOPPLER_WINDOW_MS * 2) rawHistory.removeFirst()
        if (!dopplerSeen || dopplerUnreliable || t < maxSpeedSettledAt) return
        val window = dopplerWindow.filter { it.first >= maxSpeedSettledAt }
        if (window.size < DOPPLER_MIN_SAMPLES) return
        val first = window.first().first
        if (t - first < DOPPLER_MIN_SPAN_MS) return
        if (window.zipWithNext().any { (a, b) -> b.first - a.first > DOPPLER_MAX_SAMPLE_GAP_MS }) return
        val (t0, p0) = rawHistory.firstOrNull { it.first >= first } ?: return
        if (t - t0 < DOPPLER_MIN_SPAN_MS) return
        // Ligne droite entre les deux mesures : jamais plus que le chemin réel, bruit compris.
        val travelled = (GeoMath.distanceM(p0, here) + (fix.horizontalAccuracyM ?: 0f)) / ((t - t0) / 1000.0)
        val median = window.map { it.second }.sorted()[window.size / 2].toDouble()
        maxSpeed = maxOf(maxSpeed, minOf(median, travelled * DOPPLER_DISTANCE_TOLERANCE))
    }

    /**
     * Repli sans vitesse Doppler fiable : distance parcourue sur une fenêtre longue
     * (2 × maxSpeedWindowMs), pour diluer l'effet d'un saut de position.
     */
    private fun updateMaxSpeed(t: Long) {
        speedWindow.addLast(t to distanceM)
        if (dopplerSeen && !dopplerUnreliable) return
        val window = params.maxSpeedWindowMs * 2
        while (speedWindow.size > 2 && t - speedWindow[1].first >= window) {
            speedWindow.removeFirst()
        }
        val (t0, d0) = speedWindow.first()
        val span = t - t0
        if (span >= window) maxSpeed = maxOf(maxSpeed, (distanceM - d0) / (span / 1000.0))
    }

    // --- Commandes ---

    /** Pause manuelle : jamais annulée par la détection de mouvement (§6.2). */
    fun pauseManually(t: Long) {
        if (state != RecordingState.MOVING && state != RecordingState.AUTO_PAUSED) return
        advanceClock(t)
        state = RecordingState.MANUAL_PAUSED
        autoPauseAnchor = null
        stationarySince = null
        smoothedSpeed = 0.0
    }

    fun resumeManually(t: Long) {
        if (state != RecordingState.MANUAL_PAUSED) return
        advanceClock(t)
        state = RecordingState.MOVING
        // L'utilisateur a pu se déplacer pendant la pause : on ne relie pas.
        startNewSegment = true
    }

    /** Arrêt anormal du service : la période sans mesure n'est ni déplacement ni pause. */
    fun interrupt(lastDataMillis: Long) {
        if (state == RecordingState.FINISHED || state == RecordingState.INTERRUPTED) return
        advanceClock(lastDataMillis)
        state = RecordingState.INTERRUPTED
        smoothedSpeed = 0.0
    }

    fun resumeAfterInterruption(t: Long) {
        if (state != RecordingState.INTERRUPTED) return
        advanceClock(t)
        state = RecordingState.MOVING
        startNewSegment = true
        lastGoodFixMillis = t
    }

    fun finish(t: Long) {
        if (state == RecordingState.FINISHED) return
        advanceClock(t)
        state = RecordingState.FINISHED
        smoothedSpeed = 0.0
    }

    fun apply(event: SessionEvent) {
        when (event.type) {
            SessionEventType.START -> Unit
            SessionEventType.MANUAL_PAUSE -> pauseManually(event.timeMillis)
            SessionEventType.MANUAL_RESUME -> resumeManually(event.timeMillis)
            SessionEventType.INTERRUPTED -> interrupt(event.timeMillis)
            SessionEventType.RESUMED_AFTER_INTERRUPTION -> resumeAfterInterruption(event.timeMillis)
            SessionEventType.STOP -> finish(event.timeMillis)
        }
    }

    /** Fait avancer les chronomètres jusqu'à [t] dans l'état courant. */
    private fun advanceClock(t: Long) {
        if (t <= clockMillis) return
        val delta = t - clockMillis
        when (state) {
            RecordingState.MOVING -> movingMs += delta
            RecordingState.AUTO_PAUSED, RecordingState.MANUAL_PAUSED -> pausedMs += delta
            RecordingState.INTERRUPTED -> interruptedMs += delta
            RecordingState.FINISHED -> Unit
        }
        clockMillis = t
    }

    /** Statistiques à l'instant [nowMillis], sans modifier l'état. */
    fun stats(nowMillis: Long = clockMillis): RecordingStats {
        val recording = state == RecordingState.MOVING || state == RecordingState.AUTO_PAUSED
        return RecordingStats(
            state = state,
            startMillis = startMillis,
            computedAtMillis = clockMillis,
            distanceM = distanceM,
            movingMs = movingMs,
            pausedMs = pausedMs,
            interruptedMs = interruptedMs,
            currentSpeedMps = if (state == RecordingState.MOVING) smoothedSpeed else 0.0,
            maxSpeedMps = maxSpeed,
            currentAltitudeM = elevation.current,
            minAltitudeM = elevation.min,
            maxAltitudeM = elevation.max,
            elevationGainM = elevation.gain,
            elevationLossM = elevation.loss,
            altitudeSamples = elevation.samples,
            barometric = elevation.barometric,
            gpsSignalOk = !recording || nowMillis - lastGoodFixMillis <= params.signalLostAfterMs,
        ).at(nowMillis)
    }

    companion object {
        private const val SPEED_ALPHA = 0.3
        private const val OUTLIER_RESET_COUNT = 5
        private const val DOPPLER_WINDOW_MS = 5_000L
        private const val DOPPLER_MIN_SAMPLES = 3
        private const val DOPPLER_MIN_SPAN_MS = 3_000L
        private const val DOPPLER_MAX_SAMPLE_GAP_MS = 2_500L
        private const val DOPPLER_DISTANCE_TOLERANCE = 1.1
        private const val SETTLE_AFTER_GAP_MS = 5_000L
        private const val IMPLAUSIBLE_STILL_DOPPLER = 0.3f
        private const val DOPPLER_CONTRADICTIONS_MAX = 3

        /** Horloge GPS et horloge système peuvent différer d'une à deux secondes. */
        private const val OUT_OF_ORDER_TOLERANCE_MS = 2_000L

        /**
         * Recalcule une session à partir des mesures brutes et des événements
         * enregistrés, dans l'ordre chronologique. À heure égale, les commandes
         * précèdent la mesure, sauf l'arrêt qui la suit.
         */
        fun replay(
            params: TrackingParams,
            startMillis: Long,
            fixes: List<LocationFix>,
            events: List<SessionEvent>,
        ): TrackRecorder {
            val recorder = TrackRecorder(params, startMillis)
            val sorted = events.sortedBy { it.timeMillis }
            var e = 0
            fun appliesBefore(event: SessionEvent, fix: LocationFix) =
                event.timeMillis < fix.timeMillis ||
                    (event.timeMillis == fix.timeMillis && event.type != SessionEventType.STOP)
            for (fix in fixes) {
                while (e < sorted.size && appliesBefore(sorted[e], fix)) recorder.apply(sorted[e++])
                recorder.process(fix)
            }
            while (e < sorted.size) recorder.apply(sorted[e++])
            return recorder
        }
    }
}
