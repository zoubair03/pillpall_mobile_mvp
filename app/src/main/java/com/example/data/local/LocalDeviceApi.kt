package com.example.data.local

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LocalStatusResponse(
    @SerialName("deviceUid") val deviceUid: String = "",
    @SerialName("pairingCode") val pairingCode: String = "",
)

@Serializable
private data class WifiCredentialsRequest(
    @SerialName("ssid") val ssid: String,
    @SerialName("pass") val pass: String,
)

// Talks directly to the PillPal device's own local web server at its
// setup-hotspot IP — a separate trust boundary from the Supabase client:
// local-only, unauthenticated, plain HTTP. Every call here MUST be given
// the specific Network from a successful DeviceWifiConnector.connect(),
// or the request silently routes over the phone's normal connection
// instead of the device's hotspot (see DeviceWifiConnector's doc comment).
@Singleton
class LocalDeviceApi @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun clientFor(network: Network): OkHttpClient =
        baseClient.newBuilder().socketFactory(network.socketFactory).build()

    suspend fun getStatus(network: Network): LocalStatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$DEVICE_BASE_URL/api/status")
            .get()
            .build()

        clientFor(network).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: "{}"
            json.decodeFromString(LocalStatusResponse.serializer(), body)
        }
    }

    suspend fun sendWifiCredentials(network: Network, ssid: String, password: String) {
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                WifiCredentialsRequest.serializer(),
                WifiCredentialsRequest(ssid, password),
            )
            val request = Request.Builder()
                .url("$DEVICE_BASE_URL/api/wifi")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            clientFor(network).newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            }
        }
    }

    private companion object {
        // The ESP32's own SoftAP gateway IP — fixed by the AsyncWebServer
        // setup in frimware_v0, same on every unit.
        const val DEVICE_BASE_URL = "http://192.168.4.1"
    }
}
