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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val scheme: String,
        val path: String = "",
        val landscape: Boolean? = null,
        val parameters: Map<String, String>? = null
    ) {
        val baseUrl: String
            get() = Uri.Builder()
                .scheme(scheme)
                .encodedAuthority("$host:$port")
                .build()
                .toString()
        val url: String
            get() = Uri.Builder()
                .scheme(scheme)
                .encodedAuthority("$host:$port")
                .path(path)
                .build()
                .toString()
    }

    private val serviceMap = mutableMapOf<String, BridgeService>()
    private val activeCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    var isSearching by mutableStateOf(false)
        private set

    var discoveredServices by mutableStateOf<List<BridgeService>>(emptyList())
        private set

    fun startDiscovery() {
        if (isSearching) return
        isSearching = true
        serviceMap.clear()
        discoveredServices = emptyList()
        startNsdDiscovery()
        viewModelScope.launch {
            delay(10.seconds)
            stopDiscovery()
        }
    }

    fun clearServices() {
        discoveredServices = emptyList()
    }

    fun stopDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activeCallbacks.forEach {
                try {
                    nsdManager.unregisterServiceInfoCallback(it)
                } catch (_: Exception) {
                }
            }
            activeCallbacks.clear()
        }
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service discovery", e)
            }
            discoveryListener = null
        }
        isSearching = false
    }

    private fun updateServices() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            discoveredServices = serviceMap.values.toList()
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    Log.d(
                        TAG,
                        "Service Updated: ${info.serviceName} ${info.hostAddresses.joinToString(",")}"
                    )
                    val attrs = info.attributes

                    fun attr(key: String): String? = attrs
                        ?.get(key)
                        ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }

                    val host = attr("host")
                        ?: info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                        ?: info.hostAddresses.firstOrNull { it is Inet6Address }?.hostAddress
                        ?: "unknown"
                    val port = info.port
                    val serviceName = info.serviceName

                    val scheme = if (attr("https") == "true") "https" else "http"
                    val path = attr("path") ?: ""

                    serviceMap[serviceName] = BridgeService(
                        name = serviceName,
                        host = host,
                        port = port,
                        scheme = scheme,
                        path = path,
                        parameters = attr("params")?.let { json ->
                            runCatching {
                                val obj = org.json.JSONObject(json)
                                obj.keys().asSequence().associateWith { obj.getString(it) }
                            }.getOrNull()
                        },
                        landscape = attr("landscape")?.let { it == "true" }
                    )
                    updateServices()
                }

                override fun onServiceLost() {
                    Log.w(TAG, "Service lost during resolution: ${serviceInfo.serviceName}")
                    serviceMap.remove(serviceInfo.serviceName)
                    updateServices()
                }

                override fun onServiceInfoCallbackUnregistered() {}
            }
            try {
                nsdManager.registerServiceInfoCallback(serviceInfo, { it.run() }, callback)
                activeCallbacks.add(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve service: ${serviceInfo.serviceName}", e)
            }
        } else {
            @Suppress("DEPRECATION")
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "Service resolved: ${serviceInfo?.serviceName}")
                    serviceInfo?.let { info ->
                        val attrs = info.attributes

                        fun attr(key: String): String? = attrs
                            ?.get(key)
                            ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
                            ?.takeIf { it.isNotBlank() }

                        val host = attr("host") ?: info.host?.hostAddress ?: "unknown"
                        val port = info.port
                        val serviceName = info.serviceName

                        val scheme = if (attr("https") == "true") "https" else "http"
                        val path = attr("path") ?: ""

                        serviceMap[serviceName] = BridgeService(
                            name = serviceName,
                            host = host,
                            port = port,
                            scheme = scheme,
                            path = path,
                            parameters = attr("params")?.let { json ->
                                runCatching {
                                    val obj = org.json.JSONObject(json)
                                    obj.keys().asSequence().associateWith { obj.getString(it) }
                                }.getOrNull()
                            },
                            landscape = attr("landscape")?.let { it == "true" }
                        )
                        updateServices()
                    }
                }
            }
            try {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, resolveListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve service: ${serviceInfo.serviceName}", e)
            }
        }
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                viewModelScope.launch(Dispatchers.Main.immediate) { isSearching = false }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
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
                    updateServices()
                }
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            isSearching = false
        }
    }
}

