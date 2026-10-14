package fr.igntrail.data.location

import fr.igntrail.core.model.LocationFix
import kotlinx.coroutines.flow.Flow

/**
 * Source de positions interchangeable (Fused ou GNSS natif).
 *
 * Les implémentations supposent que l'autorisation ACCESS_FINE_LOCATION ou
 * ACCESS_COARSE_LOCATION a déjà été accordée : sinon le flux se termine
 * par une [SecurityException].
 */
interface LocationSource {
    val type: String

    /** Positions en continu tant que le flux est collecté. */
    fun updates(intervalMs: Long): Flow<LocationFix>

    /** Dernière position mise en cache par le système, sans activer le GPS. */
    suspend fun lastKnown(): LocationFix?
}
