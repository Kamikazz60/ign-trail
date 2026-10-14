package fr.igntrail.offline

import fr.igntrail.core.geo.Corridor
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.core.geo.Quad
import fr.igntrail.core.geo.TileId
import fr.igntrail.core.geo.TileMath
import fr.igntrail.core.geo.TileSizeModel
import fr.igntrail.core.geo.coverage
import kotlinx.coroutines.flow.StateFlow

enum class OfflineKind { AREA, CORRIDOR }

/** Niveaux de détail proposés (zoom maximal téléchargé). */
enum class DetailLevel(val maxZoom: Int, val label: String, val hint: String) {
    OVERVIEW(14, "Essentiel", "chemins et relief, moins précis"),
    HIKING(15, "Randonnée", "bon compromis"),
    DETAILED(16, "Détaillé", "précision maximale"),
}

/** Demande de téléchargement : une zone libre ou une bande autour d'un parcours (§4). */
data class OfflineRequest(
    val name: String,
    val kind: OfflineKind,
    val detail: DetailLevel,
    val bounds: GeoBounds,
    /** Bande autour d'un parcours ; null pour une zone rectangulaire. */
    val corridor: List<Quad>? = null,
    val sessionId: Long? = null,
) {
    /** Tuiles nécessaires par niveau de zoom. */
    fun tileCount(z: Int): Long =
        corridor?.let { Corridor.tiles(it, z).size.toLong() } ?: TileMath.countInBounds(bounds, z)

    val estimate: OfflineEstimate by lazy {
        var tiles = 0L
        var bytes = TileSizeModel.STYLE_OVERHEAD_BYTES
        for (z in MIN_ZOOM..detail.maxZoom) {
            val n = tileCount(z)
            tiles += n
            bytes += n * TileSizeModel.bytesPerTile(z)
        }
        OfflineEstimate(tiles, bytes, bounds.areaKm2)
    }

    /** Tuiles de référence pour vérifier la couverture (zoom 14, commun à tous les niveaux). */
    fun coverageTiles(): Set<TileId> = corridor?.let { Corridor.tiles(it, COVERAGE_ZOOM) }
        ?: TileMath.tilesInBounds(bounds, COVERAGE_ZOOM)

    companion object {
        /** Les vues d'ensemble (zooms 4 à 13) sont incluses : quelques tuiles seulement. */
        const val MIN_ZOOM = 4
        const val COVERAGE_ZOOM = 14

        /** Au-delà, on refuse : préférer plusieurs zones plus petites. */
        const val MAX_TILES = 150_000L
        const val WARN_BYTES = 300L * 1024 * 1024
    }
}

data class OfflineEstimate(val tiles: Long, val bytes: Long, val areaKm2: Double)

sealed interface DownloadState {
    data class Downloading(val progress: Float) : DownloadState

    /** Interrompu (pause, application fermée, réseau perdu) : reprise possible. */
    data class Incomplete(val progress: Float) : DownloadState

    data object Complete : DownloadState

    data class Failed(val message: String) : DownloadState
}

data class OfflineRegionInfo(
    val id: Long,
    val name: String,
    val kind: OfflineKind,
    val detail: DetailLevel,
    val createdMillis: Long,
    val bounds: GeoBounds,
    val corridor: List<Quad>?,
    val state: DownloadState,
    val sizeBytes: Long,
    val completedResources: Long,
    val requiredResources: Long,
) {
    val coverageTiles: Set<TileId> by lazy {
        corridor?.let { Corridor.tiles(it, OfflineRequest.COVERAGE_ZOOM) }
            ?: TileMath.tilesInBounds(bounds, OfflineRequest.COVERAGE_ZOOM)
    }
}

/** Disponibilité hors ligne d'une zone (§4 : « avant le départ, afficher… »). */
enum class Availability(val label: String) {
    AVAILABLE("Carte disponible hors ligne"),
    PARTIAL("Carte partiellement disponible hors ligne"),
    UNAVAILABLE("Carte indisponible hors ligne"),
}

/**
 * Fournisseur de cartes hors ligne. Interface indépendante de MapLibre : il peut être
 * remplacé (MBTiles, PMTiles…) sans toucher à l'enregistrement GPS (§4).
 */
interface OfflineMaps {
    val regions: StateFlow<List<OfflineRegionInfo>>

    fun refresh()

    /** Crée la zone et lance le téléchargement. Retourne un message d'erreur si refus. */
    suspend fun download(request: OfflineRequest): String?

    fun pause(id: Long)

    fun resume(id: Long)

    suspend fun delete(id: Long)

    /** Vide le cache automatique (tuiles vues en ligne), sans toucher aux zones téléchargées. */
    suspend fun clearTemporaryCache()

    /** Tuiles (zoom 14) des zones complètes. */
    fun completeTiles(): Set<TileId> = regions.value.filter { it.state == DownloadState.Complete }.flatMapTo(HashSet()) { it.coverageTiles }

    /** Tuiles (zoom 14) des zones en cours ou incomplètes. */
    fun partialTiles(): Set<TileId> = regions.value.filter { it.state != DownloadState.Complete }.flatMapTo(HashSet()) { it.coverageTiles }

    /** Part de [needed] déjà téléchargée (0 à 1). */
    fun coverageRatio(needed: Set<TileId>): Double = coverage(needed, completeTiles())

    fun availability(needed: Set<TileId>): Availability {
        val ratio = coverageRatio(needed)
        return when {
            ratio >= 0.999 -> Availability.AVAILABLE
            ratio > 0.0 -> Availability.PARTIAL
            else -> Availability.UNAVAILABLE
        }
    }
}
