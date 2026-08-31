package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.CashMovementEntity
import com.example.data.model.CashRegisterShiftEntity
import com.example.data.model.CreditTransactionEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.HeldSaleEntity
import com.example.data.model.ProductEntity
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StaffEntity
import com.example.data.model.StockMovementEntity
import com.example.data.model.SupplierEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        HeldSaleEntity::class
    ],
    version = 1,
    exportSchema = false
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
                    "lk_pos_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default sample data in background
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { database ->
                                    populateInitialData(database.posDao())
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateInitialData(dao: PosDao) {
            // Initial Profile
            dao.saveProfile(
                BusinessProfileEntity(
                    id = 1,
                    name = "ABC Stores",
                    businessType = "Retail",
                    phone = "+94 77 123 4567",
                    address = "123 Main Street, Colombo 03",
                    currencySymbol = "Rs.",
                    managementLevel = "MANAGE_BUSINESS",
                    trackStock = true,
                    creditEnabled = true,
                    staffEnabled = true,
                    receiptStyle = "Modern",
                    receiptFooter = "Thank you for shopping with us! Please come again.",
                    receiptShowQr = true,
                    printerName = "MTP-58 (Bluetooth)",
                    printerPaperWidth = "58mm",
                    printerConnected = true,
                    autoPrint = false,
                    isConfigured = false,
                    activeStaffId = 1L,
                    activeStaffName = "Aslam (Cashier)"
                )
            )

            // Initial Staff
            val staff1 = dao.insertStaff(
                StaffEntity(
                    name = "Ihshan",
                    phone = "077 000 1111",
                    role = "Owner",
                    pin = "0000",
                    isActive = true
                )
            )
            val staff2 = dao.insertStaff(
                StaffEntity(
                    name = "Aslam",
                    phone = "077 123 4567",
                    role = "Cashier",
                    pin = "1234",
                    isActive = true
                )
            )
            val staff3 = dao.insertStaff(
                StaffEntity(
                    name = "Zakiya",
                    phone = "076 999 8888",
                    role = "Manager",
                    pin = "4321",
                    isActive = true
                )
            )

            // Initial Products
            val products = listOf(
                ProductEntity(name = "Coca Cola 500ml", sellingPrice = 240.0, costPrice = 190.0, barcode = "4791234567890", sku = "CC500", category = "Beverages", unit = "Bottle", currentStock = 18.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Prima Bread", sellingPrice = 180.0, costPrice = 140.0, barcode = "4791112223334", sku = "PB001", category = "Bakery", unit = "Piece", currentStock = 6.0, lowStockThreshold = 3.0, isFavourite = true),
                ProductEntity(name = "Highland Fresh Milk 1L", sellingPrice = 320.0, costPrice = 260.0, barcode = "4792223334445", sku = "ML001", category = "Dairy", unit = "Bottle", currentStock = 2.0, lowStockThreshold = 4.0, isFavourite = true),
                ProductEntity(name = "Mineral Water 1.5L", sellingPrice = 100.0, costPrice = 65.0, barcode = "4793334445556", sku = "WT150", category = "Beverages", unit = "Bottle", currentStock = 42.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Munchee Super Cream Cracker", sellingPrice = 220.0, costPrice = 175.0, barcode = "4794445556667", sku = "BSC01", category = "Snacks", unit = "Packet", currentStock = 25.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "White Sugar 1kg", sellingPrice = 280.0, costPrice = 230.0, barcode = "4795556667778", sku = "SGR01", category = "Grocery", unit = "Kg", currentStock = 15.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Keeri Samba Rice 5kg", sellingPrice = 1450.0, costPrice = 1200.0, barcode = "4796667778889", sku = "RC005", category = "Grocery", unit = "Packet", currentStock = 8.0, lowStockThreshold = 3.0, isFavourite = false),
                ProductEntity(name = "Sunlight Soap 100g", sellingPrice = 130.0, costPrice = 95.0, barcode = "4797778889990", sku = "SP001", category = "Household", unit = "Piece", currentStock = 30.0, lowStockThreshold = 6.0, isFavourite = false),
                ProductEntity(name = "DSI Slippers M", sellingPrice = 750.0, costPrice = 500.0, barcode = "4798889990001", sku = "SL001", category = "Footwear", unit = "Piece", currentStock = 12.0, lowStockThreshold = 2.0, isFavourite = false),
                ProductEntity(name = "USB-C Fast Charging Cable", sellingPrice = 450.0, costPrice = 220.0, barcode = "4799990001112", sku = "CBL01", category = "Electronics", unit = "Piece", currentStock = 0.0, lowStockThreshold = 3.0, isFavourite = false),
                ProductEntity(name = "Mobile Screen Guard Fixing", sellingPrice = 850.0, costPrice = 200.0, barcode = "", sku = "SRV01", category = "Services", unit = "Service", currentStock = 999.0, lowStockThreshold = 0.0, isTracked = false, isFavourite = true),
                ProductEntity(name = "Standard Haircut", sellingPrice = 1500.0, costPrice = 0.0, barcode = "", sku = "SRV02", category = "Services", unit = "Service", currentStock = 999.0, lowStockThreshold = 0.0, isTracked = false, isFavourite = false)
            )

            for (p in products) {
                dao.insertProduct(p)
            }

            // Initial Customers
            val c1 = dao.insertCustomer(
                CustomerEntity(
                    name = "Mohamed Ahamed",
                    phone = "077 123 4567",
                    creditBalance = 4500.0,
                    creditLimit = 15000.0,
                    totalPurchased = 18450.0,
                    purchaseCount = 14,
                    isFavourite = true,
                    notes = "Regular customer. Usually pays every Friday."
                )
            )
            val c2 = dao.insertCustomer(
                CustomerEntity(
                    name = "Kamal Stores (Wholesale)",
                    phone = "076 222 1111",
                    creditBalance = 8200.0,
                    creditLimit = 50000.0,
                    totalPurchased = 42800.0,
                    purchaseCount = 21,
                    isFavourite = true
                )
            )
            val c3 = dao.insertCustomer(
                CustomerEntity(
                    name = "Nimal Perera",
                    phone = "071 456 7890",
                    creditBalance = 0.0,
                    creditLimit = 10000.0,
                    totalPurchased = 6200.0,
                    purchaseCount = 8,
                    isFavourite = false
                )
            )

            // Initial Credit Transactions
            dao.insertCreditTransaction(
                CreditTransactionEntity(
                    customerId = c1,
                    type = "SALE_CREDIT",
                    amount = 5000.0,
                    balanceAfter = 5000.0,
                    timestamp = System.currentTimeMillis() - 86400000L * 2,
                    note = "Credit purchase on INV-002488"
                )
            )
            dao.insertCreditTransaction(
                CreditTransactionEntity(
                    customerId = c1,
                    type = "PAYMENT",
                    amount = 2000.0,
                    balanceAfter = 3000.0,
                    paymentMethod = "CASH",
                    timestamp = System.currentTimeMillis() - 86400000L,
                    note = "Partial settlement (Cash)"
                )
            )
            dao.insertCreditTransaction(
                CreditTransactionEntity(
                    customerId = c1,
                    type = "SALE_CREDIT",
                    amount = 1500.0,
                    balanceAfter = 4500.0,
                    timestamp = System.currentTimeMillis() - 3600000L * 3,
                    note = "Credit purchase on INV-002541"
                )
            )

            // Initial Suppliers
            val s1 = dao.insertSupplier(
                SupplierEntity(
                    name = "ABC Distributors (Ceylon)",
                    contactPerson = "Farhan",
                    phone = "077 444 5555",
                    outstandingBalance = 24500.0,
                    totalPurchased = 428500.0,
                    purchaseCount = 12,
                    notes = "Distributor for beverages and biscuits."
                )
            )
            val s2 = dao.insertSupplier(
                SupplierEntity(
                    name = "Lanka Wholesale Direct",
                    contactPerson = "Sunil",
                    phone = "071 888 9999",
                    outstandingBalance = 0.0,
                    totalPurchased = 185000.0,
                    purchaseCount = 8
                )
            )
            val s3 = dao.insertSupplier(
                SupplierEntity(
                    name = "City Suppliers & FMCG",
                    contactPerson = "Naveen",
                    phone = "076 333 4444",
                    outstandingBalance = 8200.0,
                    totalPurchased = 96000.0,
                    purchaseCount = 5
                )
            )

            // Initial Purchases
            dao.insertPurchase(
                PurchaseEntity(
                    supplierId = s1,
                    supplierName = "ABC Distributors (Ceylon)",
                    invoiceNumber = "INV-45821",
                    timestamp = System.currentTimeMillis() - 3600000L * 5,
                    totalAmount = 42000.0,
                    paidAmount = 20000.0,
                    dueAmount = 22000.0,
                    paymentStatus = "PARTIAL",
                    itemsCount = 12,
                    notes = "Weekly delivery"
                )
            )

            // Initial Expenses
            dao.insertExpense(
                ExpenseEntity(
                    category = "Electricity",
                    amount = 8500.0,
                    timestamp = System.currentTimeMillis() - 86400000L * 2,
                    paymentMethod = "BANK",
                    reference = "CEB-82931",
                    note = "Monthly shop electricity bill"
                )
            )
            dao.insertExpense(
                ExpenseEntity(
                    category = "Transport",
                    amount = 3200.0,
                    timestamp = System.currentTimeMillis() - 86400000L,
                    paymentMethod = "CASH",
                    reference = "TR-004",
                    note = "Goods delivery pickup three-wheeler"
                )
            )
            dao.insertExpense(
                ExpenseEntity(
                    category = "Packaging",
                    amount = 1850.0,
                    timestamp = System.currentTimeMillis() - 3600000L * 6,
                    paymentMethod = "CASH",
                    reference = "PK-01",
                    note = "Paper shopping bags bundle"
                )
            )

            // Initial Active Shift
            val shiftId = dao.insertShift(
                CashRegisterShiftEntity(
                    counterName = "Counter 01",
                    staffId = staff2,
                    staffName = "Aslam",
                    openedAt = System.currentTimeMillis() - 3600000L * 8,
                    openingCash = 10000.0,
                    expectedCash = 32500.0,
                    actualCash = 32500.0,
                    difference = 0.0,
                    status = "OPEN"
                )
            )

            // Initial Sales History
            val sale1 = SaleEntity(
                invoiceNumber = "INV-002538",
                customerName = "Walk-in",
                cashierName = "Aslam",
                timestamp = System.currentTimeMillis() - 3600000L * 4,
                subtotal = 450.0,
                totalAmount = 450.0,
                paymentMethod = "CASH",
                cashReceived = 500.0,
                changeGiven = 50.0
            )
            dao.insertSale(sale1)

            val sale2 = SaleEntity(
                invoiceNumber = "INV-002539",
                customerId = c1,
                customerName = "Mohamed Ahamed",
                customerPhone = "077 123 4567",
                cashierName = "Aslam",
                timestamp = System.currentTimeMillis() - 3600000L * 3,
                subtotal = 1200.0,
                totalAmount = 1200.0,
                paymentMethod = "CREDIT",
                creditAmount = 1200.0
            )
            dao.insertSale(sale2)

            val sale3 = SaleEntity(
                invoiceNumber = "INV-002540",
                customerName = "Walk-in",
                cashierName = "Aslam",
                timestamp = System.currentTimeMillis() - 3600000L * 2,
                subtotal = 2450.0,
                totalAmount = 2450.0,
                paymentMethod = "CARD",
                cardAmount = 2450.0
            )
            dao.insertSale(sale3)

            val sale4 = SaleEntity(
                invoiceNumber = "INV-002541",
                customerName = "Walk-in",
                cashierName = "Aslam",
                timestamp = System.currentTimeMillis() - 1800000L,
                subtotal = 980.0,
                totalAmount = 980.0,
                paymentMethod = "CASH",
                cashReceived = 1000.0,
                changeGiven = 20.0
            )
            val s4Id = dao.insertSale(sale4)
            dao.insertSaleItems(
                listOf(
                    SaleItemEntity(saleId = s4Id, productName = "Coca Cola 500ml", unitPrice = 240.0, quantity = 2.0, lineTotal = 480.0, unit = "Bottle"),
                    SaleItemEntity(saleId = s4Id, productName = "Prima Bread", unitPrice = 180.0, quantity = 1.0, lineTotal = 180.0, unit = "Piece"),
                    SaleItemEntity(saleId = s4Id, productName = "Highland Fresh Milk 1L", unitPrice = 320.0, quantity = 1.0, lineTotal = 320.0, unit = "Bottle")
                )
            )
        }
    }
}
