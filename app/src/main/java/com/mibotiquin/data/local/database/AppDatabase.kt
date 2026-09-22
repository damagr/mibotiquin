package com.mibotiquin.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mibotiquin.data.local.dao.CabinetDao
import com.mibotiquin.data.local.dao.CustomCategoryDao
import com.mibotiquin.data.local.dao.ProductDao
import com.mibotiquin.data.local.entity.CabinetEntity
import com.mibotiquin.data.local.entity.CustomCategoryEntity
import com.mibotiquin.data.local.entity.ProductEntity


@Database(
    entities = [ProductEntity::class, CabinetEntity::class, CustomCategoryEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun cabinetDao(): CabinetDao
    abstract fun customCategoryDao(): CustomCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Botiquín por defecto para productos existentes antes de multi-botiquín */
        const val LEGACY_CABINET_ID = "legacy"
        const val LEGACY_CABINET_NAME = "Mi botiquín"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cabinets` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """INSERT INTO cabinets (id, name, updatedAt, createdAt)
                       VALUES ('$LEGACY_CABINET_ID', '$LEGACY_CABINET_NAME', $now, $now)"""
                )

                // Recrear products con cabinetId + índice único (barcode, cabinetId)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `products_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `expiryDate` INTEGER NOT NULL,
                        `cabinetId` TEXT NOT NULL DEFAULT '$LEGACY_CABINET_ID',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """INSERT INTO products_new (id, barcode, name, category, quantity, expiryDate, cabinetId, createdAt, updatedAt)
                       SELECT id, barcode, name, category, quantity, expiryDate, '$LEGACY_CABINET_ID', createdAt, updatedAt
                       FROM products"""
                )
                db.execSQL("DROP TABLE products")
                db.execSQL("ALTER TABLE products_new RENAME TO products")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_barcode_cabinetId ON products (barcode, cabinetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products (name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_category ON products (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_expiryDate ON products (expiryDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_cabinetId ON products (cabinetId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `custom_categories` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `order` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        // v4: el índice (barcode, cabinetId) deja de ser único — múltiples cajas del
        // mismo medicamento con distintas fechas/familias; deduplicación a nivel de repo
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_products_barcode_cabinetId")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode_cabinetId ON products (barcode, cabinetId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mibotiquin.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
