package com.veil.privacy.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.veil.privacy.model.SelectedContact
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for 'SelectedContact' entity.
 * Supports retrieval for the base notification listener service and UI state management.
 */
@Dao
interface SelectedContactDao {

    @Query("SELECT * FROM selected_contacts ORDER BY addedTimestamp DESC")
    fun getAllFlow(): Flow<List<SelectedContact>>

    @Query("SELECT * FROM selected_contacts ORDER BY addedTimestamp DESC")
    fun getAllSync(): List<SelectedContact>

    @Query("SELECT * FROM selected_contacts WHERE isEnabled = 1")
    fun getEnabledContactsSync(): List<SelectedContact>

    @Query("SELECT * FROM selected_contacts WHERE isEnabled = 1")
    fun getEnabledContactsFlow(): Flow<List<SelectedContact>>

    @Query("SELECT * FROM selected_contacts WHERE normalizedPhoneNumber = :normalizedPhone LIMIT 1")
    fun findByNormalizedPhoneSync(normalizedPhone: String): SelectedContact?

    @Query("SELECT * FROM selected_contacts WHERE displayName = :displayName LIMIT 1")
    fun findByDisplayNameSync(displayName: String): SelectedContact?

    @Query("SELECT * FROM selected_contacts WHERE id = :id LIMIT 1")
    fun getByIdSync(id: Long): SelectedContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(contact: SelectedContact): Long

    @Update
    fun update(contact: SelectedContact)

    @Delete
    fun delete(contact: SelectedContact)

    @Query("DELETE FROM selected_contacts WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM selected_contacts")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM selected_contacts")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM selected_contacts")
    fun getCountSync(): Int
}
