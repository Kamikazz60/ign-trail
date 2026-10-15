package fr.igntrail.map

/**
 * Choisit les positions affichées par la carte. Pendant une sortie, la carte suit les
 * positions satellites de l'enregistrement, sans les mélanger aux positions Wi-Fi de son
 * propre abonnement (le point sauterait de l'une à l'autre). Sans position du service
 * depuis [servicePriorityMs], la carte reprend son propre abonnement.
 */
class FixArbiter(private val servicePriorityMs: Long = 5_000L) {
    private var lastServiceFix: Long? = null
    private var lastShown: Long? = null

    /** Vrai si la position (heure [timeMillis]) doit être affichée. */
    fun accept(timeMillis: Long, fromService: Boolean): Boolean {
        if (fromService) {
            lastServiceFix = timeMillis
        } else if (lastServiceFix?.let { timeMillis - it < servicePriorityMs } == true) {
            return false
        }
        if (lastShown?.let { timeMillis <= it } == true) return false
        lastShown = timeMillis
        return true
    }
}
