package fr.igntrail

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import fr.igntrail.ui.AppRoot
import fr.igntrail.ui.theme.IgnTrailTheme

class MainActivity : ComponentActivity() {
    /** Fichier GPX reçu par « Ouvrir avec » ou « Partager » (§9, §14). */
    private var incomingGpx by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Icônes de la barre d'état sombres : elle est posée sur la carte, claire.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incomingGpx = gpxUri(intent)
        setContent {
            IgnTrailTheme { AppRoot(incomingGpx, onIncomingGpxConsumed = { incomingGpx = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gpxUri(intent)?.let { incomingGpx = it }
    }

    private fun gpxUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}
