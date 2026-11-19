package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BranchEntity::class,
        CashierEntity::class,
        OrderSourceEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        OrderEntity::class,
        RefundEntity::class,
        ShiftEntity::class,
        CustomerEntity::class,
        StoreSettingsEntity::class,
        SyncQueueEntity::class,
        ProductOfferEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: PosDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentCategoryId TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_offers (
                        id TEXT NOT NULL PRIMARY KEY,
                        titleEn TEXT NOT NULL,
                        titleAr TEXT NOT NULL,
                        targetProductId TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        optionsJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN imageUri TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): PosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PosDatabase::class.java,
                    "cashfix_pos_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
