package com.veil.privacy.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact

@Database(
    entities = [
        ProtectionRule::class,
        SelectedContact::class,
        ProtectedContact::class,
        ProtectedEvent::class
    ],
    version = 6,
    exportSchema = false
)
abstract class VeilDatabase : RoomDatabase() {

    abstract fun protectionRuleDao(): ProtectionRuleDao
    abstract fun selectedContactDao(): SelectedContactDao
    abstract fun protectedContactDao(): ProtectedContactDao
    abstract fun protectedEventDao(): ProtectedEventDao

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

        fun getInstance(context: Context): VeilDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VeilDatabase::class.java,
                    "veil_privacy_database.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
