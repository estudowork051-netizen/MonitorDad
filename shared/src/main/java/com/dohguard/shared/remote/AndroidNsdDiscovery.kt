package com.dohguard.shared.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Descoberta e anúncio automático em rede Wi-Fi local via Android NsdManager (mDNS / DNS-SD).
 * - KidsApp anuncia `_monitordad._tcp.` quando seu servidor local está ativo.
 * - ParentApp anuncia `_monitordad-pair._tcp.` durante o pareamento local.
 */
class AndroidNsdDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val isResolving = AtomicBoolean(false)

    fun registerService(
        serviceType: String,
        serviceName: String,
        port: Int,
        attributes: Map<String, String> = emptyMap(),
        onRegistered: (String) -> Unit = {},
        onFailed: (Int) -> Unit = {}
    ) {
        unregisterService()
        val manager = nsdManager ?: return
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = serviceType
            this.port = port
            attributes.forEach { (k, v) ->
                runCatching { setAttribute(k, v) }
            }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                onRegistered(serviceInfo.serviceName)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Falha ao registrar serviço NSD ($serviceType): $errorCode")
                onFailed(errorCode)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Falha ao remover serviço NSD: $errorCode")
            }
        }
        registrationListener = listener
        runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w(TAG, "Erro ao iniciar registerService: ${it.message}")
        }
    }

    fun unregisterService() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { manager.unregisterService(listener) }
    }

    fun startDiscovery(
        serviceType: String,
        onServiceResolved: (LocalEndpointInfo, Map<String, String>) -> Unit,
        onServiceLost: (String) -> Unit = {}
    ) {
        stopDiscovery()
        val manager = nsdManager ?: return
        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains(serviceType.trimEnd('.'))) return
                resolveQueue.add(serviceInfo)
                drainResolveQueue(onServiceResolved)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onServiceLost(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Falha ao iniciar descoberta NSD ($serviceType): $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Falha ao parar descoberta NSD: $errorCode")
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w(TAG, "Erro ao chamar discoverServices: ${it.message}")
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager
        val listener = discoveryListener
        discoveryListener = null
        resolveQueue.clear()
        isResolving.set(false)
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        releaseMulticastLock()
    }

    @Suppress("DEPRECATION")
    private fun drainResolveQueue(
        onServiceResolved: (LocalEndpointInfo, Map<String, String>) -> Unit
    ) {
        val manager = nsdManager ?: return
        if (!isResolving.compareAndSet(false, true)) return
        val next = resolveQueue.poll()
        if (next == null) {
            isResolving.set(false)
            return
        }
        runCatching {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    isResolving.set(false)
                    drainResolveQueue(onServiceResolved)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress.orEmpty()
                    val port = serviceInfo.port
                    val attrs = mutableMapOf<String, String>()
                    runCatching {
                        serviceInfo.attributes?.forEach { (k, v) ->
                            if (v != null) attrs[k] = String(v, Charsets.UTF_8)
                        }
                    }
                    if (host.isNotBlank() && port in 1..65535) {
                        val endpoint = LocalEndpointInfo(
                            deviceId = attrs["deviceId"] ?: serviceInfo.serviceName,
                            deviceName = attrs["deviceName"] ?: serviceInfo.serviceName,
                            host = host,
                            port = port,
                            discoveredAtMillis = System.currentTimeMillis(),
                            isReachable = true
                        )
                        onServiceResolved(endpoint, attrs)
                    }
                    isResolving.set(false)
                    drainResolveQueue(onServiceResolved)
                }
            })
        }.onFailure {
            isResolving.set(false)
        }
    }

    private fun acquireMulticastLock() {
        runCatching {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("MonitorDadNsdLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            multicastLock?.takeIf { !it.isHeld }?.acquire()
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
        }
    }

    companion object {
        private const val TAG = "MonitorDadNsd"
        const val SERVICE_TYPE_KIDS = "_monitordad._tcp."
        const val SERVICE_TYPE_PAIRING = "_monitordad-pair._tcp."

        fun getLocalWifiIpv4Address(): String? {
            return runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                var fallbackPrivateIp: String? = null
                while (interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (!nif.isUp || nif.isLoopback) continue
                    val name = nif.name.lowercase()
                    val addrs = nif.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("eth")) {
                                return ip
                            }
                            if (fallbackPrivateIp == null) {
                                fallbackPrivateIp = ip
                            }
                        }
                    }
                }
                fallbackPrivateIp
            }.getOrNull()
        }
    }
}
