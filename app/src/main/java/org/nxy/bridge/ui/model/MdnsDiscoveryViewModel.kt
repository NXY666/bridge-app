package org.nxy.bridge.ui.model

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.nxy.bridge.App
import java.net.Inet4Address
import java.net.Inet6Address
import kotlin.time.Duration.Companion.seconds

/**
 * 通过 NSD 发现局域网 Bridge 服务，并解析为可用 URL。
 */
class MdnsDiscoveryViewModel : ViewModel() {

    private val nsdManager = App.context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        private const val TAG = "ServiceDiscoveryViewModel"
        private const val SERVICE_TYPE = "_bridge._tcp"
    }

    data class BridgeService(
        val name: String,
        val host: String,
        val port: Int,
        val url: String,
        val apis: ApiConfig? = null,
        val landscape: Boolean? = null,
        val parameters: Map<String, String>? = null
    )

    var isSearching by mutableStateOf(false)
        private set

    var discoveredServices by mutableStateOf<List<BridgeService>>(emptyList())
        private set

    fun startDiscovery() {
        if (isSearching) return

        viewModelScope.launch {
            isSearching = true
            discoveredServices = emptyList()

            try {
                discoverServices().collect { services ->
                    discoveredServices = services
                }
            } catch (_: Exception) {
            } finally {
                isSearching = false
            }
        }
    }

    fun clearServices() {
        discoveredServices = emptyList()
    }

    @OptIn(FlowPreview::class)
    private fun discoverServices(): Flow<List<BridgeService>> = callbackFlow {
        val serviceMap = mutableMapOf<String, BridgeService>()

        fun resolveService(serviceInfo: NsdServiceInfo) {
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "Service resolved: ${serviceInfo?.serviceName}")
                    serviceInfo?.let { info ->
                        @Suppress("DEPRECATION")
                        val host = if (Build.VERSION.SDK_INT >= 34) {
                            val addresses = info.hostAddresses // List<InetAddress>
                            addresses.firstOrNull { it is Inet4Address }?.hostAddress
                                ?: addresses.firstOrNull { it is Inet6Address }?.hostAddress
                                ?: info.host?.hostAddress
                        } else {
                            info.host?.hostAddress
                        }

                        if (host != null) {
                            val port = info.port
                            val serviceName = info.serviceName
                            val url = Uri.Builder()
                                .scheme("http")
                                .encodedAuthority("$host:$port")
                                .build()
                                .toString()

                            val attrs = info.attributes

                            fun attr(key: String): String? = attrs
                                ?.get(key)
                                ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
                                ?.takeIf { it.isNotBlank() }

                            val bridgeService = BridgeService(
                                name = serviceName,
                                host = host,
                                port = port,
                                url = url,
                                parameters = attr("params")?.let { json ->
                                    runCatching {
                                        val obj = JSONObject(json)
                                        obj.keys().asSequence().associateWith { obj.getString(it) }
                                    }.getOrNull()
                                },
                                landscape = attr("landscape")?.let { it == "true" },
                                apis = attr("apis")?.let { json ->
                                    runCatching {
                                        val obj = JSONObject(json)
                                        ApiConfig(version = obj.optString("version").ifBlank { null })
                                    }.getOrNull()
                                }
                            )

                            serviceMap[serviceName] = bridgeService
                            trySend(serviceMap.values.toList())
                        }
                    }
                }
            }

            try {
                nsdManager.resolveService(serviceInfo, resolveListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve service: ${serviceInfo.serviceName}", e)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
                close()
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service found: ${serviceInfo?.serviceName}")
                serviceInfo?.let { resolveService(it) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service lost: ${serviceInfo?.serviceName}")
                serviceInfo?.let {
                    serviceMap.remove(it.serviceName)
                    trySend(serviceMap.values.toList())
                }
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            close()
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service discovery", e)
            }
        }
    }.timeout(10.seconds)
}