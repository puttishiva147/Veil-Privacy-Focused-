package com.veil.privacy.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.veil.privacy.model.TransportMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface TransportMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: TransportMessage)

    @Query("SELECT * FROM transport_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransportMessage?

    @Query("SELECT * FROM transport_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: String): Flow<List<TransportMessage>>

    @Query("SELECT * FROM transport_messages WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<TransportMessage>

    @Query("SELECT * FROM transport_messages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransportMessage>>

    @Query("DELETE FROM transport_messages WHERE id = :id")
    fun deleteById(id: String)
}
