package fr.igntrail.navigation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import fr.igntrail.MainActivity
import fr.igntrail.R
import fr.igntrail.core.navigation.GuidanceEvent
import fr.igntrail.core.navigation.GuidanceState
import fr.igntrail.data.settings.GuidanceSettings
import fr.igntrail.tracking.TrackingNotifications
import fr.igntrail.ui.format.Format

/**
 * Alertes de guidage (§9) : vibration et son réglables, plus une notification
 * prioritaire en cas de sortie de parcours (visible écran verrouillé).
 * Une alerte par événement : l'hystérésis du guidage évite les répétitions.
 */
class GuidanceAlerts(private val context: Context) {
    fun signal(event: GuidanceEvent, settings: GuidanceSettings, state: GuidanceState?) {
        when (event) {
            GuidanceEvent.OFF_ROUTE -> {
                if (settings.vibrate) vibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                if (settings.sound) beep(ToneGenerator.TONE_PROP_BEEP2, 600)
                notifyOffRoute(state)
            }
            GuidanceEvent.BACK_ON_ROUTE -> {
                if (settings.vibrate) vibrate(longArrayOf(0, 150))
                if (settings.sound) beep(ToneGenerator.TONE_PROP_ACK, 200)
                clear()
            }
            GuidanceEvent.ARRIVED -> {
                if (settings.vibrate) vibrate(longArrayOf(0, 200, 100, 200, 100, 200))
                if (settings.sound) beep(ToneGenerator.TONE_PROP_ACK, 400)
            }
        }
    }

    fun clear() {
        NotificationManagerCompat.from(context).cancel(OFF_ROUTE_ID)
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            context.getSystemService<VibratorManager>()?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun beep(tone: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            generator.startTone(tone, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ generator.release() }, durationMs + 200L)
        }
    }

    private fun notifyOffRoute(state: GuidanceState?) {
        val distance = state?.let { " (${Format.distance(it.crossTrackM)} du tracé)" }.orEmpty()
        val notification = NotificationCompat.Builder(context, TrackingNotifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Vous avez quitté le parcours")
            .setContentText("Revenez vers le tracé$distance.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(OFF_ROUTE_ID, notification) }
    }

    companion object {
        private const val OFF_ROUTE_ID = 4
    }
}
