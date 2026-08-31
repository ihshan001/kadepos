package com.example.data.repository

import com.example.data.db.PosDao
import com.example.data.model.NotificationEntity
import com.example.data.model.NotificationSettingsEntity
import com.example.data.model.NotificationType
import com.example.data.model.NotificationImportance
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
import com.example.data.model.VariantCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PosRepository(private val dao: PosDao) {

    val businessProfile: Flow<BusinessProfileEntity?> = dao.getProfile()

    /** The shop type chosen during setup drives every product query. */
    private val activeShopType: Flow<String> =
        businessProfile.map { it?.shopTypeKey.orEmpty() }

    val allProducts: Flow<List<ProductEntity>> =
        activeShopType.flatMapLatest { dao.getProductsForShopType(it) }
    val lowStockProducts: Flow<List<ProductEntity>> =
        activeShopType.flatMapLatest { dao.getLowStockProducts(it) }
    val outOfStockProducts: Flow<List<ProductEntity>> =
        activeShopType.flatMapLatest { dao.getOutOfStockProducts(it) }
    val auditLog: Flow<List<AuditLogEntity>> = dao.getAuditLog()
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

    /**
     * Adds a product and records its opening stock as the first movement, so
     * the ledger and the cached figure agree from the moment it exists.
     */
    suspend fun insertProductWithOpeningStock(product: ProductEntity): Long {
        val id = dao.insertProduct(product)
        if (product.isTracked && product.currentStock > 0.0 && id > 0) {
            dao.insertStockMovement(
                StockMovementEntity(
                    productId = id,
                    productName = product.name,
                    changeQty = product.currentStock,
                    stockAfter = product.currentStock,
                    type = "INITIAL",
                    reason = "Opening stock when the item was added"
                )
            )
        }
        return id
    }
    suspend fun updateProduct(product: ProductEntity) = dao.updateProduct(product)
    suspend fun archiveProduct(id: Long) = dao.archiveProduct(id)
    suspend fun deleteProduct(id: Long) = dao.deleteProduct(id)
    suspend fun insertProducts(products: List<ProductEntity>) = dao.insertProducts(products)

    /**
     * Replaces the catalogue with the starter items for [shopType].
     * Everything belonging to another shop type is removed first so the two
     * catalogues can never mix, and nothing is inserted twice.
     */
    suspend fun installShopTypeCatalog(shopType: String, products: List<ProductEntity>) {
        dao.deleteProductsOutsideShopType(shopType)
        if (dao.countProductsForShopType(shopType) == 0) {
            dao.insertProducts(products)
            // Opening stock has to enter the ledger too, otherwise the sum of
            // movements would say zero while the shelf clearly is not empty.
            for (seeded in dao.getAllProductsSync()) {
                if (seeded.isTracked && seeded.currentStock > 0.0) {
                    dao.insertStockMovement(
                        StockMovementEntity(
                            productId = seeded.id,
                            productName = seeded.name,
                            changeQty = seeded.currentStock,
                            stockAfter = seeded.currentStock,
                            type = "INITIAL",
                            reason = "Opening stock when the shop was set up"
                        )
                    )
                }
            }
        }
    }

    /** Drops every product from other shop types when the owner switches type. */
    suspend fun pruneProductsOutsideShopType(shopType: String) =
        dao.deleteProductsOutsideShopType(shopType)

    suspend fun countProductsForShopType(shopType: String) = dao.countProductsForShopType(shopType)

    suspend fun clearAllProducts() = dao.clearAllProducts()

    suspend fun getVariantChildren(parentId: Long) = dao.getVariantChildren(parentId)

    suspend fun getAllProductsSync() = dao.getAllProductsSync()

    suspend fun getProductById(id: Long) = dao.getProductById(id)
    suspend fun getCustomerById(id: Long) = dao.getCustomerById(id)
    suspend fun getCurrentShiftSync() = dao.getCurrentShiftSync()

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
        dao.getCustomerById(customerId) ?: return
        // Append to the ledger first, then let the balance follow from it.
        val newBalance = dao.sumCreditTransactions(customerId) + amount
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
        refreshCustomerCredit(customerId)
    }

    /**
     * Recomputes `customers.creditBalance` from the credit ledger.
     *
     * Same reasoning as stock: the stored balance is a cache for listing and
     * sorting, `credit_transactions` is the truth. Recomputing means a credit
     * sale recorded on the counter phone and a repayment taken on the owner's
     * phone both land, instead of the second overwriting the first.
     */
    suspend fun refreshCustomerCredit(customerId: Long) {
        val customer = dao.getCustomerById(customerId) ?: return
        if (dao.countCreditTransactions(customerId) == 0) return
        val total = dao.sumCreditTransactions(customerId).coerceAtLeast(0.0)
        if (kotlin.math.abs(total - customer.creditBalance) > 0.0001) {
            dao.setCustomerCredit(customerId, total)
        }
    }

    /** Rebuilds every cached credit balance from the ledger. */
    suspend fun refreshAllCredit() {
        val totals = dao.sumAllCreditTransactions()
        for (row in totals) {
            dao.setCustomerCredit(row.customerId, row.total.coerceAtLeast(0.0))
        }
    }

    suspend fun recordCustomerCreditPayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        note: String
    ) {
        val customer = dao.getCustomerById(customerId) ?: return
        val newBalance = (dao.sumCreditTransactions(customerId) - amount).coerceAtLeast(0.0)
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
        refreshCustomerCredit(customerId)

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

    /** Erases every sale, customer, supplier, product, stock and log row. */
    suspend fun clearAllBusinessData() = dao.clearAllBusinessData()

    /**
     * A simple, spreadsheet-friendly CSV of the current catalogue (including
     * the new sub-category and options columns). Used by Settings to hand a
     * shopkeeper a template they can fill in on a computer.
     */
    suspend fun exportProductsCsv(): String {
        val headers = listOf(
            "name", "sellingPrice", "costPrice", "barcode", "sku", "category",
            "subCategory", "unit", "currentStock", "lowStockThreshold", "tracked",
            "favourite", "variants"
        )
        val rows = dao.getAllProductsSync().map { p ->
            listOf(
                p.name, p.sellingPrice.toString(), p.costPrice.toString(), p.barcode,
                p.sku, p.category, p.subCategory, p.unit, p.currentStock.toString(),
                p.lowStockThreshold.toString(), p.isTracked.toString(), p.isFavourite.toString(),
                // Keep variants on one line so the CSV can be re-imported without
                // needing a multi-line quote/parse step.
                p.variants.replace("\r\n", ";").replace("\n", ";").replace("\r", ";")
            )
        }
        return buildString {
            append(headers.joinToString(",") { csvCell(it) })
            append("\n")
            rows.forEach { row ->
                append(row.joinToString(",") { csvCell(it) })
                append("\n")
            }
        }
    }

    /**
     * Imports products from the CSV exported above. Rows are inserted with their
     * opening stock as a ledger movement, exactly as a manually added product
     * would be, so the shelf count is never just a display figure.
     *
     * Returns (count of rows saved, list of human readable problems).
     */
    suspend fun importProductsFromCsv(raw: String, shopType: String): Pair<Int, List<String>> {
        var saved = 0
        val problems = mutableListOf<String>()
        val lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val dataLines = lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("name,", ignoreCase = true) || it.startsWith("name\"", ignoreCase = true) }

        dataLines.forEachIndexed { index, line ->
            val cells = parseCsvLine(line)
            if (cells.size < 2) {
                problems.add("Line ${index + 1}: needs at least a name and a selling price")
                return@forEachIndexed
            }
            val name = cells.getOrNull(0)?.trim().orEmpty()
            val selling = cells.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (name.isBlank() || selling == null || selling < 0.0) {
                problems.add("Line ${index + 1}: name is blank or the price is not a number")
                return@forEachIndexed
            }
            val product = ProductEntity(
                name = name,
                sellingPrice = selling,
                costPrice = cells.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 0.0,
                barcode = cells.getOrNull(3)?.trim().orEmpty(),
                sku = cells.getOrNull(4)?.trim().orEmpty(),
                category = cells.getOrNull(5)?.trim().orEmpty().ifBlank { "General" },
                subCategory = cells.getOrNull(6)?.trim().orEmpty(),
                unit = cells.getOrNull(7)?.trim().orEmpty().ifBlank { "Piece" },
                currentStock = cells.getOrNull(8)?.trim()?.toDoubleOrNull() ?: 0.0,
                lowStockThreshold = cells.getOrNull(9)?.trim()?.toDoubleOrNull() ?: 5.0,
                isTracked = cells.getOrNull(10)?.trim()?.toBooleanStrictOrNull() ?: true,
                isFavourite = cells.getOrNull(11)?.trim()?.toBooleanStrictOrNull() ?: false,
                variants = cells.getOrNull(12)?.trim().orEmpty(),
                shopType = shopType
            )
            val parentId = insertProductWithOpeningStock(product)
            // A CSV cell can carry simple definitions ("Regular|1200;Full|1800")
            // or deep ones ("Rice: Basmati|Keeri;Portion: Regular|Full;
            // Basmati/Regular|1200;..."). Build a stockable child line per
            // purchasable combination, matching the in-app add exactly.
            val combos = VariantCatalog.buildCombinations(product.variants, selling)
            combos.forEach { combo ->
                insertProductWithOpeningStock(
                    product.copy(
                        id = 0L,
                        name = VariantCatalog.childName(name, combo),
                        sellingPrice = combo.price,
                        variants = "",
                        parentProductId = parentId,
                        isVariant = true,
                        currentStock = 0.0,
                        isTracked = product.isTracked
                    )
                )
            }
            saved++
        }
        return saved to problems
    }

    private fun csvCell(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuote && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i += 2
                    continue
                }
                c == '"' -> inQuote = !inQuote
                c == ',' && !inQuote -> {
                    out.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        out.add(current.toString().trim())
        return out
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
                dao.updateProduct(prod.copy(costPrice = item.costPrice))
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
        // Stock comes from the ledger, so recompute each product we just moved.
        for (item in mappedItems) refreshProductStock(item.productId)
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
        // A recount states the truth, so the movement is the difference between
        // what the ledger currently says and what the shopkeeper actually counted.
        val ledger = dao.sumStockMovements(productId)
        val delta = newCount - ledger
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
        refreshProductStock(productId)
    }

    /**
     * Recomputes `products.currentStock` from the movement ledger.
     *
     * The stored figure is only a cache so screens can sort and filter cheaply;
     * `stock_movements` is the truth. Every write appends a movement and then
     * calls this, which is what makes two devices selling the last unit add up
     * correctly instead of one overwriting the other.
     */
    suspend fun refreshProductStock(productId: Long) {
        val prod = dao.getProductById(productId) ?: return
        // A product with no movements at all has never been counted; leave the
        // opening figure from the catalogue alone rather than zeroing it.
        if (dao.countStockMovements(productId) == 0) return
        val total = dao.sumStockMovements(productId).coerceAtLeast(0.0)
        if (kotlin.math.abs(total - prod.currentStock) > 0.0001) {
            dao.setProductStock(productId, total)
        }
    }

    /**
     * Rebuilds every cached stock figure. Cheap enough to run at startup and
     * the repair path if a cache is ever found to have drifted.
     */
    suspend fun refreshAllStock() {
        val totals = dao.sumAllStockMovements()
        for (row in totals) {
            dao.setProductStock(row.productId, row.total.coerceAtLeast(0.0))
        }
    }

    suspend fun receiveStockDirect(
        productId: Long,
        qty: Double,
        unitCost: Double,
        supplierName: String
    ) {
        val prod = dao.getProductById(productId) ?: return
        val newStock = prod.currentStock + qty
        if (unitCost > 0) {
            dao.updateProduct(prod.copy(costPrice = unitCost))
        }
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
        refreshProductStock(productId)
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
                    val newStock = dao.sumStockMovements(prod.id) + item.returnedQuantity
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
                    refreshProductStock(prod.id)
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

    suspend fun recordAudit(
        staffId: Long,
        staffName: String,
        action: String,
        description: String,
        amount: Double = 0.0,
        reference: String = ""
    ) {
        dao.insertAuditLog(
            AuditLogEntity(
                staffId = staffId,
                staffName = staffName,
                action = action,
                description = description,
                amount = amount,
                reference = reference
            )
        )
    }

    suspend fun holdSale(heldSale: HeldSaleEntity) = dao.insertHeldSale(heldSale)
    suspend fun deleteHeldSale(id: Long) = dao.deleteHeldSale(id)

    // ---- Notifications -------------------------------------------------------

    val notifications = dao.getNotifications()
    val unreadNotificationCount = dao.getUnreadNotificationCount()
    val notificationSettings = dao.getNotificationSettings()

    suspend fun notificationSettingsOrDefault(): NotificationSettingsEntity =
        dao.getNotificationSettingsSync() ?: NotificationSettingsEntity()

    suspend fun saveNotificationSettings(settings: NotificationSettingsEntity) =
        dao.saveNotificationSettings(settings)

    suspend fun markNotificationRead(id: Long) = dao.markNotificationRead(id)
    suspend fun markAllNotificationsRead() = dao.markAllNotificationsRead()
    suspend fun clearNotifications() = dao.clearNotifications()

    /**
     * Records something worth knowing about, if the shop asked to be told.
     * Returns true when it should also interrupt (buzz) rather than sit
     * quietly in the list - quiet hours and QUIET importance stay silent.
     */
    suspend fun notify(
        type: NotificationType,
        title: String,
        body: String,
        amount: Double = 0.0,
        actorName: String = "",
        reference: String = "",
        hourOfDay: Int = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
    ): Boolean {
        val settings = notificationSettingsOrDefault()
        if (!settings.isOn(type)) return false

        dao.insertNotification(
            NotificationEntity(
                type = type.key,
                title = title,
                body = body,
                amount = amount,
                actorName = actorName,
                reference = reference,
                importance = type.importance.name
            )
        )
        // Keep the table from growing without limit on a phone that runs for years.
        dao.trimNotifications()

        return type.importance != NotificationImportance.QUIET &&
            !settings.isQuietAt(hourOfDay)
    }
}
