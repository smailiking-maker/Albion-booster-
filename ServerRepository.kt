package com.albionboost.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

object ServerRepository {
    private const val PREFS_NAME = "albion_boost_servers"
    private const val KEY_SERVERS_JSON = "servers_json_v1"
    private const val KEY_SELECTED_ID = "selected_server_id_v1"

    private lateinit var appContext: Context

    private val _servers = MutableStateFlow<List<ServerNode>>(DefaultServers.list)
    val servers: StateFlow<List<ServerNode>> = _servers.asStateFlow()

    private val _selectedId = MutableStateFlow(DefaultServers.INITIAL_SELECTED_ID)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            loadFromPrefs()
        }
    }

    fun getSelectedServer(): ServerNode {
        val current = _servers.value
        return current.find { it.id == _selectedId.value } ?: current.first()
    }

    fun selectServer(serverId: String) {
        _selectedId.value = serverId
        saveSelectedId(serverId)
    }

    fun applyConfigToServer(serverId: String, conf: ParsedWireGuardConf) {
        val updated = _servers.value.map { node ->
            if (node.id == serverId) {
                node.copy(
                    endpoint = conf.endpoint,
                    serverPublicKey = conf.serverPublicKey,
                    clientPrivateKey = conf.clientPrivateKey,
                    presharedKey = conf.presharedKey,
                    tunnelAddress = conf.tunnelAddress,
                    dnsServers = conf.dnsServers,
                    mtu = conf.mtu,
                    keepaliveSeconds = conf.keepaliveSeconds,
                    jc = conf.jc,
                    jmin = conf.jmin,
                    jmax = conf.jmax,
                    s1 = conf.s1,
                    s2 = conf.s2,
                    h1 = conf.h1,
                    h2 = conf.h2,
                    h3 = conf.h3,
                    h4 = conf.h4
                )
            } else {
                node
            }
        }
        _servers.value = updated
        saveServersToPrefs(updated)
        selectServer(serverId)
    }

    fun addCustomServer(city: String, region: String, conf: ParsedWireGuardConf) {
        val newId = "custom_${System.currentTimeMillis()}"
        val node = ServerNode(
            id = newId,
            countryCode = "WG",
            country = "Мой сервер",
            city = city.ifBlank { "Свой узел" },
            region = region,
            albionTarget = "Пользовательский маршрут",
            probeHost = conf.endpoint.substringBefore(":"),
            probePort = 443,
            endpoint = conf.endpoint,
            serverPublicKey = conf.serverPublicKey,
            clientPrivateKey = conf.clientPrivateKey,
            presharedKey = conf.presharedKey,
            tunnelAddress = conf.tunnelAddress,
            dnsServers = conf.dnsServers,
            mtu = conf.mtu,
            keepaliveSeconds = conf.keepaliveSeconds,
            jc = conf.jc,
            jmin = conf.jmin,
            jmax = conf.jmax,
            s1 = conf.s1,
            s2 = conf.s2,
            h1 = conf.h1,
            h2 = conf.h2,
            h3 = conf.h3,
            h4 = conf.h4
        )
        val updated = listOf(node) + _servers.value
        _servers.value = updated
        saveServersToPrefs(updated)
        selectServer(newId)
    }

    fun addAutoWarpNodes(nodes: List<ServerNode>) {
        if (nodes.isEmpty()) return
        val filteredExisting = _servers.value.filterNot { it.id.startsWith("warp_auto_") }
        val updated = nodes + filteredExisting
        _servers.value = updated
        saveServersToPrefs(updated)
        selectServer(nodes.first().id)
    }

    suspend fun pingAllServers() = withContext(Dispatchers.IO) {
        val snapshot = _servers.value
        val measured = coroutineScope {
            snapshot.map { node ->
                async {
                    val host = node.probeHost
                    val port = node.probePort
                    val ms = measureTcpMs(host, port)
                    node.copy(latencyMs = ms)
                }
            }.awaitAll()
        }
        _servers.value = measured
    }

    private fun measureTcpMs(host: String, port: Int): Int? {
        if (host.isBlank()) return null
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1600)
            }
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
            elapsedMs.coerceAtLeast(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveSelectedId(id: String) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_ID, id)
            .apply()
    }

    private fun saveServersToPrefs(list: List<ServerNode>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("countryCode", n.countryCode)
                    put("country", n.country)
                    put("city", n.city)
                    put("region", n.region)
                    put("albionTarget", n.albionTarget)
                    put("probeHost", n.probeHost)
                    put("probePort", n.probePort)
                    put("endpoint", n.endpoint)
                    put("serverPublicKey", n.serverPublicKey)
                    put("clientPrivateKey", n.clientPrivateKey)
                    put("presharedKey", n.presharedKey)
                    put("tunnelAddress", n.tunnelAddress)
                    put("dnsServers", n.dnsServers)
                    put("mtu", n.mtu)
                    put("keepaliveSeconds", n.keepaliveSeconds)
                    n.jc?.let { put("jc", it) }
                    n.jmin?.let { put("jmin", it) }
                    n.jmax?.let { put("jmax", it) }
                    n.s1?.let { put("s1", it) }
                    n.s2?.let { put("s2", it) }
                    n.h1?.let { put("h1", it) }
                    n.h2?.let { put("h2", it) }
                    n.h3?.let { put("h3", it) }
                    n.h4?.let { put("h4", it) }
                }
            )
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVERS_JSON, arr.toString())
            .apply()
    }

    private fun loadFromPrefs() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSelected = prefs.getString(KEY_SELECTED_ID, null)
        if (!savedSelected.isNullOrBlank()) {
            _selectedId.value = savedSelected
        }
        val rawJson = prefs.getString(KEY_SERVERS_JSON, null) ?: return
        runCatching {
            val arr = JSONArray(rawJson)
            val loaded = mutableListOf<ServerNode>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                loaded.add(
                    ServerNode(
                        id = o.getString("id"),
                        countryCode = o.optString("countryCode", "EU"),
                        country = o.optString("country", "Европа"),
                        city = o.optString("city", "Сервер"),
                        region = o.optString("region", "eu"),
                        albionTarget = o.optString("albionTarget", "Albion Europe"),
                        probeHost = o.optString("probeHost", "live.albiononline.com"),
                        probePort = o.optInt("probePort", 443),
                        endpoint = o.optString("endpoint", ""),
                        serverPublicKey = o.optString("serverPublicKey", ""),
                        clientPrivateKey = o.optString("clientPrivateKey", ""),
                        presharedKey = o.optString("presharedKey", ""),
                        tunnelAddress = o.optString("tunnelAddress", "10.66.66.2/32"),
                        dnsServers = o.optString("dnsServers", "1.1.1.1, 8.8.8.8"),
                        mtu = o.optInt("mtu", 1280),
                        keepaliveSeconds = o.optInt("keepaliveSeconds", 25),
                        jc = if (o.has("jc")) o.optInt("jc") else null,
                        jmin = if (o.has("jmin")) o.optInt("jmin") else null,
                        jmax = if (o.has("jmax")) o.optInt("jmax") else null,
                        s1 = if (o.has("s1")) o.optInt("s1") else null,
                        s2 = if (o.has("s2")) o.optInt("s2") else null,
                        h1 = if (o.has("h1")) o.optInt("h1") else null,
                        h2 = if (o.has("h2")) o.optInt("h2") else null,
                        h3 = if (o.has("h3")) o.optInt("h3") else null,
                        h4 = if (o.has("h4")) o.optInt("h4") else null
                    )
                )
            }
            if (loaded.isNotEmpty()) {
                _servers.value = loaded
            }
        }
    }
}
