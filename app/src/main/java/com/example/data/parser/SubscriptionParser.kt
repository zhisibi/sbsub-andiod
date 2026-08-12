package com.example.data.parser

import android.util.Base64
import com.example.data.model.ProxyNode
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SubscriptionParser {

    fun parseContent(rawContent: String, subscriptionId: Long = 0): List<ProxyNode> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Try Base64 decoding first
        val decodedBase64 = tryDecodeBase64(trimmed)
        val contentToParse = if (decodedBase64.lines().size > 1 || decodedBase64.contains("://")) {
            decodedBase64
        } else {
            trimmed
        }

        // 2. Try JSON (Sing-box config or Clash JSON)
        if (contentToParse.startsWith("{")) {
            val jsonNodes = parseSingBoxJson(contentToParse, subscriptionId)
            if (jsonNodes.isNotEmpty()) return jsonNodes
        }

        // 3. Try Line-by-line URI format
        val nodes = mutableListOf<ProxyNode>()
        contentToParse.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
                val node = parseUri(line, subscriptionId)
                if (node != null) {
                    nodes.add(node)
                }
            }
        }

        return nodes
    }

    private fun parseUri(uri: String, subscriptionId: Long): ProxyNode? {
        return try {
            when {
                uri.startsWith("vless://", ignoreCase = true) -> parseVless(uri, subscriptionId)
                uri.startsWith("vmess://", ignoreCase = true) -> parseVmess(uri, subscriptionId)
                uri.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(uri, subscriptionId)
                uri.startsWith("trojan://", ignoreCase = true) -> parseTrojan(uri, subscriptionId)
                uri.startsWith("hy2://", ignoreCase = true) || uri.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(uri, subscriptionId)
                uri.startsWith("socks5://", ignoreCase = true) || uri.startsWith("socks://", ignoreCase = true) -> parseSocks(uri, subscriptionId)
                uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> parseHttp(uri, subscriptionId)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVless(uri: String, subscriptionId: Long): ProxyNode? {
        // vless://uuid@host:port?param1=val1&param2=val2#Remark
        val main = uri.removePrefix("vless://").removePrefix("VLESS://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "VLESS Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val uuid = atSplit[0]
        val hostPort = atSplit[1].split(":", limit = 2)
        if (hostPort.size < 2) return null

        val server = hostPort[0]
        val port = hostPort[1].toIntOrNull() ?: 443

        val network = queryParams["type"] ?: queryParams["network"] ?: "tcp"
        val security = queryParams["security"] ?: ""
        val tls = security.equals("tls", ignoreCase = true) || security.equals("reality", ignoreCase = true)
        val sni = queryParams["sni"] ?: queryParams["peer"] ?: ""
        val host = queryParams["host"] ?: ""
        val path = queryParams["path"] ?: ""
        val flow = queryParams["flow"] ?: ""

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "vless",
            server = server,
            port = port,
            uuidOrPassword = uuid,
            network = network,
            path = path,
            host = host,
            tls = tls,
            sni = sni,
            flow = flow,
            rawUri = uri
        )
    }

    private fun parseVmess(uri: String, subscriptionId: Long): ProxyNode? {
        val base64Content = uri.removePrefix("vmess://").removePrefix("VMESS://")
        val decoded = tryDecodeBase64(base64Content)
        val json = JSONObject(decoded)

        val name = json.optString("ps", "VMess Node")
        val server = json.optString("add", "")
        val port = json.optInt("port", 443)
        val uuid = json.optString("id", "")
        val cipher = json.optString("scy", "auto")
        val alterId = json.optInt("aid", 0)
        val net = json.optString("net", "tcp")
        val type = json.optString("type", "none")
        val host = json.optString("host", "")
        val path = json.optString("path", "")
        val tlsStr = json.optString("tls", "")
        val sni = json.optString("sni", "")

        val tls = tlsStr.equals("tls", ignoreCase = true)

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "vmess",
            server = server,
            port = port,
            uuidOrPassword = uuid,
            cipher = cipher,
            alterId = alterId,
            network = net,
            path = path,
            host = host,
            tls = tls,
            sni = if (sni.isNotEmpty()) sni else host,
            rawUri = uri
        )
    }

    private fun parseShadowsocks(uri: String, subscriptionId: Long): ProxyNode? {
        // ss://base64(method:password)@server:port#Name OR ss://base64(method:password@server:port)#Name
        val main = uri.removePrefix("ss://").removePrefix("SS://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "Shadowsocks"
        val body = nameSplit[0]

        return if (body.contains("@")) {
            val parts = body.split("@", limit = 2)
            val userInfoDecoded = tryDecodeBase64(parts[0])
            val creds = if (userInfoDecoded.contains(":")) userInfoDecoded else parts[0]
            val methodPass = creds.split(":", limit = 2)
            val hostPort = parts[1].split(":", limit = 2)

            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "ss",
                server = hostPort[0],
                port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8388,
                cipher = methodPass.getOrElse(0) { "aes-256-gcm" },
                uuidOrPassword = methodPass.getOrElse(1) { "" },
                rawUri = uri
            )
        } else {
            val decoded = tryDecodeBase64(body)
            val atSplit = decoded.split("@", limit = 2)
            if (atSplit.size < 2) return null
            val methodPass = atSplit[0].split(":", limit = 2)
            val hostPort = atSplit[1].split(":", limit = 2)

            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "ss",
                server = hostPort[0],
                port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8388,
                cipher = methodPass.getOrElse(0) { "aes-256-gcm" },
                uuidOrPassword = methodPass.getOrElse(1) { "" },
                rawUri = uri
            )
        }
    }

    private fun parseTrojan(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("trojan://").removePrefix("TROJAN://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "Trojan Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val password = atSplit[0]
        val hostPort = atSplit[1].split(":", limit = 2)

        val server = hostPort[0]
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
        val sni = queryParams["sni"] ?: queryParams["peer"] ?: server

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "trojan",
            server = server,
            port = port,
            uuidOrPassword = password,
            tls = true,
            sni = sni,
            rawUri = uri
        )
    }

    private fun parseHysteria2(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("hy2://").removePrefix("hysteria2://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "Hysteria2 Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val password = atSplit[0]
        val hostPort = atSplit[1].split(":", limit = 2)

        val server = hostPort[0]
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
        val sni = queryParams["sni"] ?: server
        val insecure = queryParams["insecure"] == "1" || queryParams["allow_insecure"] == "1"

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "hysteria2",
            server = server,
            port = port,
            uuidOrPassword = password,
            tls = true,
            sni = sni,
            allowInsecure = insecure,
            rawUri = uri
        )
    }

    private fun parseSocks(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("socks5://").removePrefix("socks://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "SOCKS5 Node"
        val body = nameSplit[0]

        val atSplit = body.split("@", limit = 2)
        return if (atSplit.size == 2) {
            val userPass = atSplit[0].split(":", limit = 2)
            val hostPort = atSplit[1].split(":", limit = 2)
            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "socks",
                server = hostPort[0],
                port = hostPort.getOrNull(1)?.toIntOrNull() ?: 1080,
                uuidOrPassword = userPass.getOrElse(1) { "" },
                host = userPass.getOrElse(0) { "" },
                rawUri = uri
            )
        } else {
            val hostPort = body.split(":", limit = 2)
            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "socks",
                server = hostPort[0],
                port = hostPort.getOrNull(1)?.toIntOrNull() ?: 1080,
                rawUri = uri
            )
        }
    }

    private fun parseHttp(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("http://").removePrefix("https://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "HTTP Node"
        val body = nameSplit[0]

        val atSplit = body.split("@", limit = 2)
        val tls = uri.startsWith("https://", ignoreCase = true)
        return if (atSplit.size == 2) {
            val hostPort = atSplit[1].split(":", limit = 2)
            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "http",
                server = hostPort[0],
                port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8080,
                tls = tls,
                rawUri = uri
            )
        } else {
            val hostPort = body.split(":", limit = 2)
            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "http",
                server = hostPort[0],
                port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8080,
                tls = tls,
                rawUri = uri
            )
        }
    }

    private fun parseSingBoxJson(jsonStr: String, subscriptionId: Long): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        try {
            val root = JSONObject(jsonStr)
            val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val type = ob.optString("type", "")
                val tag = ob.optString("tag", "Node $i")
                val server = ob.optString("server", "")
                val port = ob.optInt("server_port", 0)

                if (server.isNotEmpty() && port > 0) {
                    val uuid = ob.optString("uuid", ob.optString("password", ""))
                    val tlsObj = ob.optJSONObject("tls")
                    val tls = tlsObj?.optBoolean("enabled", false) ?: false
                    val sni = tlsObj?.optString("server_name", "") ?: ""

                    nodes.add(
                        ProxyNode(
                            subscriptionId = subscriptionId,
                            name = tag,
                            protocol = type,
                            server = server,
                            port = port,
                            uuidOrPassword = uuid,
                            tls = tls,
                            sni = sni
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return nodes
    }

    private fun tryDecodeBase64(input: String): String {
        return try {
            val cleaned = input.trim().replace("\n", "").replace("\r", "")
            val decoded = Base64.decode(cleaned, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE)
            String(decoded, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            input
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        queryString.split("&").forEach { pair ->
            val split = pair.split("=", limit = 2)
            if (split.size == 2) {
                map[urlDecode(split[0])] = urlDecode(split[1])
            }
        }
        return map
    }

    private fun urlDecode(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }
}
