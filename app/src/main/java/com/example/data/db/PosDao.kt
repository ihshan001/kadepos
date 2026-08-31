package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.AuditLogEntity
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
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {

    // --- Business Profile ---
    @Query("SELECT * FROM business_profile WHERE id = 1")
    fun getProfile(): Flow<BusinessProfileEntity?>

    @Query("SELECT * FROM business_profile WHERE id = 1")
    suspend fun getProfileSync(): BusinessProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: BusinessProfileEntity)

    // --- Products ---
    // Every product read is scoped to the active shop type so a grocery never
    // sees pharmacy items and vice-versa. Products saved before a shop type
    // existed (shopType = "") stay visible to their owner.
    @Query(
        "SELECT * FROM products WHERE isArchived = 0 AND (shopType = :shopType OR shopType = '') " +
            "ORDER BY isFavourite DESC, name ASC"
    )
    fun getProductsForShopType(shopType: String): Flow<List<ProductEntity>>

    @Query(
        "SELECT * FROM products WHERE isArchived = 0 AND isTracked = 1 " +
            "AND (shopType = :shopType OR shopType = '') AND currentStock <= lowStockThreshold " +
            "ORDER BY currentStock ASC"
    )
    fun getLowStockProducts(shopType: String): Flow<List<ProductEntity>>

    @Query(
        "SELECT * FROM products WHERE isArchived = 0 AND isTracked = 1 " +
            "AND (shopType = :shopType OR shopType = '') AND currentStock <= 0"
    )
    fun getOutOfStockProducts(shopType: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE shopType = :shopType")
    suspend fun countProductsForShopType(shopType: String): Int

    @Query("DELETE FROM products WHERE shopType != :shopType AND shopType != ''")
    suspend fun deleteProductsOutsideShopType(shopType: String)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query(
        "SELECT * FROM products WHERE barcode = :barcode AND isArchived = 0 " +
            "AND (shopType = :shopType OR shopType = '') LIMIT 1"
    )
    suspend fun getProductByBarcode(barcode: String, shopType: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET isArchived = 1 WHERE id = :id")
    suspend fun archiveProduct(id: Long)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("UPDATE products SET currentStock = currentStock + :delta, updatedAt = :time WHERE id = :id")
    suspend fun adjustProductStock(id: Long, delta: Double, time: Long = System.currentTimeMillis())

    // --- Sales ---
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getSalesSince(startTime: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItems(saleId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsSync(saleId: Long): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Update
    suspend fun updateSaleItem(item: SaleItemEntity)

    // --- Customers & Credit ---
    @Query("SELECT * FROM customers ORDER BY isFavourite DESC, name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE creditBalance > 0 ORDER BY creditBalance DESC")
    fun getCustomersWithCredit(): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Query("UPDATE customers SET creditBalance = creditBalance + :delta WHERE id = :id")
    suspend fun updateCustomerCredit(id: Long, delta: Double)

    @Query("SELECT * FROM credit_transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getCreditTransactions(customerId: Long): Flow<List<CreditTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditTransaction(tx: CreditTransactionEntity): Long

    // --- Suppliers & Purchases ---
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: Long): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplier(id: Long)

    @Query("UPDATE suppliers SET outstandingBalance = outstandingBalance + :delta WHERE id = :id")
    suspend fun updateSupplierBalance(id: Long, delta: Double)

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseById(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getPurchasesBySupplier(supplierId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchase(id: Long)

    // --- Stock Movements ---
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 200")
    fun getAllStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp DESC")
    fun getStockMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity)

    // --- Expenses ---
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getExpensesSince(startTime: Long): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    // --- Staff ---
    @Query("SELECT * FROM staff ORDER BY name ASC")
    fun getAllStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getStaffById(id: Long): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: StaffEntity): Long

    @Update
    suspend fun updateStaff(staff: StaffEntity)

    // --- Shifts & Register ---
    @Query("SELECT * FROM cash_register_shifts ORDER BY openedAt DESC LIMIT 1")
    fun getCurrentShift(): Flow<CashRegisterShiftEntity?>

    @Query("SELECT * FROM cash_register_shifts ORDER BY openedAt DESC LIMIT 1")
    suspend fun getCurrentShiftSync(): CashRegisterShiftEntity?

    @Query("SELECT * FROM cash_register_shifts ORDER BY openedAt DESC")
    fun getAllShifts(): Flow<List<CashRegisterShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: CashRegisterShiftEntity): Long

    @Update
    suspend fun updateShift(shift: CashRegisterShiftEntity)

    @Query("SELECT * FROM cash_movements WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getCashMovements(shiftId: Long): Flow<List<CashMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: CashMovementEntity): Long

    // --- Audit Log ---
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 300")
    fun getAuditLog(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entry: AuditLogEntity): Long

    // --- Held Sales ---
    @Query("SELECT * FROM held_sales ORDER BY timestamp DESC")
    fun getHeldSales(): Flow<List<HeldSaleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeldSale(heldSale: HeldSaleEntity): Long

    @Query("DELETE FROM held_sales WHERE id = :id")
    suspend fun deleteHeldSale(id: Long)

    // --- High Level Transactions ---
    @Transaction
    suspend fun completeSaleTransaction(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        activeShift: CashRegisterShiftEntity?
    ): Long {
        val saleId = insertSale(sale)
        val preparedItems = items.map { it.copy(saleId = saleId) }
        insertSaleItems(preparedItems)

        // Decrement product stock if tracked
        for (item in preparedItems) {
            if (item.productId != null && item.productId > 0) {
                val prod = getProductById(item.productId)
                if (prod != null && prod.isTracked) {
                    val newStock = (prod.currentStock - item.quantity).coerceAtLeast(0.0)
                    adjustProductStock(prod.id, -item.quantity)
                    insertStockMovement(
                        StockMovementEntity(
                            productId = prod.id,
                            productName = prod.name,
                            changeQty = -item.quantity,
                            stockAfter = newStock,
                            type = "SALE",
                            referenceId = sale.invoiceNumber,
                            reason = "Sold on bill ${sale.invoiceNumber}",
                            staffName = sale.cashierName
                        )
                    )
                }
            }
        }

        // If credit sale, update customer
        if (sale.paymentMethod == "CREDIT" || sale.creditAmount > 0) {
            val cid = sale.customerId
            if (cid != null && cid > 0) {
                val creditToAdd = if (sale.paymentMethod == "CREDIT") sale.totalAmount else sale.creditAmount
                updateCustomerCredit(cid, creditToAdd)
                val cust = getCustomerById(cid)
                insertCreditTransaction(
                    CreditTransactionEntity(
                        customerId = cid,
                        saleId = saleId,
                        type = "SALE_CREDIT",
                        amount = creditToAdd,
                        balanceAfter = cust?.creditBalance ?: creditToAdd,
                        paymentMethod = "CREDIT",
                        note = "Credit sale ${sale.invoiceNumber}"
                    )
                )
            }
        }

        // Update shift expected cash if cash was received
        if (activeShift != null && activeShift.status == "OPEN") {
            val cashPortion = if (sale.paymentMethod == "CASH") sale.totalAmount else sale.cashReceived - sale.changeGiven
            if (cashPortion > 0) {
                updateShift(activeShift.copy(expectedCash = activeShift.expectedCash + cashPortion))
            }
        }

        return saleId
    }
}
