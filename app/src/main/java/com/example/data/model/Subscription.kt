package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val rawContent: String = "",
    val enabled: Boolean = true,
    val nodeCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val updateIntervalHours: Int = 24,
    val userAgent: String = "SingBoxSub-Android/1.0"
)

@Entity(tableName = "server_logs")
data class ServerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val path: String,
    val format: String, // singbox, clash, base64
    val userAgent: String,
    val statusCode: Int = 200
)
