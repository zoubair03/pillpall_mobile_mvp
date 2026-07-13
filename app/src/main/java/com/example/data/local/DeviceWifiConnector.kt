package com.example.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface DeviceConnectResult {
    data class Success(val network: Network) : DeviceConnectResult
    data class Error(val message: String) : DeviceConnectResult
}

// Connects the phone directly to the PillPal device's own setup hotspot
// via WifiNetworkSpecifier (Android 10+) — the app-initiated local-network
// API, distinct from the classic scan/list WiFi APIs and requiring no
// location permission, since it targets one exact known SSID rather than
// discovering nearby networks. The device's AP SSID is a fixed constant
// (frimware_v0/include/config.h WIFI_AP_SSID), identical on every unit —
// there's nothing to scan or pick from a list.
//
// The returned Network is deliberately NOT the phone's default route:
// WifiNetworkSpecifier connections targeting one exact SSID are local-only
// by nature (the AP has no internet), so any HTTP request to the device
// must be explicitly bound to this Network (see LocalDeviceApi) or it
// silently goes out over the phone's normal WiFi/cellular connection
// instead.
@Singleton
class DeviceWifiConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val DEVICE_AP_SSID = "PillPal-Setup"
        private const val CONNECT_TIMEOUT_MS = 20_000L
    }

    suspend fun connect(): DeviceConnectResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return DeviceConnectResult.Error(
                "Cette fonctionnalité nécessite Android 10 ou plus récent. " +
                    "Connectez-vous manuellement au réseau \"$DEVICE_AP_SSID\" dans les paramètres " +
                    "WiFi de votre téléphone, puis revenez à l'application."
            )
        }

        release()

        // The 3-arg requestNetwork(request, callback, timeoutMs) overload
        // needs CHANGE_NETWORK_STATE, which normal (non-system) apps can't
        // hold — so the plain 2-arg overload is used instead, with our own
        // withTimeoutOrNull() standing in for the timeout. That overload's
        // onUnavailable() isn't guaranteed to ever fire on its own, so
        // without this the coroutine could hang forever if the device is
        // simply unreachable.
        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(DEVICE_AP_SSID)
                    .build()

                // Deliberately NOT calling removeCapability(NET_CAPABILITY_INTERNET)
                // here: doing so throws SecurityException requiring
                // CHANGE_NETWORK_STATE/WRITE_SETTINGS (privileged-app-only
                // permissions) — removing a default-required capability is
                // treated as a more invasive network-request modification
                // than a normal app is allowed to make. Leaving it out
                // still works: with a NetworkSpecifier targeting one exact
                // SSID, the framework connects and fires onAvailable() for
                // that specific network regardless of internet validation.
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(DeviceConnectResult.Success(network))
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) {
                            cont.resume(
                                DeviceConnectResult.Error(
                                    "Impossible de se connecter à l'appareil PillPal. " +
                                        "Assurez-vous qu'il est allumé et à proximité."
                                )
                            )
                        }
                    }
                }

                activeCallback = callback
                connectivityManager.requestNetwork(request, callback)
                cont.invokeOnCancellation { release() }
            }
        }

        if (result == null) {
            release()
            return DeviceConnectResult.Error(
                "La connexion a expiré. Assurez-vous que l'appareil est allumé et à proximité, " +
                    "puis réessayez."
            )
        }
        return result
    }

    // Unbinds from the device's hotspot — must be called once the app is
    // done talking to it (credentials sent, or the flow was abandoned),
    // otherwise the OS keeps holding the network request open.
    fun release() {
        activeCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        activeCallback = null
    }
}
