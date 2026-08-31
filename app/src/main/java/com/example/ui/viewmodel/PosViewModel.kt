package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PosDatabase
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.CashMovementEntity
import com.example.data.model.CashRegisterShiftEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.HeldSaleEntity
import com.example.data.model.ProductCatalogPresets
import com.example.data.model.ProductEntity
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StaffEntity
import com.example.data.model.SupplierEntity
import com.example.data.repository.PosRepository
import com.example.data.service.BluetoothPrinterService
import com.example.data.service.BluetoothPrinterState
import com.example.ui.util.CurrencyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartItem(
    val productId: Long? = null,
    val name: String,
    val unitPrice: Double,
    val costPrice: Double = 0.0,
    var quantity: Double = 1.0,
    var discount: Double = 0.0,
    val unit: String = "Piece",
    var note: String = ""
) {
    val lineTotal: Double
        get() = ((unitPrice * quantity) - discount).coerceAtLeast(0.0)
}

enum class PosTab(val title: String) {
    SELL("Sell"),
    SALES("Sales"),
    PRODUCTS("Products"),
    INVENTORY("Inventory"),
    MORE("More")
}

enum class MoreDestination {
    DASHBOARD,
    CUSTOMERS,
    CREDIT_BOOK,
    SUPPLIERS,
    PURCHASES,
    REPORTS,
    EXPENSES,
    STAFF,
    REGISTER,
    NOTIFICATIONS,
    SETTINGS
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PosRepository

    init {
        val db = PosDatabase.getDatabase(application)
        repository = PosRepository(db.posDao())

        viewModelScope.launch {
            repository.businessProfile.collect { prof ->
                if (prof != null && !prof.isConfigured && _onboardingStep.value == 0) {
                    _onboardingStep.value = 1
                }
            }
        }
    }

    val profile: StateFlow<BusinessProfileEntity?> = repository.businessProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val products: StateFlow<List<ProductEntity>> = repository.allProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val lowStockProducts: StateFlow<List<ProductEntity>> = repository.lowStockProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val outOfStockProducts: StateFlow<List<ProductEntity>> = repository.outOfStockProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val sales: StateFlow<List<SaleEntity>> = repository.allSales.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val suppliers: StateFlow<List<SupplierEntity>> = repository.allSuppliers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val purchases: StateFlow<List<PurchaseEntity>> = repository.allPurchases.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val staffList: StateFlow<List<StaffEntity>> = repository.allStaff.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val currentShift: StateFlow<CashRegisterShiftEntity?> = repository.currentShift.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val stockMovements = repository.allStockMovements.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val heldSales: StateFlow<List<HeldSaleEntity>> = repository.heldSales.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Navigation state
    private val _selectedTab = MutableStateFlow(PosTab.SELL)
    val selectedTab = _selectedTab.asStateFlow()

    private val _moreDestination = MutableStateFlow<MoreDestination?>(null)
    val moreDestination = _moreDestination.asStateFlow()

    // Onboarding step (0 = not in onboarding, 1..10 = onboarding steps)
    private val _onboardingStep = MutableStateFlow(0)
    val onboardingStep = _onboardingStep.asStateFlow()

    // Active Cart
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart = _cart.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer = _selectedCustomer.asStateFlow()

    private val _billDiscount = MutableStateFlow(0.0)
    val billDiscount = _billDiscount.asStateFlow()

    private val _billNote = MutableStateFlow("")
    val billNote = _billNote.asStateFlow()

    // Active Completed Sale for Modal / Receipt preview
    private val _lastCompletedSale = MutableStateFlow<SaleEntity?>(null)
    val lastCompletedSale = _lastCompletedSale.asStateFlow()

    private val _lastCompletedItems = MutableStateFlow<List<SaleItemEntity>>(emptyList())
    val lastCompletedItems = _lastCompletedItems.asStateFlow()

    private val _showSaleSuccessDialog = MutableStateFlow(false)
    val showSaleSuccessDialog = _showSaleSuccessDialog.asStateFlow()

