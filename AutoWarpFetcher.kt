package com.albionboost.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.awg.crypto.KeyPair
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AutoWarpFetcher {
    suspend fun fetchAndRegister(): Result<List<ServerNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val keyPair = KeyPair()
            val privKey = keyPair.privateKey.toBase64()
            val pubKey = keyPair.publicKey.toBase64()

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = isoFormat.format(Date())

            val regPayload = JSONObject().apply {
                put("key", pubKey)
                put("install_id", "")
                put("fcm_token", "")
                put("tos", nowIso)
                put("model", "Android")
                put("serial_number", "")
                put("locale", "en_US")
            }

            val regResponseText = postJson(
                url = "https://api.cloudflareclient.com/v0a2158/reg",
                body = regPayload.toString(),
                method = "POST",
                bearerToken = null
            )

            val json = JSONObject(regResponseText)
            val deviceId = json.getString("id")
            val token = json.getString("token")

            runCatching {
                postJson(
                    url = "https://api.cloudflareclient.com/v0a2158/reg/$deviceId",
                    body = JSONObject().put("warp_enabled", true).toString(),
                    method = "PATCH",
                    bearerToken = token
                )
            }

            val configObj = json.getJSONObject("config")
            val peerObj = configObj.getJSONArray("peers").getJSONObject(0)
            val serverPubKey = peerObj.getString("public_key")
            val endpointV4 = peerObj.getJSONObject("endpoint").optString("v4", "162.159.192.2:0")
            val baseIp = endpointV4.substringBefore(":").ifBlank { "162.159.192.2" }

            val v4Address = configObj
                .getJSONObject("interface")
                .getJSONObject("addresses")
                .optString("v4", "172.16.0.2")

            val endpoints = listOf(
                "$baseIp:2408" to "Авто WARP (2408)",
                "188.114.96.1:2408" to "Авто WARP (188.114)",
                "$baseIp:894" to "Авто WARP (894)",
                "$baseIp:4500" to "Авто WARP (4500)"
            )

            endpoints.mapIndexed { idx, (ep, label) ->
                val port = ep.substringAfter(":")
                ServerNode(
                    id = "warp_auto_${idx}_${port}",
                    countryCode = "CF",
                    country = "AmneziaWG",
                    city = label,
                    region = "eu",
                    albionTarget = "WARP + обход ТСПУ",
                    probeHost = ep.substringBefore(":"),
                    probePort = 443,
                    endpoint = ep,
                    serverPublicKey = serverPubKey,
                    clientPrivateKey = privKey,
                    presharedKey = "",
                    tunnelAddress = "$v4Address/32",
                    dnsServers = "1.1.1.1, 8.8.8.8",
                    mtu = 1280,
                    keepaliveSeconds = 25,
                    jc = 5,
                    jmin = 50,
                    jmax = 100,
                    s1 = 0,
                    s2 = 0,
                    h1 = 1,
                    h2 = 2,
                    h3 = 3,
                    h4 = 4
                )
            }
        }
    }

    private fun postJson(
        url: String,
        body: String,
        method: String,
        bearerToken: String?
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "okhttp/3.12.1")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $responseText")
        }
        return responseText
    }
}
