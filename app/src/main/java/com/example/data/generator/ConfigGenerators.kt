package com.example.data.generator

import android.util.Base64
import com.example.data.model.ProxyNode
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object SingBoxConfigGenerator {

    fun generateJson(
        nodes: List<ProxyNode>,
        routingMode: String = "Rule", // Rule, Global, Direct
        inboundPort: Int = 2080
    ): String {
        val enabledNodes = nodes.filter { it.enabled }
        val root = JSONObject()

        // 1. Log configuration
        val log = JSONObject().apply {
            put("disabled", false)
            put("level", "info")
            put("timestamp", true)
        }
        root.put("log", log)

        // 2. DNS configuration
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "google")
                    put("address", "tls://8.8.8.8")
                })
                put(JSONObject().apply {
                    put("tag", "ali")
                    put("address", "223.5.5.5")
                    put("detour", "direct")
                })
            }
            put("servers", servers)
            put("strategy", "ipv4_only")
        }
        root.put("dns", dns)

        // 3. Inbounds configuration
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "0.0.0.0")
                put("listen_port", inboundPort)
                put("sniff", true)
            })
        }
        root.put("inbounds", inbounds)

        // 4. Outbounds configuration
        val outbounds = JSONArray()

        val nodeTags = enabledNodes.map { it.name }.filter { it.isNotEmpty() }

        // Selector outbound
        val selectorOutbound = JSONObject().apply {
            put("type", "selector")
            put("tag", "🚀 节点选择")
            val outList = JSONArray().apply {
                put("⚡ 自动选择")
                put("🎯 直连")
                nodeTags.forEach { put(it) }
            }
            put("outbounds", outList)
            put("default", if (nodeTags.isNotEmpty()) nodeTags[0] else "🎯 直连")
        }
        outbounds.put(selectorOutbound)

        // Auto URLTest outbound
        val autoTestOutbound = JSONObject().apply {
            put("type", "urltest")
            put("tag", "⚡ 自动选择")
            val outList = JSONArray().apply {
                nodeTags.forEach { put(it) }
            }
            put("outbounds", if (outList.length() > 0) outList else JSONArray().apply { put("🎯 直连") })
            put("url", "http://www.gstatic.com/generate_204")
            put("interval", "3m")
            put("tolerance", 50)
        }
        outbounds.put(autoTestOutbound)

        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "🎯 直连")
        })

        // Block outbound
        outbounds.put(JSONObject().apply {
            put("type", "block")
            put("tag", "🛑 拦截")
        })

        // Node outbounds
        enabledNodes.forEach { node ->
            val ob = buildNodeOutbound(node)
            if (ob != null) {
                outbounds.put(ob)
            }
        }

        root.put("outbounds", outbounds)

        // 5. Route configuration
        val route = JSONObject().apply {
            val rules = JSONArray()

            when (routingMode) {
                "Global" -> {
                    rules.put(JSONObject().apply {
                        put("outbound", "🚀 节点选择")
                    })
                }
                "Direct" -> {
                    rules.put(JSONObject().apply {
                        put("outbound", "🎯 直连")
                    })
                }
                else -> { // Rule mode
                    rules.put(JSONObject().apply {
                        put("protocol", "dns")
                        put("outbound", "direct")
                    })
                    rules.put(JSONObject().apply {
                        put("ip_is_private", true)
                        put("outbound", "🎯 直连")
                    })
                    rules.put(JSONObject().apply {
                        put("geosite", JSONArray().apply { put("cn") })
                        put("outbound", "🎯 直连")
                    })
                    rules.put(JSONObject().apply {
                        put("geoip", JSONArray().apply { put("cn") })
                        put("outbound", "🎯 直连")
                    })
                }
            }
            put("rules", rules)
            put("final", "🚀 节点选择")
            put("auto_detect_interface", true)
        }
        root.put("route", route)

        return root.toString(2)
    }

    private fun buildNodeOutbound(node: ProxyNode): JSONObject? {
        val ob = JSONObject()
        ob.put("tag", node.name)
        ob.put("server", node.server)
        ob.put("server_port", node.port)

        when (node.protocol.lowercase()) {
            "vless" -> {
                ob.put("type", "vless")
                ob.put("uuid", node.uuidOrPassword)
                if (node.flow.isNotEmpty()) ob.put("flow", node.flow)
                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                        put("insecure", node.allowInsecure)
                    }
                    ob.put("tls", tlsObj)
                }
                if (node.network.isNotEmpty() && node.network != "tcp") {
                    val transport = JSONObject().apply {
                        put("type", node.network)
                        if (node.path.isNotEmpty()) put("path", node.path)
                        if (node.host.isNotEmpty()) {
                            put("headers", JSONObject().apply {
                                put("Host", JSONArray().apply { put(node.host) })
                            })
                        }
                    }
                    ob.put("transport", transport)
                }
            }
            "vmess" -> {
                ob.put("type", "vmess")
                ob.put("uuid", node.uuidOrPassword)
                ob.put("security", if (node.cipher.isNotEmpty()) node.cipher else "auto")
                ob.put("alter_id", node.alterId)
                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    }
                    ob.put("tls", tlsObj)
                }
            }
            "ss", "shadowsocks" -> {
                ob.put("type", "shadowsocks")
                ob.put("method", if (node.cipher.isNotEmpty()) node.cipher else "aes-256-gcm")
                ob.put("password", node.uuidOrPassword)
            }
            "trojan" -> {
                ob.put("type", "trojan")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                }
                ob.put("tls", tlsObj)
            }
            "hysteria2", "hy2" -> {
                ob.put("type", "hysteria2")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    put("insecure", node.allowInsecure)
                }
                ob.put("tls", tlsObj)
            }
            "socks", "socks5" -> {
                ob.put("type", "socks")
                if (node.host.isNotEmpty()) ob.put("username", node.host)
                if (node.uuidOrPassword.isNotEmpty()) ob.put("password", node.uuidOrPassword)
            }
            "http" -> {
                ob.put("type", "http")
                if (node.host.isNotEmpty()) ob.put("username", node.host)
                if (node.uuidOrPassword.isNotEmpty()) ob.put("password", node.uuidOrPassword)
            }
            else -> return null
        }

        return ob
    }
}

