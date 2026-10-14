package fr.igntrail.offline

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.core.geo.Quad
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.map.IgnLayers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import kotlin.coroutines.resume

/**
 * Cartes hors ligne avec le gestionnaire intégré de MapLibre (vérifié dans le code
 * source 13.6.1) : reprise, comptage des ressources, région en forme de bande.
 *
 * Toutes les opérations MapLibre se font sur le fil principal.
 */
class MapLibreOfflineMaps(private val context: Context) : OfflineMaps {
    private val manager = OfflineManager.getInstance(context)
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)

    private val handles = mutableMapOf<Long, OfflineRegion>()
    private val _regions = MutableStateFlow<List<OfflineRegionInfo>>(emptyList())
    override val regions: StateFlow<List<OfflineRegionInfo>> = _regions.asStateFlow()

    private val lastUiUpdate = mutableMapOf<Long, Long>()

    init {
        refresh(resumeInterrupted = true)
    }

    override fun refresh() {
        refresh(resumeInterrupted = false)
    }

    /**
     * @param resumeInterrupted au démarrage de l'application, reprend les téléchargements
     * qui étaient en cours quand le processus s'est arrêté (§4 : reprise après interruption).
     */
    private fun refresh(resumeInterrupted: Boolean) {
        main.post { listRegions(resumeInterrupted) }
    }

    private fun listRegions(resumeInterrupted: Boolean) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val list = offlineRegions.orEmpty()
                list.forEach { region ->
                    handles[region.id] = region
                    if (_regions.value.none { it.id == region.id }) upsert(region, null)
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null) upsert(region, status)
                            val wasActive = region.id in activeIds()
                            if (resumeInterrupted && wasActive && status?.isComplete == false) resume(region.id)
                        }

                        override fun onError(error: String?) = Unit
                    })
                }
                _regions.update { current -> current.filter { info -> list.any { it.id == info.id } } }
            }

            override fun onError(error: String) {
                Log.e(TAG, "Liste des zones hors ligne : $error")
            }
        })
    }

    override suspend fun download(request: OfflineRequest): String? {
        val estimate = request.estimate
        if (estimate.tiles > OfflineRequest.MAX_TILES) {
            return "Zone trop grande (${estimate.tiles} tuiles). Réduisez la zone ou le niveau de détail."
        }
        val free = context.filesDir.usableSpace
        if (estimate.bytes * 12 / 10 > free) return "Espace de stockage insuffisant sur le téléphone."

        val pixelRatio = context.resources.displayMetrics.density
        val definition = if (request.corridor != null) {
            val polygons = request.corridor.map { quad ->
                val ring = (quad + quad.first()).map { Point.fromLngLat(it.longitude, it.latitude) }
                listOf(ring)
            }
            OfflineGeometryRegionDefinition(
                IgnLayers.PLAN_IGN_CLASSIQUE,
                MultiPolygon.fromLngLats(polygons),
                OfflineRequest.MIN_ZOOM.toDouble(),
                request.detail.maxZoom.toDouble(),
                pixelRatio,
            )
        } else {
            val b = request.bounds
            OfflineTilePyramidRegionDefinition(
                IgnLayers.PLAN_IGN_CLASSIQUE,
                LatLngBounds.from(b.north, b.east, b.south, b.west),
                OfflineRequest.MIN_ZOOM.toDouble(),
                request.detail.maxZoom.toDouble(),
                pixelRatio,
            )
        }
        val metadata = Metadata(request.name, request.kind, request.detail, System.currentTimeMillis(), request.bounds, request.corridor)
        return suspendCancellableCoroutine { cont ->
            main.post {
                manager.createOfflineRegion(definition, metadata.encode(), object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        handles[offlineRegion.id] = offlineRegion
                        upsert(offlineRegion, null)
                        resume(offlineRegion.id)
                        cont.resume(null)
                    }

                    override fun onError(error: String) {
                        cont.resume("Création impossible : $error")
                    }
                })
            }
        }
    }

    override fun resume(id: Long) {
        main.post {
            val region = handles[id] ?: return@post
            setActive(id, true)
            region.setObserver(observer(region))
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            mark(id) { it.copy(state = DownloadState.Downloading(progressOf(it))) }
            // Refusé si l'application est en arrière-plan : le téléchargement continue alors sans service.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, OfflineDownloadService::class.java)) }
        }
    }

    override fun pause(id: Long) {
        main.post {
            setActive(id, false)
            handles[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
            mark(id) { it.copy(state = DownloadState.Incomplete(progressOf(it))) }
        }
    }

    override suspend fun delete(id: Long) {
        suspendCancellableCoroutine { cont ->
            main.post {
                val region = handles[id]
                if (region == null) {
                    cont.resume(Unit)
                    return@post
                }
                setActive(id, false)
                region.setObserver(null)
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        handles.remove(id)
                        _regions.update { list -> list.filterNot { it.id == id } }
                        // Récupère l'espace disque libéré.
                        manager.packDatabase(null)
                        cont.resume(Unit)
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Suppression impossible : $error")
                        cont.resume(Unit)
                    }
                })
            }
        }
    }

    override suspend fun clearTemporaryCache() {
        suspendCancellableCoroutine { cont ->
            main.post {
                manager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = cont.resume(Unit)
                    override fun onError(message: String) = cont.resume(Unit)
                })
            }
        }
    }

    private fun observer(region: OfflineRegion) = object : OfflineRegion.OfflineRegionObserver {
        override fun onStatusChanged(status: OfflineRegionStatus) {
            if (status.isComplete) {
                // Intégrité : toutes les ressources requises sont présentes (§4).
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                setActive(region.id, false)
                upsert(region, status)
                return
            }
            // Mise à jour de l'interface au plus deux fois par seconde.
            val now = System.currentTimeMillis()
            if (now - (lastUiUpdate[region.id] ?: 0L) < 500) return
            lastUiUpdate[region.id] = now
            upsert(region, status)
        }

        override fun onError(error: OfflineRegionError) {
            // Les erreurs réseau sont temporaires : MapLibre réessaie tout seul.
            Log.w(TAG, "Zone ${region.id} : ${error.reason} ${error.message}")
            if (error.reason == OfflineRegionError.REASON_NOT_FOUND || error.reason == OfflineRegionError.REASON_OTHER) {
                mark(region.id) { it.copy(state = DownloadState.Failed(error.message)) }
            }
        }

        override fun mapboxTileCountLimitExceeded(limit: Long) = Unit
    }

    private fun upsert(region: OfflineRegion, status: OfflineRegionStatus?) {
        val meta = Metadata.decode(region.metadata) ?: return
        val active = region.id in activeIds()
        val required = status?.requiredResourceCount ?: 0L
        val completed = status?.completedResourceCount ?: 0L
        val progress = if (required > 0) completed.toFloat() / required else 0f
        val state = when {
            status == null -> _regions.value.firstOrNull { it.id == region.id }?.state ?: DownloadState.Incomplete(0f)
            status.isComplete -> DownloadState.Complete
            active -> DownloadState.Downloading(progress)
            else -> DownloadState.Incomplete(progress)
        }
        val info = OfflineRegionInfo(
            id = region.id,
            name = meta.name,
            kind = meta.kind,
            detail = meta.detail,
            createdMillis = meta.created,
            bounds = meta.bounds,
            corridor = meta.corridor,
            state = state,
            sizeBytes = status?.completedResourceSize ?: _regions.value.firstOrNull { it.id == region.id }?.sizeBytes ?: 0L,
            completedResources = completed,
            requiredResources = required,
        )
        _regions.update { list ->
            (list.filterNot { it.id == region.id } + info).sortedByDescending { it.createdMillis }
        }
    }

    private fun mark(id: Long, change: (OfflineRegionInfo) -> OfflineRegionInfo) {
        _regions.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun progressOf(info: OfflineRegionInfo): Float =
        if (info.requiredResources > 0) info.completedResources.toFloat() / info.requiredResources else 0f

    /** Téléchargements en cours, mémorisés pour reprendre après un arrêt de l'application. */
    private fun activeIds(): Set<Long> = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun setActive(id: Long, active: Boolean) {
        val ids = activeIds().toMutableSet()
        if (active) ids += id else ids -= id
        prefs.edit { putStringSet(KEY_ACTIVE, ids.map { it.toString() }.toSet()) }
    }

    /** Métadonnées stockées avec chaque zone dans la base MapLibre. */
    private data class Metadata(
        val name: String,
        val kind: OfflineKind,
        val detail: DetailLevel,
        val created: Long,
        val bounds: GeoBounds,
        val corridor: List<Quad>?,
    ) {
        fun encode(): ByteArray = JSONObject()
            .put("v", 1)
            .put("name", name)
            .put("kind", kind.name)
            .put("detail", detail.name)
            .put("created", created)
            .put("bounds", JSONArray(listOf(bounds.south, bounds.west, bounds.north, bounds.east)))
            .apply {
                corridor?.let { quads ->
                    put("corridor", JSONArray(quads.map { q -> JSONArray(q.flatMap { listOf(it.latitude, it.longitude) }) }))
                }
            }
            .toString().toByteArray(Charsets.UTF_8)

        companion object {
            fun decode(bytes: ByteArray): Metadata? = runCatching {
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                val b = o.getJSONArray("bounds")
                val corridor = o.optJSONArray("corridor")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val q = arr.getJSONArray(i)
                        (0 until q.length() step 2).map { GeoPoint(q.getDouble(it), q.getDouble(it + 1)) }
                    }
                }
                Metadata(
                    name = o.getString("name"),
                    kind = OfflineKind.valueOf(o.getString("kind")),
                    detail = DetailLevel.valueOf(o.getString("detail")),
                    created = o.getLong("created"),
                    bounds = GeoBounds(b.getDouble(0), b.getDouble(1), b.getDouble(2), b.getDouble(3)),
                    corridor = corridor,
                )
            }.getOrNull()
        }
    }

    companion object {
        private const val TAG = "OfflineMaps"
        private const val KEY_ACTIVE = "active_downloads"
    }
}
