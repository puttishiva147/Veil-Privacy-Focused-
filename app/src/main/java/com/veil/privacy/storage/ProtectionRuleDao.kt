package com.veil.privacy.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.veil.privacy.model.ProtectionRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectionRuleDao {

    @Query("SELECT * FROM protection_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<ProtectionRule>>

    @Query("SELECT * FROM protection_rules ORDER BY createdAt DESC")
    fun getAllRulesSync(): List<ProtectionRule>

    @Query("SELECT * FROM protection_rules WHERE isEnabled = 1")
    fun getEnabledRulesSync(): List<ProtectionRule>

    @Query("SELECT * FROM protection_rules WHERE isEnabled = 1")
    fun getEnabledRulesFlow(): Flow<List<ProtectionRule>>

    @Query("SELECT * FROM protection_rules WHERE id = :id LIMIT 1")
    fun getRuleByIdSync(id: Long): ProtectionRule?

    @Query("SELECT * FROM protection_rules WHERE normalizedPhoneNumber = :normalizedPhone LIMIT 1")
    fun findByNormalizedPhoneSync(normalizedPhone: String): ProtectionRule?

    @Query("SELECT * FROM protection_rules WHERE normalizedContactName = :normalizedName LIMIT 1")
    fun findByNormalizedNameSync(normalizedName: String): ProtectionRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: ProtectionRule): Long

    @Update
    fun update(rule: ProtectionRule)

    @Query("UPDATE protection_rules SET isEnabled = :isEnabled WHERE id = :id")
    fun updateEnabled(id: Long, isEnabled: Boolean)

    @Delete
    fun delete(rule: ProtectionRule)

    @Query("DELETE FROM protection_rules WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM protection_rules")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM protection_rules")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM protection_rules")
    fun getCountSync(): Int
}
