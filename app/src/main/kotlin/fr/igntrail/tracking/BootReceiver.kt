package fr.igntrail.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.igntrail.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Après un redémarrage du téléphone, si une randonnée était en cours, on propose
 * de la reprendre. Android interdit de relancer la localisation au premier plan
 * depuis le démarrage sans autorisation d'arrière-plan : la reprise est manuelle.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.appContainer.trackRepository.activeSession() != null) {
                    TrackingNotifications.showInterrupted(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
