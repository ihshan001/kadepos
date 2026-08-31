package com.example.data.repository

import com.example.data.db.PosDao
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

class PosRepository(private val dao: PosDao) {

    val businessProfile: Flow<BusinessProfileEntity?> = dao.getProfile()
    val allProducts: Flow<List<ProductEntity>> = dao.getAllProducts()
    val lowStockProducts: Flow<List<ProductEntity>> = dao.getLowStockProducts()
    val outOfStockProducts: Flow<List<ProductEntity>> = dao.getOutOfStockProducts()
    val allSales: Flow<List<SaleEntity>> = dao.getAllSales()
    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers()
    val customersWithCredit: Flow<List<CustomerEntity>> = dao.getCustomersWithCredit()
    val allSuppliers: Flow<List<SupplierEntity>> = dao.getAllSuppliers()
    val allPurchases: Flow<List<PurchaseEntity>> = dao.getAllPurchases()
    val allExpenses: Flow<List<ExpenseEntity>> = dao.getAllExpenses()
    val allStaff: Flow<List<StaffEntity>> = dao.getAllStaff()
    val currentShift: Flow<CashRegisterShiftEntity?> = dao.getCurrentShift()
    val allShifts: Flow<List<CashRegisterShiftEntity>> = dao.getAllShifts()
    val allStockMovements: Flow<List<StockMovementEntity>> = dao.getAllStockMovements()
    val heldSales: Flow<List<HeldSaleEntity>> = dao.getHeldSales()

    suspend fun saveProfile(profile: BusinessProfileEntity) = dao.saveProfile(profile)
    suspend fun getProfileSync() = dao.getProfileSync()

    suspend fun insertProduct(product: ProductEntity) = dao.insertProduct(product)
    suspend fun updateProduct(product: ProductEntity) = dao.updateProduct(product)
    suspend fun archiveProduct(id: Long) = dao.archiveProduct(id)
    suspend fun deleteProduct(id: Long) = dao.deleteProduct(id)
    suspend fun insertProducts(products: List<ProductEntity>) = dao.insertProducts(products)
    suspend fun clearAndPreloadProducts(products: List<ProductEntity>) {
        dao.clearAllProducts()
        dao.insertProducts(products)
    }
    suspend fun getProductByBarcode(barcode: String) = dao.getProductByBarcode(barcode)
    suspend fun getProductById(id: Long) = dao.getProductById(id)

    suspend fun insertCustomer(customer: CustomerEntity) = dao.insertCustomer(customer)
    suspend fun updateCustomer(customer: CustomerEntity) = dao.updateCustomer(customer)
    suspend fun deleteCustomer(id: Long) = dao.deleteCustomer(id)
    fun getCreditTransactions(customerId: Long) = dao.getCreditTransactions(customerId)

    suspend fun recordManualCustomerCredit(
        customerId: Long,
        amount: Double,
        reason: String,
        note: String
    ) {
        val customer = dao.getCustomerById(customerId) ?: return
        val newBalance = customer.creditBalance + amount
        dao.updateCustomer(customer.copy(creditBalance = newBalance))
        dao.insertCreditTransaction(
            CreditTransactionEntity(
                customerId = customerId,
                type = "ADJUSTMENT",
                amount = amount,
                balanceAfter = newBalance,
                paymentMethod = "CREDIT",
                note = if (reason.isNotBlank()) "$reason - $note" else note
            )
        )
    }

