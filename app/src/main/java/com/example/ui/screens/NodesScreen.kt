package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProxyNode
import com.example.ui.MainViewModel

@Composable
fun NodesScreen(viewModel: MainViewModel) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf("All") }

    val protocols = listOf("All", "vless", "vmess", "ss", "trojan", "hysteria2", "socks", "http")

    val filteredNodes = remember(nodes, searchQuery, selectedProtocol) {
        nodes.filter { node ->
            val matchesSearch = searchQuery.isBlank() ||
                    node.name.contains(searchQuery, ignoreCase = true) ||
                    node.server.contains(searchQuery, ignoreCase = true)

            val matchesProtocol = selectedProtocol == "All" ||
                    node.protocol.equals(selectedProtocol, ignoreCase = true)

            matchesSearch && matchesProtocol
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Search Bar & Ping All
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search nodes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("node_search_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.testAllNodesPing() },
                    modifier = Modifier.testTag("ping_all_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ping")
                }
            }
        }

        // Protocol Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                protocols.forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = { selectedProtocol = proto },
                        label = { Text(if (proto == "All") "All Protocols" else proto.uppercase()) }
                    )
                }
            }
        }

        item {
            Text(
                text = "Extracted Nodes (${filteredNodes.size} / ${nodes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (filteredNodes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (nodes.isEmpty()) "No nodes available. Add a subscription first." else "No nodes matched your search filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredNodes) { node ->
                NodeCard(
                    node = node,
                    onToggleEnabled = { viewModel.toggleNodeEnabled(node) },
                    onPing = { viewModel.testNodePing(node) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun NodeCard(
    node: ProxyNode,
    onToggleEnabled: () -> Unit,
    onPing: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("node_card_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = getProtocolColor(node.protocol)
                    ) {
                        Text(
                            text = node.protocol.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = node.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag("node_switch_${node.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${node.server}:${node.port}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.tls) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "TLS Enabled",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TLS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Ping latency badge
                    when {
                        node.pingMs > 0 -> {
                            Text(
                                text = "${node.pingMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (node.pingMs < 200) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                        node.pingMs == -2 -> {
                            Text(
                                text = "Timeout",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = "Untested",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onPing,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = "Test Ping",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun getProtocolColor(protocol: String): Color {
    return when (protocol.lowercase()) {
        "vless" -> Color(0xFF2563EB)
        "vmess" -> Color(0xFF7C3AED)
        "ss", "shadowsocks" -> Color(0xFF059669)
        "trojan" -> Color(0xFFDC2626)
        "hysteria2", "hy2" -> Color(0xFFD97706)
        else -> Color(0xFF475569)
    }
}
