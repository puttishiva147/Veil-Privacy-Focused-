package com.veil.privacy.storage

import android.content.Context
import com.veil.privacy.model.ProtectedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * High-level manager for the local encrypted private event inbox.
 * All operations operate strictly on device storage.
 */
class VeilInbox(context: Context) {

    private val db = VeilDatabase.getInstance(context)
    private val eventDao = db.protectedEventDao()

    fun getAllEventsFlow(): Flow<List<ProtectedEvent>> = eventDao.getAllFlow()

    suspend fun getAllEvents(): List<ProtectedEvent> = withContext(Dispatchers.IO) {
        eventDao.getAllSync()
    }

    suspend fun getEventById(id: Long): ProtectedEvent? = withContext(Dispatchers.IO) {
        eventDao.getByIdSync(id)
    }

    suspend fun saveEvent(event: ProtectedEvent): Long = withContext(Dispatchers.IO) {
        eventDao.insert(event)
    }

    fun saveEventSync(event: ProtectedEvent): Long {
        return eventDao.insert(event)
    }

    suspend fun deleteEvent(id: Long) = withContext(Dispatchers.IO) {
        eventDao.deleteById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        eventDao.deleteAll()
    }

    fun getCountFlow(): Flow<Int> = eventDao.getCountFlow()

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        eventDao.getCountSync()
    }

    companion object {
        @Volatile
        private var INSTANCE: VeilInbox? = null

        fun getInstance(context: Context): VeilInbox {
            return INSTANCE ?: synchronized(this) {
                val instance = VeilInbox(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
