package fr.igntrail.routing

import fr.igntrail.core.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Le service n'a pas pu répondre (pas de réseau, erreur serveur) : repli en ligne droite. */
class RoutingUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Calcul d'itinéraire entre deux points (§8.2). Interface interchangeable :
 * IGN aujourd'hui, un moteur hors ligne (BRouter, GraphHopper…) en V2.
 */
interface RoutingProvider {
    val label: String

    /** Tracé sur les chemins, de [from] à [to]. */
    suspend fun route(from: GeoPoint, to: GeoPoint): List<GeoPoint>
}

/** Altitudes d'une liste de points (null si inconnue, par exemple hors de France). */
interface ElevationProvider {
    suspend fun elevations(points: List<GeoPoint>): List<Double?>
}

/** Respect de la limite de la Géoplateforme : 5 requêtes par seconde et par adresse IP. */
private class Throttle(private val minIntervalMs: Long = 250) {
    private val mutex = Mutex()
    private var last = 0L

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = last + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            last = System.currentTimeMillis()
        }
    }
}

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

private val throttle = Throttle()

/**
 * Service de calcul d'itinéraire de la Géoplateforme IGN (graphe BD TOPO, profil piéton,
 * chemin le plus court). Sans clé. Seules les coordonnées des deux points sont transmises.
 */
class IgnRoutingProvider : RoutingProvider {
    override val label = "IGN – Géoplateforme (BD TOPO®)"

    override suspend fun route(from: GeoPoint, to: GeoPoint): List<GeoPoint> = throttle.run {
        withContext(Dispatchers.IO) {
            val url = ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("resource", "bdtopo-osrm")
                .addQueryParameter("profile", "pedestrian")
                .addQueryParameter("optimization", "shortest")
                .addQueryParameter("start", "${from.longitude},${from.latitude}")
                .addQueryParameter("end", "${to.longitude},${to.latitude}")
                .addQueryParameter("geometryFormat", "geojson")
                .addQueryParameter("getSteps", "false")
                .build()
            val body = execute(Request.Builder().url(url).build())
            val coords = JSONObject(body).getJSONObject("geometry").getJSONArray("coordinates")
            (0 until coords.length()).map { i ->
                val c = coords.getJSONArray(i)
                GeoPoint(c.getDouble(1), c.getDouble(0))
            }
        }
    }

    companion object {
        private const val ENDPOINT = "https://data.geopf.fr/navigation/itineraire"
    }
}

/** Altimétrie de la Géoplateforme (modèle RGE ALTI®), par lots en requête POST. */
class IgnElevationProvider : ElevationProvider {
    override suspend fun elevations(points: List<GeoPoint>): List<Double?> =
        points.chunked(BATCH).flatMap { batch ->
            throttle.run {
                withContext(Dispatchers.IO) {
                    val json = JSONObject()
                        .put("lon", batch.joinToString("|") { "%.6f".format(java.util.Locale.ROOT, it.longitude) })
                        .put("lat", batch.joinToString("|") { "%.6f".format(java.util.Locale.ROOT, it.latitude) })
                        .put("resource", "ign_rge_alti_wld")
                        .put("zonly", "true")
                    val request = Request.Builder()
                        .url(ENDPOINT)
                        .post(json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val values = JSONObject(execute(request)).getJSONArray("elevations")
                    if (values.length() != batch.size) throw RoutingUnavailableException("Réponse d'altimétrie incomplète")
                    (0 until values.length()).map { i -> values.getDouble(i).takeIf { it > NO_DATA_BELOW } }
                }
            }
        }

    companion object {
        private const val ENDPOINT = "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevation.json"
        private const val BATCH = 2_000

        /** Le service renvoie -99999 hors de la zone couverte. */
        private const val NO_DATA_BELOW = -1_000.0
    }
}

private fun execute(request: Request): String = try {
    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw RoutingUnavailableException("Service IGN indisponible (HTTP ${response.code})")
        response.body?.string() ?: throw RoutingUnavailableException("Réponse vide du service IGN")
    }
} catch (e: IOException) {
    throw RoutingUnavailableException("Pas de connexion au service IGN", e)
}