object ClashConfigGenerator {
    fun generateYaml(nodes: List<ProxyNode>): String {
        val enabled = nodes.filter { it.enabled }
        val sb = StringBuilder()
        sb.appendLine("port: 7890")
        sb.appendLine("socks-port: 7891")
        sb.appendLine("allow-lan: true")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine()
        sb.appendLine("proxies:")

        enabled.forEach { node ->
            sb.appendLine("  - name: \"${node.name}\"")
            sb.appendLine("    type: ${mapClashType(node.protocol)}")
            sb.appendLine("    server: ${node.server}")
            sb.appendLine("    port: ${node.port}")
            if (node.uuidOrPassword.isNotEmpty()) {
                if (node.protocol == "ss") {
                    sb.appendLine("    cipher: ${if (node.cipher.isNotEmpty()) node.cipher else "aes-256-gcm"}")
                    sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                } else {
                    sb.appendLine("    uuid: \"${node.uuidOrPassword}\"")
                }
            }
            if (node.tls) {
                sb.appendLine("    tls: true")
                if (node.sni.isNotEmpty()) sb.appendLine("    sni: \"${node.sni}\"")
            }
        }

        sb.appendLine()
        sb.appendLine("proxy-groups:")
        sb.appendLine("  - name: PROXY")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - AUTO")
        enabled.forEach { sb.appendLine("      - \"${it.name}\"") }
        sb.appendLine("  - name: AUTO")
        sb.appendLine("    type: url-test")
        sb.appendLine("    url: http://www.gstatic.com/generate_204")
        sb.appendLine("    interval: 300")
        sb.appendLine("    proxies:")
        enabled.forEach { sb.appendLine("      - \"${it.name}\"") }

        return sb.toString()
    }

    private fun mapClashType(protocol: String): String {
        return when (protocol.lowercase()) {
            "vless" -> "vless"
            "vmess" -> "vmess"
            "ss" -> "ss"
            "trojan" -> "trojan"
            "hysteria2", "hy2" -> "hysteria2"
            "socks" -> "socks5"
            else -> "http"
        }
    }
}

object Base64Generator {
    fun generateBase64(nodes: List<ProxyNode>): String {
        val uris = nodes.filter { it.enabled }.joinToString("\n") { it.rawUri }
        return Base64.encodeToString(uris.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.DEFAULT)
    }
}
