package fr.igntrail.map

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Adapte les styles Plan IGN au moment de leur téléchargement (l'IGN autorise
 * la modification de ses styles) :
 * - ajout de l'ombrage du relief : il fait ainsi partie du style et il est inclus
 *   dans les téléchargements hors ligne de MapLibre, qui ne récupèrent que les
 *   sources déclarées dans le style (§4) ; son affichage reste réglable ;
 * - tuiles vectorielles plafonnées au zoom 16, agrandies au-delà : rendu identique
 *   en ligne et hors ligne, sans télécharger les zooms 17-18 (numéros de rue).
 */
class IgnStyleInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!IgnLayers.isPlanIgnStyle(request.url.toString()) || !response.isSuccessful) return response
        val body = response.body ?: return response
        val original = body.string()
        val patched = runCatching { StyleInjection.patch(original) }.getOrDefault(original)
        return response.newBuilder()
            .removeHeader("Content-Length")
            .body(patched.toResponseBody(body.contentType()))
            .build()
    }
}

object StyleInjection {
    const val VECTOR_MAX_ZOOM = 16
    private const val PLAN_IGN_SOURCE = "plan_ign"
    private const val PLAN_IGN_TILES = "https://data.geopf.fr/tms/1.0.0/PLAN.IGN/{z}/{x}/{y}.pbf"

    /** Source vectorielle déclarée en tuiles directes, zoom maximal 16. */
    private val VECTOR_SOURCE =
        """{"type":"vector","tiles":["$PLAN_IGN_TILES"],"scheme":"xyz","minzoom":0,"maxzoom":$VECTOR_MAX_ZOOM}"""

    private val HILLSHADE_SOURCE =
        """"${IgnLayers.HILLSHADE_ID}":{"type":"raster","tiles":["${IgnLayers.HILLSHADE_TILES}"],""" +
            """"tileSize":256,"maxzoom":${IgnLayers.HILLSHADE_MAX_ZOOM.toInt()},"attribution":"© IGN – RGE ALTI®"},"""

    private val HILLSHADE_LAYER =
        """{"id":"${IgnLayers.HILLSHADE_ID}","type":"raster","source":"${IgnLayers.HILLSHADE_ID}",""" +
            """"paint":{"raster-opacity":0.35}},"""

    /**
     * Modifications ciblées, le reste du style restant identique à l'octet près :
     * - source vectorielle plafonnée au zoom 16 ;
     * - source et couche d'ombrage, insérées juste avant la première couche « line ».
     */
    fun patch(styleJson: String): String {
        val root = styleJson.indexOf('{')
        val members = JsonScanner.members(styleJson, root)
        val sources = members.first { it.key == "sources" }
        val layers = members.first { it.key == "layers" }
        val sourceMembers = JsonScanner.members(styleJson, sources.valueStart)

        // (position, longueur remplacée, texte inséré), appliquées de la fin vers le début.
        val edits = mutableListOf<Triple<Int, Int, String>>()
        sourceMembers.firstOrNull { it.key == PLAN_IGN_SOURCE }?.let {
            edits += Triple(it.valueStart, it.valueEnd - it.valueStart, VECTOR_SOURCE)
        }
        if (sourceMembers.none { it.key == IgnLayers.HILLSHADE_ID }) {
            edits += Triple(sources.valueStart + 1, 0, HILLSHADE_SOURCE)
            val elements = JsonScanner.elements(styleJson, layers.valueStart)
            val firstLine = elements.firstOrNull { range ->
                JsonScanner.members(styleJson, range.first).any { m ->
                    m.key == "type" && styleJson.substring(m.valueStart, m.valueEnd) == "\"line\""
                }
            }
            if (firstLine != null) edits += Triple(firstLine.first, 0, HILLSHADE_LAYER)
        }
        val out = StringBuilder(styleJson)
        edits.sortedByDescending { it.first }.forEach { (pos, len, text) -> out.replace(pos, pos + len, text) }
        return out.toString()
    }
}
