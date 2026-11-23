package com.albionboost.app

data class ParsedWireGuardConf(
    val endpoint: String,
    val serverPublicKey: String,
    val clientPrivateKey: String,
    val presharedKey: String,
    val tunnelAddress: String,
    val dnsServers: String,
    val mtu: Int,
    val keepaliveSeconds: Int,
    val jc: Int? = null,
    val jmin: Int? = null,
    val jmax: Int? = null,
    val s1: Int? = null,
    val s2: Int? = null,
    val h1: Int? = null,
    val h2: Int? = null,
    val h3: Int? = null,
    val h4: Int? = null
)

object WgConfigParser {
    fun parse(rawText: String): ParsedWireGuardConf? {
        var section = ""
        var endpoint = ""
        var serverPublicKey = ""
        var clientPrivateKey = ""
        var presharedKey = ""
        var tunnelAddress = "10.66.66.2/32"
        var dnsServers = "1.1.1.1, 8.8.8.8"
        var mtu = 1280
        var keepaliveSeconds = 25
        var jc: Int? = null
        var jmin: Int? = null
        var jmax: Int? = null
        var s1: Int? = null
        var s2: Int? = null
        var h1: Int? = null
        var h2: Int? = null
        var h3: Int? = null
        var h4: Int? = null

        rawText.lines().forEach { rawLine ->
            val line = rawLine.substringBefore("#").substringBefore(";").trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                return@forEach
            }

            val eqIdx = line.indexOf('=')
            if (eqIdx == -1) return@forEach

            val key = line.substring(0, eqIdx).trim().lowercase()
            val value = line.substring(eqIdx + 1).trim()
            if (value.isEmpty()) return@forEach

            when (section) {
                "interface" -> when (key) {
                    "privatekey" -> clientPrivateKey = value
                    "address" -> tunnelAddress = value
                    "dns" -> dnsServers = value
                    "mtu" -> value.toIntOrNull()?.let { if (it in 1200..1420) mtu = it }
                    "jc" -> jc = value.toIntOrNull()
                    "jmin" -> jmin = value.toIntOrNull()
                    "jmax" -> jmax = value.toIntOrNull()
                    "s1" -> s1 = value.toIntOrNull()
                    "s2" -> s2 = value.toIntOrNull()
                    "h1" -> h1 = value.toIntOrNull()
                    "h2" -> h2 = value.toIntOrNull()
                    "h3" -> h3 = value.toIntOrNull()
                    "h4" -> h4 = value.toIntOrNull()
                }
                "peer" -> when (key) {
                    "publickey" -> serverPublicKey = value
                    "presharedkey" -> presharedKey = value
                    "endpoint" -> endpoint = value
                    "persistentkeepalive" -> value.toIntOrNull()?.let { if (it in 0..120) keepaliveSeconds = it }
                }
            }
        }

        if (endpoint.isEmpty() || serverPublicKey.isEmpty() || clientPrivateKey.isEmpty()) {
            return null
        }

        return ParsedWireGuardConf(
            endpoint = endpoint,
            serverPublicKey = serverPublicKey,
            clientPrivateKey = clientPrivateKey,
            presharedKey = presharedKey,
            tunnelAddress = tunnelAddress,
            dnsServers = dnsServers,
            mtu = mtu,
            keepaliveSeconds = keepaliveSeconds,
            jc = jc,
            jmin = jmin,
            jmax = jmax,
            s1 = s1,
            s2 = s2,
            h1 = h1,
            h2 = h2,
            h3 = h3,
            h4 = h4
        )
    }
}
