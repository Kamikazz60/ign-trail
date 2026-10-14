package fr.igntrail.map

import fr.igntrail.data.settings.BaseMap

/**
 * Sources cartographiques de la Géoplateforme IGN, toutes sous Licence Ouverte
 * Etalab 2.0, sans clé. Endpoints vérifiés le 13/10/2026.
 *
 * Point d'extension unique pour changer de fournisseur (cahier des charges §4).
 */
object IgnLayers {
    private const val STYLES = "https://data.geopf.fr/annexes/ressources/vectorTiles/styles/PLAN.IGN"
    private const val WMTS = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"

    const val PLAN_IGN_CLASSIQUE = "$STYLES/classique.json"
    const val PLAN_IGN_STANDARD = "$STYLES/standard.json"

    /** Identifiant commun de la source et de la couche d'ombrage injectées dans le style. */
    const val HILLSHADE_ID = "igntrail-hillshade"

    /** Vrai pour les styles Plan IGN dans lesquels on injecte l'ombrage. */
    fun isPlanIgnStyle(url: String): Boolean = url.startsWith(PLAN_IGN_CLASSIQUE) || url.startsWith(PLAN_IGN_STANDARD)

    /** Ombrage du relief (RGE ALTI), disponible jusqu'au zoom 15 puis agrandi. */
    const val HILLSHADE_TILES = "$WMTS&LAYER=ELEVATION.ELEVATIONGRIDCOVERAGE.SHADOW" +
        "&STYLE=estompage_grayscale&FORMAT=image/png"
    const val HILLSHADE_MAX_ZOOM = 15f

    private const val ORTHO_TILES = "$WMTS&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&FORMAT=image/jpeg"

    const val ATTRIBUTION = "© IGN – Géoplateforme · Licence Ouverte Etalab 2.0"

    /** Style MapLibre minimal pour les photographies aériennes (raster). */
    val orthoStyleJson = """
        {
          "version": 8,
          "name": "Orthophotos IGN",
          "sources": {
            "ortho": { "type": "raster", "tiles": ["$ORTHO_TILES"], "tileSize": 256, "maxzoom": 19 }
          },
          "layers": [
            { "id": "background", "type": "background", "paint": { "background-color": "#DDE3D6" } },
            { "id": "ortho", "type": "raster", "source": "ortho" }
          ]
        }
    """.trimIndent()

    fun styleUri(baseMap: BaseMap): String? = when (baseMap) {
        BaseMap.PLAN_IGN_CLASSIQUE -> PLAN_IGN_CLASSIQUE
        BaseMap.PLAN_IGN_STANDARD -> PLAN_IGN_STANDARD
        BaseMap.ORTHOPHOTOS -> null
    }

    fun label(baseMap: BaseMap): String = when (baseMap) {
        BaseMap.PLAN_IGN_CLASSIQUE -> "Plan IGN classique"
        BaseMap.PLAN_IGN_STANDARD -> "Plan IGN standard"
        BaseMap.ORTHOPHOTOS -> "Photographies aériennes"
    }
}
