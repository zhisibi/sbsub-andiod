package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isRunning by viewModel.httpServer.isRunning.collectAsStateWithLifecycle()
    val port by viewModel.httpServer.port.collectAsStateWithLifecycle()
    val token by viewModel.httpServer.secretToken.collectAsStateWithLifecycle()
    val requestCount by viewModel.httpServer.requestCount.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val logs by viewModel.serverLogs.collectAsStateWithLifecycle()

    val activeNodeCount = remember(nodes) { nodes.count { it.enabled } }

    var qrModalUrl by remember { mutableStateOf<String?>(null) }
    var qrModalTitle by remember { mutableStateOf("") }

    val tokenParam = if (token.isNotEmpty()) "?token=$token" else ""

    val localhostUrl = "http://127.0.0.1:$port/sub?type=singbox$tokenParam"
    val lanUrl = "http://$localIp:$port/sub?type=singbox$tokenParam"

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Server Status Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_status_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) "LAN Server Active" else "Server Stopped",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.updateLocalIp() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh IP")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Nodes: $activeNodeCount / ${nodes.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Requests Handled: $requestCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleServer(port, token) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("toggle_server_button")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRunning) "Stop" else "Start Server")
                        }
                    }
                }
            }
        }

        // Connections & Links Section
        item {
            Text(
                text = "Local & LAN Connections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Localhost Link Card
        item {
            LinkCard(
                icon = Icons.Default.Router,
                title = "Local Device Link (127.0.0.1)",
                subtitle = "Use on this phone with Sing-box / NekoBox",
                url = localhostUrl,
                onCopy = { copyToClipboard(localhostUrl, "Localhost Link") },
                onQrCode = {
                    qrModalUrl = localhostUrl
                    qrModalTitle = "Localhost Subscription QR"
                }
            )
        }

        // LAN WiFi Link Card
        item {
            LinkCard(
                icon = Icons.Default.Lan,
                title = "LAN WiFi Link ($localIp)",
                subtitle = "Share with PC, Smart TV or phones on same WiFi",
                url = lanUrl,
                onCopy = { copyToClipboard(lanUrl, "LAN Link") },
                onQrCode = {
                    qrModalUrl = lanUrl
                    qrModalTitle = "LAN Subscription QR"
                }
            )
        }

        // Access Logs Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Server Access Logs (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (logs.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearAllLogs() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs")
                    }
                }
            }
        }

        // Access Logs List
        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "No request logs yet. Start server and query subscription endpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(logs) { log ->
                LogItemCard(log = log)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // QR Code Modal Dialog
    qrModalUrl?.let { url ->
        QrCodeModal(
            title = qrModalTitle,
            url = url,
            onDismiss = { qrModalUrl = null }
        )
    }
}

@Composable
fun LinkCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    url: String,
    onCopy: () -> Unit,
    onQrCode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onQrCode,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("QR Code", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onCopy,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun LogItemCard(log: com.example.data.model.ServerLog) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.clientIp,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = log.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text(
                    text = log.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
