package fr.igntrail

import android.app.Application
import fr.igntrail.core.model.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Context
import fr.igntrail.backup.BackupManager
import fr.igntrail.diagnostic.CrashLog
import fr.igntrail.data.location.Barometer
import fr.igntrail.data.location.CompassHeading
import fr.igntrail.data.location.LocationSource
import fr.igntrail.data.location.LocationSources
import fr.igntrail.data.settings.DisplayPreferences
import fr.igntrail.data.settings.MapPreferences
import fr.igntrail.data.settings.NavigationPreferences
import fr.igntrail.data.settings.RecordingPreferences
import fr.igntrail.data.storage.PoiRepository
import fr.igntrail.data.storage.RouteRepository
import fr.igntrail.data.storage.TrackRepository
import fr.igntrail.data.storage.TrailDatabase
import fr.igntrail.map.IgnStyleInterceptor
import fr.igntrail.navigation.GuidanceAlerts
import fr.igntrail.navigation.NavigationManager
import fr.igntrail.offline.MapLibreOfflineMaps
import fr.igntrail.offline.NetworkMonitor
import fr.igntrail.offline.OfflineMaps
import fr.igntrail.tracking.RecordingManager
import fr.igntrail.tracking.TrackingNotifications
import java.io.File
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class IgnTrailApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Aucune clé : les tuiles IGN Plan IGN / ombrage / orthophotos sont en accès libre.
        MapLibre.getInstance(this)
        // Client HTTP de MapLibre : adapte les styles Plan IGN (relief, zoom max), limite la charge sur l'IGN.
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .dispatcher(Dispatcher().apply { maxRequestsPerHost = 12 })
                .addInterceptor(IgnStyleInterceptor())
                .build(),
        )
        TrackingNotifications.createChannels(this)
        // Sauvegarde à restaurer (préparée avant le redémarrage) : appliquée avant d'ouvrir la base.
        BackupManager.applyPendingRestore(this)
        container = AppContainer(this)
    }
}

/** Injection de dépendances manuelle (voir décision technique dans PROJECT_STATE.md). */
class AppContainer(context: Context) {
    /** Carte : position rapide au démarrage (fused : satellites, Wi-Fi, antennes). */
    val locationSource: LocationSource = LocationSources.create(context)

    /** Enregistrement et guidage : satellites uniquement (voir [LocationSources.createSatellite]). */
    val trackingLocationSource: LocationSource = LocationSources.createSatellite(context)

    /**
     * Dernière position reçue par le service d'enregistrement / guidage, rediffusée à la carte :
     * la carte reste à jour même si son propre abonnement GPS cesse d'être servi quand le
     * service s'abonne à son tour (constaté sur émulateur).
     */
    val liveFix = MutableStateFlow<LocationFix?>(null)
    val mapPreferences = MapPreferences(context)
    val compass = CompassHeading(context)
    val barometer = Barometer(context)
    val displayPreferences = DisplayPreferences(context)
    private val database = TrailDatabase.create(context)
    val trackRepository = TrackRepository(database)
    val recordingManager = RecordingManager(context, trackRepository, RecordingPreferences(context))
    val network = NetworkMonitor(context)
    val routeRepository = RouteRepository(database)
    val poiRepository = PoiRepository(database, File(context.filesDir, "photos").apply { mkdirs() })
    val navigationManager = NavigationManager(context, NavigationPreferences(context), GuidanceAlerts(context))
    val offlineMaps: OfflineMaps by lazy { MapLibreOfflineMaps(context) }
}

val Context.appContainer: AppContainer get() = (applicationContext as IgnTrailApp).container
