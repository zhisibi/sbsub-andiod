package com.example.server

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.generator.Base64Generator
import com.example.data.generator.ClashConfigGenerator
import com.example.data.generator.SingBoxConfigGenerator
import com.example.data.model.ServerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class LocalHttpServer(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _port = MutableStateFlow(8080)
    val port: StateFlow<Int> = _port

    private val _secretToken = MutableStateFlow("")
    val secretToken: StateFlow<String> = _secretToken

    private val _requestCount = MutableStateFlow(0)
    val requestCount: StateFlow<Int> = _requestCount

    fun startServer(port: Int = 8080, secretToken: String = "") {
        if (_isRunning.value) stopServer()

        _port.value = port
        _secretToken.value = secretToken

        serverJob = scope.launch {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                _isRunning.value = true

                while (_isRunning.value && !ss.isClosed) {
                    try {
                        val clientSocket = ss.accept()
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!_isRunning.value) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isRunning.value = false
            }
        }
    }

    fun stopServer() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@launch

                val method = parts[0]
                val fullPath = parts[1]

                // Read headers
                var userAgent = ""
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    if (line!!.startsWith("User-Agent:", ignoreCase = true)) {
                        userAgent = line!!.substringAfter(":").trim()
                    }
                }

                _requestCount.value += 1
                val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"

                val uriParts = fullPath.split("?", limit = 2)
                val path = uriParts[0]
                val queryParams = if (uriParts.size > 1) parseQueryParams(uriParts[1]) else emptyMap()

                // Token check
                val reqToken = queryParams["token"] ?: ""
                val currentSecret = _secretToken.value
                if (currentSecret.isNotEmpty() && reqToken != currentSecret) {
                    sendResponse(writer, 403, "Forbidden", "text/plain", "Error: Invalid Security Token")
                    logAccess(clientIp, path, "unauthorized", userAgent, 403)
                    socket.close()
                    return@launch
                }

                // Routing
                when {
                    path == "/" || path == "/index.html" -> {
                        val html = buildDashboardHtml(clientIp)
                        sendResponse(writer, 200, "OK", "text/html; charset=UTF-8", html)
                        logAccess(clientIp, path, "web_dashboard", userAgent, 200)
                    }
                    path == "/sub" || path == "/singbox" || path == "/config" || path == "/clash" || path == "/base64" -> {
                        val requestedType = queryParams["type"]?.lowercase() ?: when {
                            path == "/clash" -> "clash"
                            path == "/base64" -> "base64"
                            userAgent.contains("Clash", ignoreCase = true) -> "clash"
                            userAgent.contains("v2ray", ignoreCase = true) -> "base64"
                            else -> "singbox"
                        }

                        val nodes = db.proxyNodeDao().getEnabledNodes()

                        when (requestedType) {
                            "clash" -> {
                                val yaml = ClashConfigGenerator.generateYaml(nodes)
                                sendResponse(writer, 200, "OK", "text/yaml; charset=UTF-8", yaml)
                                logAccess(clientIp, path, "clash", userAgent, 200)
                            }
                            "base64" -> {
                                val b64 = Base64Generator.generateBase64(nodes)
                                sendResponse(writer, 200, "OK", "text/plain; charset=UTF-8", b64)
                                logAccess(clientIp, path, "base64", userAgent, 200)
                            }
                            else -> { // singbox
                                val json = SingBoxConfigGenerator.generateJson(nodes, inboundPort = 2080)
                                sendResponse(writer, 200, "OK", "application/json; charset=UTF-8", json)
                                logAccess(clientIp, path, "singbox", userAgent, 200)
                            }
                        }
                    }
                    else -> {
                        sendResponse(writer, 404, "Not Found", "text/plain", "404 Not Found")
                        logAccess(clientIp, path, "unknown", userAgent, 404)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun sendResponse(
        writer: PrintWriter,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, OPTIONS\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        writer.print(body)
        writer.flush()
    }

    private fun logAccess(clientIp: String, path: String, format: String, userAgent: String, statusCode: Int) {
        scope.launch {
            db.serverLogDao().insertLog(
                ServerLog(
                    clientIp = clientIp,
                    path = path,
                    format = format,
                    userAgent = userAgent,
                    statusCode = statusCode
                )
            )
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

    private fun buildDashboardHtml(clientIp: String): String {
        val lanIp = NetworkUtils.getLocalIpAddress()
        val port = _port.value
        val token = _secretToken.value
        val tokenParam = if (token.isNotEmpty()) "?token=$token" else ""

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SingBox Sub Android Server</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; margin: 0; }
                    .card { background: #1e293b; border-radius: 12px; padding: 20px; margin-bottom: 16px; border: 1px solid #334155; }
                    h1 { color: #38bdf8; margin-top: 0; font-size: 22px; }
                    .tag { display: inline-block; background: #0284c7; color: white; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: bold; margin-right: 6px; }
                    .url-box { background: #0f172a; padding: 12px; border-radius: 8px; font-family: monospace; color: #7dd3fc; word-break: break-all; margin: 8px 0; border: 1px solid #334155; }
                    a.btn { display: inline-block; background: #38bdf8; color: #0f172a; font-weight: bold; padding: 8px 16px; border-radius: 6px; text-decoration: none; margin-top: 8px; }
                </style>
            </head>
            <body>
                <h1>⚡ SingBox Sub Android Local Server</h1>
                <div class="card">
                    <div><span class="tag">Server Active</span> LAN IP: <strong>$lanIp:$port</strong></div>
                    <p style="color:#94a3b8; font-size: 14px;">Your client IP: $clientIp</p>
                </div>
                
                <div class="card">
                    <h3>🚀 Sing-Box JSON Subscription</h3>
                    <div class="url-box">http://$lanIp:$port/sub?type=singbox$tokenParam</div>
                    <a class="btn" href="/sub?type=singbox$tokenParam">Download Sing-Box Config</a>
                </div>

                <div class="card">
                    <h3>⚡ Clash YAML Subscription</h3>
                    <div class="url-box">http://$lanIp:$port/sub?type=clash$tokenParam</div>
                    <a class="btn" href="/sub?type=clash$tokenParam">Download Clash Config</a>
                </div>

                <div class="card">
                    <h3>🔗 Base64 Node List</h3>
                    <div class="url-box">http://$lanIp:$port/sub?type=base64$tokenParam</div>
                    <a class="btn" href="/sub?type=base64$tokenParam">View Base64 URIs</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
