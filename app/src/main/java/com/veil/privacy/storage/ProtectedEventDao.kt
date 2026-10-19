package com.veil.privacy.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.veil.privacy.model.ProtectedEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedEventDao {

    @Query("SELECT * FROM protected_events ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<ProtectedEvent>>

    @Query("SELECT * FROM protected_events ORDER BY timestamp DESC")
    fun getAllSync(): List<ProtectedEvent>

    @Query("SELECT * FROM protected_events WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversationFlow(conversationId: String): Flow<List<ProtectedEvent>>

    @Query("SELECT * FROM protected_events WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversationSync(conversationId: String): List<ProtectedEvent>

    @Query("SELECT * FROM protected_events WHERE conversationId = :conversationId AND sourcePackage = :sourcePackage ORDER BY timestamp ASC")
    fun getMessagesForSourceConversationFlow(conversationId: String, sourcePackage: String): Flow<List<ProtectedEvent>>

    @Query("SELECT * FROM protected_events WHERE id = :id LIMIT 1")
    fun getByIdSync(id: Long): ProtectedEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(event: ProtectedEvent): Long

    @Delete
    fun delete(event: ProtectedEvent)

    @Query("DELETE FROM protected_events WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM protected_events WHERE conversationId = :conversationId")
    fun deleteConversation(conversationId: String)

    @Query("DELETE FROM protected_events WHERE conversationId = :conversationId AND sourcePackage = :sourcePackage")
    fun deleteSourceConversation(conversationId: String, sourcePackage: String)

    @Query("DELETE FROM protected_events")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM protected_events")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM protected_events")
    fun getCountSync(): Int

    @Query("SELECT COUNT(*) FROM protected_events WHERE conversationId = :conversationId AND realMessage = :realMessage AND timestamp >= :minTimestamp")
    fun isRecentDuplicate(conversationId: String, realMessage: String, minTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM protected_events WHERE originalNotificationKey = :notificationKey AND originalNotificationKey IS NOT NULL AND originalNotificationKey != ''")
    fun hasEventWithNotificationKey(notificationKey: String): Int
}
