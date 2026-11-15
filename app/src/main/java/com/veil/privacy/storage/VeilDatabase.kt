package com.veil.privacy.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.model.TransportMessage

@Database(
    entities = [
        ProtectionRule::class,
        SelectedContact::class,
        ProtectedContact::class,
        ProtectedEvent::class,
        TransportMessage::class
    ],
    version = 8,
    exportSchema = false
)
abstract class VeilDatabase : RoomDatabase() {

    abstract fun protectionRuleDao(): ProtectionRuleDao
    abstract fun selectedContactDao(): SelectedContactDao
    abstract fun protectedContactDao(): ProtectedContactDao
    abstract fun protectedEventDao(): ProtectedEventDao
    abstract fun transportMessageDao(): TransportMessageDao

    companion object {
        @Volatile
        private var INSTANCE: VeilDatabase? = null

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE protection_rules ADD COLUMN lockPinHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE protection_rules ADD COLUMN lockPinSalt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE protection_rules ADD COLUMN isCustomLockEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE protection_rules ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE protected_contacts ADD COLUMN lockPinHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE protected_contacts ADD COLUMN lockPinSalt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE protected_contacts ADD COLUMN isCustomLockEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE protected_contacts ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE protection_rules ADD COLUMN appPackage TEXT NOT NULL DEFAULT 'com.whatsapp'")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS transport_messages (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, contactName TEXT NOT NULL, sourcePackage TEXT NOT NULL, direction TEXT NOT NULL, originalText TEXT NOT NULL, encodedPayload TEXT NOT NULL, packet TEXT NOT NULL, dictionaryVersion INTEGER NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastError TEXT NOT NULL)")
            }
        }

        fun getInstance(context: Context): VeilDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VeilDatabase::class.java,
                    "veil_privacy_database.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
