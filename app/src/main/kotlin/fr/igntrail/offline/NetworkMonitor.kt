package fr.igntrail.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkState(val online: Boolean, val metered: Boolean)

/** État de la connexion : hors ligne, Wi-Fi, données mobiles (facturées). */
class NetworkMonitor(context: Context) {
    private val manager: ConnectivityManager? = context.getSystemService()
    private val _state = MutableStateFlow(current())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    init {
        manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _state.value = caps.toState()
            }

            override fun onLost(network: Network) {
                _state.value = NetworkState(online = false, metered = false)
            }
        })
    }

    private fun current(): NetworkState {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkState(false, false)
        return caps.toState()
    }

    private fun NetworkCapabilities.toState() = NetworkState(
        online = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