/**
 * 更新服务信息
 */
data class UpdaterService(
    val name: String,
    val host: String,
    val port: Int,
    val path: String
) {
    val baseUrl: String
        get() = Uri.Builder()
            .scheme("http")
            .encodedAuthority("$host:$port")
            .build()
            .toString()
    val url: String
        get() = Uri.Builder()
            .scheme("http")
            .encodedAuthority("$host:$port")
            .path(path)
            .build()
            .toString()
}

/**
 * 通过 NSD 发现局域网更新服务。
 */
class UpdaterDiscoveryViewModel : ViewModel() {

    private val nsdManager = App.context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        private const val TAG = "UpdaterDiscoveryViewModel"
        private const val SERVICE_TYPE = "_bridge-updater._tcp"
    }

    private val serviceMap = mutableMapOf<String, UpdaterService>()
    private val activeCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    var isSearching by mutableStateOf(false)
        private set

    // 是否已完成过一次搜索
    var hasSearched by mutableStateOf(false)
        private set

    var discoveredServices by mutableStateOf<List<UpdaterService>>(emptyList())
        private set

    fun startDiscovery() {
        if (isSearching) return
        isSearching = true
        serviceMap.clear()
        discoveredServices = emptyList()
        startNsdDiscovery()
        viewModelScope.launch {
            delay(10.seconds)
            stopDiscovery()
        }
    }

    private fun stopDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activeCallbacks.forEach {
                try {
                    nsdManager.unregisterServiceInfoCallback(it)
                } catch (_: Exception) {
                }
            }
            activeCallbacks.clear()
        }
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service discovery", e)
            }
            discoveryListener = null
        }
        isSearching = false
        hasSearched = true
    }

    private fun updateServices() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            discoveredServices = serviceMap.values.toList()
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    Log.d(TAG, "Service Updated: ${info.serviceName}")

                    val serviceName = info.serviceName
                    val attrs = info.attributes

                    fun attr(key: String): String? = attrs
                        ?.get(key)
                        ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }

                    // TXT中可覆盖host（用于nginx等场景）
                    val host = attr("host")
                        ?: info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                        ?: info.hostAddresses.firstOrNull { it is Inet6Address }?.hostAddress
                        ?: "unknown"
                    val port = info.port
                    val path = attr("path") ?: ""

                    serviceMap[serviceName] = UpdaterService(
                        name = serviceName,
                        host = host,
                        port = port,
                        path = path
                    )
                    updateServices()
                    stopDiscovery()
                }

                override fun onServiceLost() {
                    Log.w(TAG, "Service lost during resolution: ${serviceInfo.serviceName}")
                    serviceMap.remove(serviceInfo.serviceName)
                    updateServices()
                }

                override fun onServiceInfoCallbackUnregistered() {}
            }
            try {
                nsdManager.registerServiceInfoCallback(serviceInfo, { it.run() }, callback)
                activeCallbacks.add(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve service: ${serviceInfo.serviceName}", e)
            }
        } else {
            @Suppress("DEPRECATION")
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "Service resolved: ${serviceInfo?.serviceName}")
                    serviceInfo?.let { info ->
                        val attrs = info.attributes

                        fun attr(key: String): String? = attrs
                            ?.get(key)
                            ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
                            ?.takeIf { it.isNotBlank() }

                        // TXT中可覆盖host（用于nginx等场景）
                        val host = attr("host") ?: info.host?.hostAddress ?: "unknown"
                        val port = info.port
                        val path = attr("path") ?: ""
                        val serviceName = info.serviceName

                        serviceMap[serviceName] = UpdaterService(
                            name = serviceName,
                            host = host,
                            port = port,
                            path = path
                        )
                        updateServices()
                        stopDiscovery()
                    }
                }
            }

            try {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, resolveListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve service: ${serviceInfo.serviceName}", e)
            }
        }
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                viewModelScope.launch(Dispatchers.Main.immediate) { isSearching = false }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
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
                    updateServices()
                }
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            isSearching = false
        }
    }
}