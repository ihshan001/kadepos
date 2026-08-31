package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.CashMovementEntity
import com.example.data.model.CashRegisterShiftEntity
import com.example.data.model.CreditTransactionEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.HeldSaleEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.NotificationSettingsEntity
import com.example.data.model.ProductEntity
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StaffEntity
import com.example.data.model.StockMovementEntity
import com.example.data.model.SupplierEntity

/**
 * The current schema version. Bumping this without adding the matching
 * migration to [ALL_MIGRATIONS] fails a unit test rather than a customer's
 * phone. Room needs a compile-time constant here, which is why it lives
 * outside the class.
 */
const val CURRENT_DB_VERSION = 5

/**
 * The whole app runs on this local SQLite database on the phone.
 * Nothing is seeded with demo shops, demo staff or demo sales: the only rows
 * that ever exist are the ones the shop owner creates during setup and while
 * trading.
 */
@Database(
    entities = [
        BusinessProfileEntity::class,
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        CustomerEntity::class,
        CreditTransactionEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        StockMovementEntity::class,
        ExpenseEntity::class,
        StaffEntity::class,
        CashRegisterShiftEntity::class,
        CashMovementEntity::class,
        HeldSaleEntity::class,
        AuditLogEntity::class,
        NotificationEntity::class,
        NotificationSettingsEntity::class
    ],
    version = CURRENT_DB_VERSION,
    // Schemas are exported to app/schemas and committed, so Room can verify at
    // build time that every migration produces exactly the schema it claims to.
    exportSchema = true
)
abstract class PosDatabase : RoomDatabase() {

    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: PosDatabase? = null

        fun getDatabase(context: Context): PosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PosDatabase::class.java,
                    "kadepos_database"
                )
                    // Real migrations. There is deliberately no
                    // fallbackToDestructiveMigration here: if a migration is
                    // ever missing we want a loud crash in testing, not a shop
                    // silently losing every sale it has ever made.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
