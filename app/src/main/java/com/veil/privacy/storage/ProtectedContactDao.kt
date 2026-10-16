package com.veil.privacy.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.veil.privacy.model.ProtectedContact
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedContactDao {

    @Query("SELECT * FROM protected_contacts ORDER BY addedTimestamp DESC")
    fun getAllFlow(): Flow<List<ProtectedContact>>

    @Query("SELECT * FROM protected_contacts ORDER BY addedTimestamp DESC")
    fun getAllSync(): List<ProtectedContact>

    @Query("SELECT * FROM protected_contacts WHERE isEnabled = 1")
    fun getEnabledContactsSync(): List<ProtectedContact>

    @Query("SELECT * FROM protected_contacts WHERE normalizedName = :normalizedName LIMIT 1")
    fun findByNormalizedNameSync(normalizedName: String): ProtectedContact?

    @Query("SELECT * FROM protected_contacts WHERE normalizedPhoneNumber = :normalizedPhone LIMIT 1")
    fun findByNormalizedPhoneSync(normalizedPhone: String): ProtectedContact?

    @Query("DELETE FROM protected_contacts")
    fun clearAll()

    @Query("SELECT * FROM protected_contacts WHERE id = :id LIMIT 1")
    fun getByIdSync(id: Long): ProtectedContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(contact: ProtectedContact): Long

    @Update
    fun update(contact: ProtectedContact)

    @Delete
    fun delete(contact: ProtectedContact)

    @Query("DELETE FROM protected_contacts WHERE id = :id")
    fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM protected_contacts")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM protected_contacts")
    fun getCountSync(): Int
}