    suspend fun recordCustomerCreditPayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        note: String
    ) {
        val customer = dao.getCustomerById(customerId) ?: return
        val newBalance = (customer.creditBalance - amount).coerceAtLeast(0.0)
        dao.updateCustomer(customer.copy(creditBalance = newBalance))
        dao.insertCreditTransaction(
            CreditTransactionEntity(
                customerId = customerId,
                type = "PAYMENT",
                amount = amount,
                balanceAfter = newBalance,
                paymentMethod = paymentMethod,
                note = note
            )
        )

        // If cash, update active shift drawer
        if (paymentMethod == "CASH") {
            val shift = dao.getCurrentShiftSync()
            if (shift != null && shift.status == "OPEN") {
                dao.updateShift(shift.copy(expectedCash = shift.expectedCash + amount))
                dao.insertCashMovement(
                    CashMovementEntity(
                        shiftId = shift.id,
                        type = "CASH_IN",
                        amount = amount,
                        reason = "Customer credit payment (${customer.name})",
                        note = note
                    )
                )
            }
        }
    }

    suspend fun insertSupplier(supplier: SupplierEntity) = dao.insertSupplier(supplier)
    suspend fun updateSupplier(supplier: SupplierEntity) = dao.updateSupplier(supplier)
    suspend fun deleteSupplier(id: Long) = dao.deleteSupplier(id)

    suspend fun recordSupplierPayment(supplierId: Long, amount: Double, paymentMethod: String, note: String) {
        val supplier = dao.getSupplierById(supplierId) ?: return
        val newBalance = (supplier.outstandingBalance - amount).coerceAtLeast(0.0)
        dao.updateSupplier(supplier.copy(outstandingBalance = newBalance))

        if (paymentMethod == "CASH") {
            val shift = dao.getCurrentShiftSync()
            if (shift != null && shift.status == "OPEN") {
                dao.updateShift(shift.copy(expectedCash = (shift.expectedCash - amount).coerceAtLeast(0.0)))
                dao.insertCashMovement(
                    CashMovementEntity(
                        shiftId = shift.id,
                        type = "CASH_OUT",
                        amount = amount,
                        reason = "Supplier payment (${supplier.name})",
                        note = note
                    )
                )
            }
        }
    }

    suspend fun getPurchaseItems(purchaseId: Long) = dao.getPurchaseItems(purchaseId)

    suspend fun settlePurchaseDue(purchaseId: Long, amount: Double, paymentMethod: String, note: String) {
        val purchase = dao.getPurchaseById(purchaseId) ?: return
        val newPaid = purchase.paidAmount + amount
        val newDue = (purchase.totalAmount - newPaid).coerceAtLeast(0.0)
        val newStatus = if (newDue <= 0.0) "PAID" else "PARTIAL"
        dao.updatePurchase(purchase.copy(paidAmount = newPaid, dueAmount = newDue, paymentStatus = newStatus))

        // Update supplier balance
        if (purchase.supplierId != null) {
            val sup = dao.getSupplierById(purchase.supplierId)
            if (sup != null) {
                dao.updateSupplier(sup.copy(outstandingBalance = (sup.outstandingBalance - amount).coerceAtLeast(0.0)))
            }
        }

        if (paymentMethod == "CASH") {
            val shift = dao.getCurrentShiftSync()
            if (shift != null && shift.status == "OPEN") {
                dao.updateShift(shift.copy(expectedCash = (shift.expectedCash - amount).coerceAtLeast(0.0)))
                dao.insertCashMovement(
                    CashMovementEntity(
                        shiftId = shift.id,
                        type = "CASH_OUT",
                        amount = amount,
                        reason = "PO Payment #${purchase.invoiceNumber}",
                        note = note
                    )
                )
            }
        }
    }

    suspend fun deletePurchase(id: Long) = dao.deletePurchase(id)

    suspend fun insertPurchase(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>
    ) {
        val purchaseId = dao.insertPurchase(purchase)
        val mappedItems = items.map { it.copy(purchaseId = purchaseId) }
        dao.insertPurchaseItems(mappedItems)

        // Update product stock and costs
        for (item in mappedItems) {
            val prod = dao.getProductById(item.productId)
            if (prod != null) {
                val updatedStock = prod.currentStock + item.quantity
                dao.updateProduct(prod.copy(currentStock = updatedStock, costPrice = item.costPrice))
                dao.insertStockMovement(
                    StockMovementEntity(
                        productId = prod.id,
                        productName = prod.name,
                        changeQty = item.quantity,
                        stockAfter = updatedStock,
                        type = "PURCHASE",
                        referenceId = purchase.invoiceNumber,
                        reason = "Purchase from ${purchase.supplierName}"
                    )
                )
            }
        }

        // If unpaid, update supplier balance
        if (purchase.dueAmount > 0 && purchase.supplierId != null) {
            dao.updateSupplierBalance(purchase.supplierId, purchase.dueAmount)
        }
    }

    suspend fun recordStockAdjustment(
        productId: Long,
        newCount: Double,
        reason: String,
        note: String
    ) {
        val prod = dao.getProductById(productId) ?: return
        val delta = newCount - prod.currentStock
        dao.updateProduct(prod.copy(currentStock = newCount))
        dao.insertStockMovement(
            StockMovementEntity(
                productId = prod.id,
                productName = prod.name,
                changeQty = delta,
                stockAfter = newCount,
                type = "ADJUST_${reason.uppercase()}",
                reason = "$reason: $note"
            )
        )
    }

    suspend fun receiveStockDirect(
        productId: Long,
        qty: Double,
        unitCost: Double,
        supplierName: String
    ) {
        val prod = dao.getProductById(productId) ?: return
        val newStock = prod.currentStock + qty
        dao.updateProduct(prod.copy(currentStock = newStock, costPrice = if (unitCost > 0) unitCost else prod.costPrice))
        dao.insertStockMovement(
            StockMovementEntity(
                productId = prod.id,
                productName = prod.name,
                changeQty = qty,
                stockAfter = newStock,
                type = "PURCHASE",
                reason = "Stock received from $supplierName"
            )
        )
    }

    suspend fun insertExpense(expense: ExpenseEntity) {
        dao.insertExpense(expense)
        if (expense.paymentMethod == "CASH") {
            val shift = dao.getCurrentShiftSync()
            if (shift != null && shift.status == "OPEN") {
                dao.updateShift(shift.copy(expectedCash = (shift.expectedCash - expense.amount).coerceAtLeast(0.0)))
                dao.insertCashMovement(
                    CashMovementEntity(
                        shiftId = shift.id,
                        type = "CASH_OUT",
                        amount = expense.amount,
                        reason = "Expense: ${expense.category}",
                        note = expense.note
                    )
                )
            }
        }
    }

    suspend fun deleteExpense(id: Long) = dao.deleteExpense(id)

    suspend fun insertStaff(staff: StaffEntity) = dao.insertStaff(staff)
    suspend fun updateStaff(staff: StaffEntity) = dao.updateStaff(staff)

    // Shift management
    suspend fun openShift(counterName: String, staffId: Long, staffName: String, openingCash: Double): Long {
        val shift = CashRegisterShiftEntity(
            counterName = counterName,
            staffId = staffId,
            staffName = staffName,
            openedAt = System.currentTimeMillis(),
            openingCash = openingCash,
            expectedCash = openingCash,
            actualCash = openingCash,
            status = "OPEN"
        )
        return dao.insertShift(shift)
    }

    suspend fun closeShift(shiftId: Long, actualCash: Double, reason: String) {
        val current = dao.getCurrentShiftSync() ?: return
        if (current.id == shiftId) {
            val diff = actualCash - current.expectedCash
            dao.updateShift(
                current.copy(
                    closedAt = System.currentTimeMillis(),
                    actualCash = actualCash,
                    difference = diff,
                    differenceReason = reason,
                    status = "CLOSED"
                )
            )
        }
    }

    suspend fun recordCashMovement(shiftId: Long, type: String, amount: Double, reason: String, note: String) {
        val shift = dao.getCurrentShiftSync() ?: return
        if (shift.id == shiftId) {
            val newExpected = if (type == "CASH_IN") shift.expectedCash + amount else (shift.expectedCash - amount).coerceAtLeast(0.0)
            dao.updateShift(shift.copy(expectedCash = newExpected))
            dao.insertCashMovement(
                CashMovementEntity(
                    shiftId = shiftId,
                    type = type,
                    amount = amount,
                    reason = reason,
                    note = note
                )
            )
        }
    }

    fun getCashMovements(shiftId: Long) = dao.getCashMovements(shiftId)

    suspend fun completeSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): Long {
        val activeShift = dao.getCurrentShiftSync()
        return dao.completeSaleTransaction(sale, items, activeShift)
    }

    suspend fun getSaleItems(saleId: Long) = dao.getSaleItemsSync(saleId)

    suspend fun processRefund(
        saleId: Long,
        refundedItems: List<SaleItemEntity>,
        refundAmount: Double,
        reason: String
    ) {
        val sale = dao.getSaleById(saleId) ?: return
        dao.updateSale(sale.copy(status = if (refundAmount >= sale.totalAmount) "REFUNDED" else "PARTIALLY_REFUNDED"))

        // Restock returned items
        for (item in refundedItems) {
            if (item.productId != null && item.productId > 0) {
                val prod = dao.getProductById(item.productId)
                if (prod != null && prod.isTracked) {
                    val newStock = prod.currentStock + item.returnedQuantity
                    dao.adjustProductStock(prod.id, item.returnedQuantity)
                    dao.insertStockMovement(
                        StockMovementEntity(
                            productId = prod.id,
                            productName = prod.name,
                            changeQty = item.returnedQuantity,
                            stockAfter = newStock,
                            type = "RETURN",
                            referenceId = sale.invoiceNumber,
                            reason = "Refund: $reason"
                        )
                    )
                }
            }
        }

        // If cash sale, deduct from drawer
        if (sale.paymentMethod == "CASH") {
            val shift = dao.getCurrentShiftSync()
            if (shift != null && shift.status == "OPEN") {
                dao.updateShift(shift.copy(expectedCash = (shift.expectedCash - refundAmount).coerceAtLeast(0.0)))
                dao.insertCashMovement(
                    CashMovementEntity(
                        shiftId = shift.id,
                        type = "CASH_OUT",
                        amount = refundAmount,
                        reason = "Refund for ${sale.invoiceNumber}",
                        note = reason
                    )
                )
            }
        }
    }

    suspend fun holdSale(heldSale: HeldSaleEntity) = dao.insertHeldSale(heldSale)
    suspend fun deleteHeldSale(id: Long) = dao.deleteHeldSale(id)
}
