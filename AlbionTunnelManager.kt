package com.albionboost.app

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.NoopTunnelActionHandler
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class TunnelUiState(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val message: String = "Выберите сервер и нажмите «Подключиться»"
)

object AlbionTunnelManager {
    private const val TUNNEL_NAME = "albion_boost"

    private lateinit var appContext: Context
    private var backend: GoBackend? = null

    private val _state = MutableStateFlow(TunnelUiState())
    val state: StateFlow<TunnelUiState> = _state.asStateFlow()

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.UP) {
                val active = ServerRepository.getSelectedServer()
                _state.value = TunnelUiState(
                    status = TunnelStatus.CONNECTED,
                    message = "Подключено · ${active.city}"
                )
            } else if (newState == Tunnel.State.DOWN &&
                _state.value.status != TunnelStatus.ERROR
            ) {
                _state.value = TunnelUiState(
                    status = TunnelStatus.DISCONNECTED,
                    message = "Отключено"
                )
            }
        }
    }

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        if (backend == null) {
            backend = GoBackend(appContext, NoopTunnelActionHandler())
        }
    }

    fun refreshState() {
        val currentBackend = backend ?: return
        val isUp = runCatching {
            currentBackend.runningTunnelNames.contains(TUNNEL_NAME)
        }.getOrDefault(false)

        if (isUp) {
            val active = ServerRepository.getSelectedServer()
            _state.value = TunnelUiState(
                status = TunnelStatus.CONNECTED,
                message = "Подключено · ${active.city}"
            )
        }
    }

    fun setPermissionDenied() {
        _state.value = TunnelUiState(
            status = TunnelStatus.ERROR,
            message = "Не выдано разрешение на VPN-подключение"
        )
    }

    suspend fun connect(server: ServerNode) {
        if (!server.isConfigured()) {
            _state.value = TunnelUiState(
                status = TunnelStatus.ERROR,
                message = "Для узла «${server.city}» вставьте конфиг (.conf)"
            )
            return
        }

        _state.value = TunnelUiState(
            status = TunnelStatus.CONNECTING,
            message = "Подключаемся к ${server.city}..."
        )

        withContext(Dispatchers.IO) {
            try {
                val wgConfig = buildAmneziaConfig(server)
                val activeBackend = backend ?: GoBackend(appContext, NoopTunnelActionHandler()).also {
                    backend = it
                }
                activeBackend.setState(tunnel, Tunnel.State.UP, wgConfig)

                _state.value = TunnelUiState(
                    status = TunnelStatus.CONNECTED,
                    message = buildConnectedMessage(server)
                )
            } catch (e: Exception) {
                _state.value = TunnelUiState(
                    status = TunnelStatus.ERROR,
                    message = "Ошибка: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    suspend fun disconnect() {
        _state.value = TunnelUiState(
            status = TunnelStatus.DISCONNECTING,
            message = "Отключаемся..."
        )

        withContext(Dispatchers.IO) {
            try {
                backend?.setState(tunnel, Tunnel.State.DOWN, null)
                _state.value = TunnelUiState(
                    status = TunnelStatus.DISCONNECTED,
                    message = "Отключено"
                )
            } catch (e: Exception) {
                _state.value = TunnelUiState(
                    status = TunnelStatus.ERROR,
                    message = "Ошибка остановки: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    private fun buildAmneziaConfig(server: ServerNode): Config {
        val sb = StringBuilder()
        sb.appendLine("[Interface]")
        sb.appendLine("PrivateKey = ${server.clientPrivateKey.trim()}")
        sb.appendLine("Address = ${server.tunnelAddress.trim()}")
        sb.appendLine("DNS = ${server.dnsServers.trim()}")
        sb.appendLine("MTU = ${server.mtu}")

        if (isPackageInstalled(DefaultServers.TARGET_PACKAGE)) {
            sb.appendLine("IncludedApplications = ${DefaultServers.TARGET_PACKAGE}")
        }

        server.jc?.let { sb.appendLine("Jc = $it") }
        server.jmin?.let { sb.appendLine("Jmin = $it") }
        server.jmax?.let { sb.appendLine("Jmax = $it") }
        server.s1?.let { sb.appendLine("S1 = $it") }
        server.s2?.let { sb.appendLine("S2 = $it") }
        server.h1?.let { sb.appendLine("H1 = $it") }
        server.h2?.let { sb.appendLine("H2 = $it") }
        server.h3?.let { sb.appendLine("H3 = $it") }
        server.h4?.let { sb.appendLine("H4 = $it") }

        sb.appendLine()
        sb.appendLine("[Peer]")
        sb.appendLine("PublicKey = ${server.serverPublicKey.trim()}")
        if (server.presharedKey.isNotBlank()) {
            sb.appendLine("PresharedKey = ${server.presharedKey.trim()}")
        }
        sb.appendLine("Endpoint = ${server.endpoint.trim()}")
        sb.appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
        if (server.keepaliveSeconds > 0) {
            sb.appendLine("PersistentKeepalive = ${server.keepaliveSeconds}")
        }

        return Config.parse(ByteArrayInputStream(sb.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun buildConnectedMessage(server: ServerNode): String {
        return if (isPackageInstalled(DefaultServers.TARGET_PACKAGE)) {
            "Подключено · ${server.city} (Albion Online)"
        } else {
            "Подключено · ${server.city} (весь трафик)"
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
