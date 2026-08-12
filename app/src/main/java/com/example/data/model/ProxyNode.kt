package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_nodes")
data class ProxyNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long = 0,
    val name: String,
    val protocol: String, // vless, vmess, ss, trojan, hysteria2, socks, http
    val server: String,
    val port: Int,
    val uuidOrPassword: String = "",
    val cipher: String = "", // for ss/vmess
    val alterId: Int = 0, // for vmess
    val network: String = "tcp", // tcp, ws, grpc, quic, http
    val path: String = "",
    val host: String = "",
    val tls: Boolean = false,
    val sni: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val flow: String = "", // for vless
    val rawUri: String = "",
    val enabled: Boolean = true,
    val pingMs: Int = -1 // -1 = untested, >0 = ms latency, -2 = error
)
