package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ProxyNode
import com.example.data.model.ServerLog
import com.example.data.model.Subscription
import com.example.data.parser.SubscriptionParser
import com.example.server.LocalHttpServer
import com.example.server.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val httpServer = LocalHttpServer(application)

    val subscriptions: StateFlow<List<Subscription>> = db.subscriptionDao().getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nodes: StateFlow<List<ProxyNode>> = db.proxyNodeDao().getAllNodesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverLogs: StateFlow<List<ServerLog>> = db.serverLogDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _routingMode = MutableStateFlow("Rule") // Rule, Global, Direct
    val routingMode: StateFlow<String> = _routingMode.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        updateLocalIp()
        // Start server by default on port 8080
        httpServer.startServer(port = 8080, secretToken = "")
    }

    fun updateLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            _localIp.value = NetworkUtils.getLocalIpAddress()
        }
    }

    fun setRoutingMode(mode: String) {
        _routingMode.value = mode
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun toggleServer(port: Int = 8080, token: String = "") {
        if (httpServer.isRunning.value) {
            httpServer.stopServer()
            _statusMessage.value = "Local LAN server stopped"
        } else {
            updateLocalIp()
            httpServer.startServer(port, token)
            _statusMessage.value = "Server started on http://${_localIp.value}:$port"
        }
    }

    fun addSubscription(name: String, urlOrContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val isUrl = urlOrContent.startsWith("http://", ignoreCase = true) ||
                            urlOrContent.startsWith("https://", ignoreCase = true)

                val subName = if (name.isBlank()) (if (isUrl) "Subscription ${System.currentTimeMillis() % 1000}" else "Custom Node List") else name
                val initialSub = Subscription(name = subName, url = if (isUrl) urlOrContent else "")

                val subId = db.subscriptionDao().insertSubscription(initialSub)

                var rawText = urlOrContent
                if (isUrl) {
                    rawText = fetchUrlContent(urlOrContent)
                }

                val parsedNodes = SubscriptionParser.parseContent(rawText, subId)

                // Save nodes
                db.proxyNodeDao().deleteNodesForSubscription(subId)
                db.proxyNodeDao().insertNodes(parsedNodes)

                // Update sub node count
                db.subscriptionDao().updateSubscription(
                    initialSub.copy(
                        id = subId,
                        rawContent = rawText,
                        nodeCount = parsedNodes.size,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                _statusMessage.value = "Successfully imported ${parsedNodes.size} nodes"
            } catch (e: Exception) {
                _statusMessage.value = "Error importing: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshSubscription(subscription: Subscription) {
        if (subscription.url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val rawText = fetchUrlContent(subscription.url)
                val parsedNodes = SubscriptionParser.parseContent(rawText, subscription.id)

                db.proxyNodeDao().deleteNodesForSubscription(subscription.id)
                db.proxyNodeDao().insertNodes(parsedNodes)

                db.subscriptionDao().updateSubscription(
                    subscription.copy(
                        rawContent = rawText,
                        nodeCount = parsedNodes.size,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                _statusMessage.value = "Updated '${subscription.name}': ${parsedNodes.size} nodes"
            } catch (e: Exception) {
                _statusMessage.value = "Refresh failed: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteSubscription(subscriptionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.proxyNodeDao().deleteNodesForSubscription(subscriptionId)
            db.subscriptionDao().deleteSubscription(subscriptionId)
            _statusMessage.value = "Subscription deleted"
        }
    }

    fun toggleNodeEnabled(node: ProxyNode) {
        viewModelScope.launch(Dispatchers.IO) {
            db.proxyNodeDao().updateNode(node.copy(enabled = !node.enabled))
        }
    }

    fun testNodePing(node: ProxyNode) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var ping = -2
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(node.server, node.port), 3000)
                ping = (System.currentTimeMillis() - start).toInt()
                socket.close()
            } catch (e: Exception) {
                ping = -2
            }
            db.proxyNodeDao().updatePing(node.id, ping)
        }
    }

    fun testAllNodesPing() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val currentNodes = nodes.value
            currentNodes.forEach { node ->
                val start = System.currentTimeMillis()
                var ping = -2
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(node.server, node.port), 2000)
                    ping = (System.currentTimeMillis() - start).toInt()
                    socket.close()
                } catch (e: Exception) {
                    ping = -2
                }
                db.proxyNodeDao().updatePing(node.id, ping)
            }
            _isRefreshing.value = false
            _statusMessage.value = "Batch latency test completed"
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.serverLogDao().clearLogs()
        }
    }

    private suspend fun fetchUrlContent(urlStr: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "SingBoxSub-Android/1.0 (sing-box)")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: ""
        }
    }
}