    // Active UI Messages (Snackbar / Toast notifications)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    fun selectTab(tab: PosTab) {
        _selectedTab.value = tab
        _moreDestination.value = null
    }

    fun navigateMore(dest: MoreDestination) {
        _moreDestination.value = dest
    }

    fun clearMoreDestination() {
        _moreDestination.value = null
    }

    fun setOnboardingStep(step: Int) {
        _onboardingStep.value = step
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    // --- Cart operations ---
    fun addToCart(product: ProductEntity, quantity: Double = 1.0) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.productId == product.id && it.productId != null }
        if (index >= 0) {
            val existing = currentList[index]
            currentList[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            currentList.add(
                CartItem(
                    productId = product.id,
                    name = product.name,
                    unitPrice = product.sellingPrice,
                    costPrice = product.costPrice,
                    quantity = quantity,
                    unit = product.unit
                )
            )
        }
        _cart.value = currentList
        showMessage("Added ${product.name}")
    }

    fun addQuickItemToCart(name: String, price: Double, quantity: Double = 1.0, discount: Double = 0.0, unit: String = "Piece") {
        val currentList = _cart.value.toMutableList()
        currentList.add(
            CartItem(
                productId = null,
                name = name.ifBlank { "Quick Item" },
                unitPrice = price,
                quantity = quantity,
                discount = discount,
                unit = unit
            )
        )
        _cart.value = currentList
        showMessage("Added $name (${CurrencyUtils.formatLkr(price * quantity)})")
    }

    fun addQuickSaleToCart(amount: Double) {
        addQuickItemToCart(name = "Quick Sale", price = amount, quantity = 1.0)
    }

    fun updateCartItemQuantity(index: Int, newQty: Double) {
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            if (newQty <= 0) {
                list.removeAt(index)
            } else {
                list[index] = list[index].copy(quantity = newQty)
            }
            _cart.value = list
        }
    }

    fun updateCartItemDiscount(index: Int, discount: Double) {
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(discount = discount)
            _cart.value = list
        }
    }

    fun removeFromCart(index: Int) {
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            val removed = list.removeAt(index)
            _cart.value = list
            showMessage("Removed ${removed.name}")
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _selectedCustomer.value = null
        _billDiscount.value = 0.0
        _billNote.value = ""
    }

    fun selectCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    fun setBillDiscount(discount: Double) {
        _billDiscount.value = discount
    }

    fun setBillNote(note: String) {
        _billNote.value = note
    }

    // --- Checkout & Sale Completion ---
    fun completeSale(
        paymentMethod: String,
        cashReceived: Double = 0.0,
        cardAmount: Double = 0.0,
        creditAmount: Double = 0.0
    ) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return

        val subtotal = cartItems.sumOf { it.lineTotal }
        val finalTotal = (subtotal - _billDiscount.value).coerceAtLeast(0.0)
        val changeGiven = if (paymentMethod == "CASH") (cashReceived - finalTotal).coerceAtLeast(0.0) else 0.0

        val customer = _selectedCustomer.value
        val cashier = profile.value?.activeStaffName ?: "Staff"
        val cashierId = profile.value?.activeStaffId ?: 1L
        val invoiceNo = CurrencyUtils.generateInvoiceNumber()

        val sale = SaleEntity(
            invoiceNumber = invoiceNo,
            customerId = customer?.id,
            customerName = customer?.name ?: "Walk-in",
            customerPhone = customer?.phone ?: "",
            cashierId = cashierId,
            cashierName = cashier,
            timestamp = System.currentTimeMillis(),
            subtotal = subtotal,
            discountAmount = _billDiscount.value,
            totalAmount = finalTotal,
            paymentMethod = paymentMethod,
            cashReceived = if (paymentMethod == "CASH") cashReceived else 0.0,
            changeGiven = changeGiven,
            cardAmount = if (paymentMethod == "CARD") finalTotal else cardAmount,
            creditAmount = if (paymentMethod == "CREDIT") finalTotal else creditAmount,
            status = "COMPLETED",
            notes = _billNote.value
        )

        val saleItems = cartItems.map {
            SaleItemEntity(
                saleId = 0,
                productId = it.productId,
                productName = it.name,
                unitPrice = it.unitPrice,
                costPrice = it.costPrice,
                quantity = it.quantity,
                discount = it.discount,
                lineTotal = it.lineTotal,
                unit = it.unit
            )
        }

        viewModelScope.launch {
            val saleId = repository.completeSale(sale, saleItems)
            _lastCompletedSale.value = sale.copy(id = saleId)
            _lastCompletedItems.value = saleItems.map { it.copy(saleId = saleId) }
            _showSaleSuccessDialog.value = true
            clearCart()
        }
    }

    fun dismissSaleSuccess() {
        _showSaleSuccessDialog.value = false
    }

    // --- Hold / Park Sale ---
    fun holdCurrentSale(label: String) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return

        val total = (cartItems.sumOf { it.lineTotal } - _billDiscount.value).coerceAtLeast(0.0)
        val customer = _selectedCustomer.value
        val held = HeldSaleEntity(
            label = label.ifBlank { "Held #${(1000..9999).random()}" },
            customerId = customer?.id,
            customerName = customer?.name ?: "Walk-in",
            cartJson = "",
            totalAmount = total,
            itemsCount = cartItems.size
        )

        viewModelScope.launch {
            repository.holdSale(held)
            clearCart()
            showMessage("Bill parked: ${held.label}")
        }
    }

    fun resumeHeldSale(held: HeldSaleEntity) {
        viewModelScope.launch {
            repository.deleteHeldSale(held.id)
            // Re-populate dummy items for demonstration
            addQuickItemToCart("Resumed Bill Item", held.totalAmount, 1.0)
            selectTab(PosTab.SELL)
            showMessage("Resumed ${held.label}")
        }
    }

    // --- Product Actions ---
    fun saveProduct(
        id: Long,
        name: String,
        sellingPrice: Double,
        costPrice: Double,
        barcode: String,
        sku: String,
        category: String,
        unit: String,
        openingStock: Double,
        lowStock: Double,
        isTracked: Boolean,
        isFavourite: Boolean
    ) {
        viewModelScope.launch {
            if (id > 0) {
                val existing = repository.getProductById(id)
                if (existing != null) {
                    repository.updateProduct(
                        existing.copy(
                            name = name,
                            sellingPrice = sellingPrice,
                            costPrice = costPrice,
                            barcode = barcode,
                            sku = sku,
                            category = category,
                            unit = unit,
                            currentStock = if (openingStock >= 0) openingStock else existing.currentStock,
                            lowStockThreshold = lowStock,
                            isTracked = isTracked,
                            isFavourite = isFavourite,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    showMessage("Updated $name")
                }
            } else {
                val newProd = ProductEntity(
                    name = name,
                    sellingPrice = sellingPrice,
                    costPrice = costPrice,
                    barcode = barcode,
                    sku = sku,
                    category = category,
                    unit = unit,
                    currentStock = openingStock,
                    lowStockThreshold = lowStock,
                    isTracked = isTracked,
                    isFavourite = isFavourite
                )
                repository.insertProduct(newProd)
                showMessage("Added $name")
            }
        }
    }

    fun archiveProduct(productId: Long) {
        viewModelScope.launch {
            repository.archiveProduct(productId)
            showMessage("Product archived")
        }
    }

    // --- Customer & Credit Actions ---
    fun saveCustomer(
        id: Long,
        name: String,
        phone: String,
        email: String,
        address: String,
        creditLimit: Double,
        notes: String
    ) {
        viewModelScope.launch {
            if (id > 0) {
                val existing = customers.value.find { it.id == id }
                if (existing != null) {
                    repository.updateCustomer(
                        existing.copy(
                            name = name,
                            phone = phone,
                            email = email,
                            address = address,
                            creditLimit = creditLimit,
                            notes = notes
                        )
                    )
                    showMessage("Updated $name")
                }
            } else {
                val newCust = CustomerEntity(
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    creditLimit = creditLimit,
                    notes = notes
                )
                val newId = repository.insertCustomer(newCust)
                showMessage("Added customer $name")
                _selectedCustomer.value = newCust.copy(id = newId)
            }
        }
    }

    fun getCustomerTransactions(customerId: Long): kotlinx.coroutines.flow.Flow<List<com.example.data.model.CreditTransactionEntity>> {
        return repository.getCreditTransactions(customerId)
    }

    fun recordManualCustomerCredit(customerId: Long, amount: Double, reason: String, note: String) {
        viewModelScope.launch {
            repository.recordManualCustomerCredit(customerId, amount, reason, note)
            showMessage("Credit debit of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    fun deleteCustomer(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomer(id)
            showMessage("Customer deleted")
        }
    }

    fun recordCustomerCreditPayment(customerId: Long, amount: Double, paymentMethod: String, note: String) {
        viewModelScope.launch {
            repository.recordCustomerCreditPayment(customerId, amount, paymentMethod, note)
            showMessage("Payment of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    // --- Supplier & Purchase Actions ---
    fun saveSupplier(
        id: Long,
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        notes: String
    ) {
        viewModelScope.launch {
            if (id > 0) {
                val existing = suppliers.value.find { it.id == id }
                if (existing != null) {
                    repository.updateSupplier(
                        existing.copy(
                            name = name,
                            contactPerson = contactPerson,
                            phone = phone,
                            email = email,
                            address = address,
                            notes = notes
                        )
                    )
                    showMessage("Updated supplier $name")
                }
            } else {
                repository.insertSupplier(
                    SupplierEntity(
                        name = name,
                        contactPerson = contactPerson,
                        phone = phone,
                        email = email,
                        address = address,
                        notes = notes
                    )
                )
                showMessage("Added supplier $name")
            }
        }
    }

    fun deleteSupplier(supplierId: Long) {
        viewModelScope.launch {
            repository.deleteSupplier(supplierId)
            showMessage("Supplier deleted")
        }
    }

    fun recordSupplierPayment(supplierId: Long, amount: Double, paymentMethod: String, note: String) {
        viewModelScope.launch {
            repository.recordSupplierPayment(supplierId, amount, paymentMethod, note)
            showMessage("Supplier payment of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    fun settlePurchaseDue(purchaseId: Long, amount: Double, paymentMethod: String, note: String) {
        viewModelScope.launch {
            repository.settlePurchaseDue(purchaseId, amount, paymentMethod, note)
            showMessage("PO Payment of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity> {
        return repository.getPurchaseItems(purchaseId)
    }

    fun deletePurchase(purchaseId: Long) {
        viewModelScope.launch {
            repository.deletePurchase(purchaseId)
            showMessage("Purchase order deleted")
        }
    }

    fun createPurchase(
        supplier: SupplierEntity?,
        invoiceNumber: String,
        items: List<PurchaseItemEntity>,
        paidAmount: Double,
        notes: String
    ) {
        viewModelScope.launch {
            val total = items.sumOf { it.lineTotal }
            val due = (total - paidAmount).coerceAtLeast(0.0)
            val purchase = PurchaseEntity(
                supplierId = supplier?.id,
                supplierName = supplier?.name ?: "Local Supplier",
                invoiceNumber = invoiceNumber.ifBlank { "PO-${(1000..9999).random()}" },
                timestamp = System.currentTimeMillis(),
                totalAmount = total,
                paidAmount = paidAmount,
                dueAmount = due,
                paymentStatus = if (due <= 0) "PAID" else if (paidAmount > 0) "PARTIAL" else "DUE",
                itemsCount = items.size,
                notes = notes
            )
            repository.insertPurchase(purchase, items)
            showMessage("Purchase recorded. Stock updated automatically.")
        }
    }

    // --- Inventory direct actions ---
    fun receiveStockDirect(productId: Long, qty: Double, unitCost: Double, supplierName: String) {
        viewModelScope.launch {
            repository.receiveStockDirect(productId, qty, unitCost, supplierName)
            showMessage("Received +$qty units")
        }
    }

    fun receiveBatchStockDirect(items: List<Triple<ProductEntity, Double, Double>>, supplierName: String) {
        viewModelScope.launch {
            items.forEach { (prod, qty, cost) ->
                if (qty > 0) {
                    repository.receiveStockDirect(prod.id, qty, cost, supplierName)
                }
            }
            showMessage("Restocked ${items.size} products successfully")
        }
    }

    fun adjustStock(productId: Long, newCount: Double, reason: String, note: String) {
        viewModelScope.launch {
            repository.recordStockAdjustment(productId, newCount, reason, note)
            showMessage("Stock adjusted to $newCount")
        }
    }

    // --- Expenses ---
    fun addExpense(category: String, amount: Double, paymentMethod: String, reference: String, note: String) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                category = category,
                amount = amount,
                timestamp = System.currentTimeMillis(),
                paymentMethod = paymentMethod,
                reference = reference,
                note = note
            )
            repository.insertExpense(expense)
            showMessage("Expense ${CurrencyUtils.formatLkr(amount)} recorded ($category)")
        }
    }

    // --- Shifts & Cash Register ---
    fun openShift(counterName: String, staffId: Long, staffName: String, openingCash: Double) {
        viewModelScope.launch {
            repository.openShift(counterName, staffId, staffName, openingCash)
            showMessage("Shift opened for $staffName at $counterName")
        }
    }

    fun closeShift(shiftId: Long, actualCash: Double, reason: String) {
        viewModelScope.launch {
            repository.closeShift(shiftId, actualCash, reason)
            showMessage("Shift closed")
        }
    }

    fun recordCashMovement(shiftId: Long, type: String, amount: Double, reason: String, note: String) {
        viewModelScope.launch {
            repository.recordCashMovement(shiftId, type, amount, reason, note)
            val typeStr = if (type == "CASH_IN") "Cash in" else "Cash out"
            showMessage("$typeStr ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    // --- Refund & Returns ---
    fun processRefund(saleId: Long, refundItems: List<SaleItemEntity>, refundAmount: Double, reason: String) {
        viewModelScope.launch {
            repository.processRefund(saleId, refundItems, refundAmount, reason)
            showMessage("Refund of ${CurrencyUtils.formatLkr(refundAmount)} completed. Stock restocked.")
        }
    }

    // --- Staff switch & Setup ---
    fun switchActiveStaff(staff: StaffEntity) {
        viewModelScope.launch {
            val current = profile.value ?: BusinessProfileEntity()
            repository.saveProfile(
                current.copy(
                    activeStaffId = staff.id,
                    activeStaffName = "${staff.name} (${staff.role})"
                )
            )
            showMessage("Logged in as ${staff.name}")
        }
    }

    // --- Hardware Printer Service ---
    val bluetoothPrinterService = BluetoothPrinterService(application)
    val printerConnectionState = bluetoothPrinterService.connectionState
    val pairedBluetoothPrinters = bluetoothPrinterService.pairedPrinters

    fun refreshBluetoothPrinters() {
        bluetoothPrinterService.refreshPairedDevices()
    }

    fun connectBluetoothPrinter(address: String, name: String) {
        viewModelScope.launch {
            val success = bluetoothPrinterService.connectToPrinter(address, name)
            if (success) {
                val current = profile.value ?: BusinessProfileEntity()
                repository.saveProfile(current.copy(printerName = name, printerConnected = true))
                showMessage("Connected to $name")
            } else {
                showMessage("Could not connect to $name")
            }
        }
    }

    fun disconnectBluetoothPrinter() {
        bluetoothPrinterService.disconnect()
        viewModelScope.launch {
            val current = profile.value ?: BusinessProfileEntity()
            repository.saveProfile(current.copy(printerConnected = false))
            showMessage("Printer disconnected")
        }
    }

    fun printTestReceipt(paperWidth: String = "58mm") {
        viewModelScope.launch {
            val p = profile.value ?: BusinessProfileEntity()
            val ok = bluetoothPrinterService.printTestReceipt(
                storeName = p.name,
                phone = p.phone,
                address = p.address,
                paperWidth = paperWidth
            )
            if (ok) showMessage("Test receipt sent to printer") else showMessage("Printing failed")
        }
    }

    fun printBillReceipt(sale: SaleEntity, items: List<SaleItemEntity>) {
        viewModelScope.launch {
            val p = profile.value ?: BusinessProfileEntity()
            val ok = bluetoothPrinterService.printBillReceipt(
                storeName = p.name,
                phone = p.phone,
                address = p.address,
                invoiceNo = sale.invoiceNumber,
                cashier = sale.cashierName,
                customerName = sale.customerName,
                items = items.map { Triple(it.productName, it.quantity, it.lineTotal) },
                subtotal = sale.subtotal,
                discount = sale.discountAmount,
                tax = sale.taxAmount,
                grandTotal = sale.totalAmount,
                paymentMethod = sale.paymentMethod,
                cashReceived = sale.cashReceived,
                change = sale.changeGiven,
                footer = p.receiptFooter,
                currencySymbol = p.currencySymbol,
                paperWidth = p.printerPaperWidth
            )
            if (ok) showMessage("Receipt printed successfully") else showMessage("Printer error")
        }
    }

    // --- Product Catalog Preloading by Shop Type ---
    fun preloadProductsForShopType(shopTypeKey: String, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            val presetProducts = ProductCatalogPresets.getProductsForShopKey(shopTypeKey)
            if (replaceExisting) {
                repository.clearAndPreloadProducts(presetProducts)
            } else {
                repository.insertProducts(presetProducts)
            }
            showMessage("Loaded ${presetProducts.size} common items for your shop type")
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
            showMessage("Product deleted from catalog")
        }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            showMessage("Expense entry deleted")
        }
    }

    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity> {
        return repository.getSaleItems(saleId)
    }

    fun saveBusinessProfile(updated: BusinessProfileEntity) {
        viewModelScope.launch {
            repository.saveProfile(updated)
            showMessage("Business profile saved")
        }
    }
}
