package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.ProxyNode
import com.example.data.model.ServerLog
import com.example.data.model.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY lastUpdated DESC")
    fun getAllSubscriptions(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscriptionById(id: Long): Subscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: Subscription): Long

    @Update
    suspend fun updateSubscription(subscription: Subscription)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: Long)
}

@Dao
interface ProxyNodeDao {
    @Query("SELECT * FROM proxy_nodes ORDER BY id ASC")
    fun getAllNodesFlow(): Flow<List<ProxyNode>>

    @Query("SELECT * FROM proxy_nodes ORDER BY id ASC")
    suspend fun getAllNodes(): List<ProxyNode>

    @Query("SELECT * FROM proxy_nodes WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabledNodes(): List<ProxyNode>

    @Query("SELECT * FROM proxy_nodes WHERE subscriptionId = :subId")
    suspend fun getNodesForSubscription(subId: Long): List<ProxyNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<ProxyNode>)

    @Query("DELETE FROM proxy_nodes WHERE subscriptionId = :subId")
    suspend fun deleteNodesForSubscription(subId: Long)

    @Query("DELETE FROM proxy_nodes")
    suspend fun deleteAllNodes()

    @Update
    suspend fun updateNode(node: ProxyNode)

    @Query("UPDATE proxy_nodes SET pingMs = :pingMs WHERE id = :nodeId")
    suspend fun updatePing(nodeId: Long, pingMs: Int)
}

@Dao
interface ServerLogDao {
    @Query("SELECT * FROM server_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<ServerLog>>

    @Insert
    suspend fun insertLog(log: ServerLog)

    @Query("DELETE FROM server_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [Subscription::class, ProxyNode::class, ServerLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun proxyNodeDao(): ProxyNodeDao
    abstract fun serverLogDao(): ServerLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "singbox_sub_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
