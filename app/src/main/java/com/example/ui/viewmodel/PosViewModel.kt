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
import com.example.data.model.AuditLogEntity
import com.example.data.model.Permission
import com.example.data.model.PermissionSet
import com.example.data.model.StaffRole
import com.example.data.repository.PosRepository
import com.example.data.service.PrinterDevice
import com.example.data.service.PrinterService
import com.example.data.service.PrinterStatus
import com.example.data.service.PrinterTransport
import com.example.ui.util.CurrencyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartItem(
    val productId: Long? = null,
    val name: String,
    /** What this line is actually being sold at. Editable: retail haggling is normal. */
    var unitPrice: Double,
    /** The catalogue price, kept so the receipt and reports can show a markdown. */
    val listPrice: Double = unitPrice,
    val costPrice: Double = 0.0,
    var quantity: Double = 1.0,
    var discount: Double = 0.0,
    val unit: String = "Piece",
    var note: String = ""
) {
    val lineTotal: Double
        get() = ((unitPrice * quantity) - discount).coerceAtLeast(0.0)

    /** True when the cashier sold this below the normal price. */
    val isPriceChanged: Boolean
        get() = kotlin.math.abs(unitPrice - listPrice) > 0.001

    /** Negative when sold cheaper than the list price. */
    val priceDifference: Double
        get() = unitPrice - listPrice
}

enum class PosTab(val title: String) {
    SELL("Sell"),
    SALES("Bills"),
    PRODUCTS("Items"),
    INVENTORY("Stock"),
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
    PRINTER,
    ACTIVITY_LOG,
    SETTINGS
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PosRepository

    /** Real Bluetooth / Wi-Fi thermal printing. */
    val printerService = PrinterService(application)

    init {
        val db = PosDatabase.getDatabase(application)
        repository = PosRepository(db.posDao())

        viewModelScope.launch {
            // Make sure a profile row always exists so the setup wizard has
            // something to write into.
            if (repository.getProfileSync() == null) {
                repository.saveProfile(BusinessProfileEntity())
            }
        }

        // Silently reconnect to the saved printer on launch.
        viewModelScope.launch {
            val saved = repository.getProfileSync()
            if (saved != null && saved.printerAddress.isNotBlank() &&
                saved.printerConnectionType != "NONE"
            ) {
                printerService.reconnectSaved(
                    name = saved.printerName,
                    address = saved.printerAddress,
                    transport = runCatching {
                        PrinterTransport.valueOf(saved.printerConnectionType)
                    }.getOrDefault(PrinterTransport.BLUETOOTH),
                    port = saved.printerPort
                )
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

    val auditLog: StateFlow<List<AuditLogEntity>> = repository.auditLog.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ------------------------------------------------------------------
    // Who is using the app right now (role based access)
    // ------------------------------------------------------------------

    /** Set once someone signs in with their PIN. Null means nobody yet. */
    private val _signedInStaffId = MutableStateFlow<Long?>(null)
    val signedInStaffId = _signedInStaffId.asStateFlow()

    /**
     * The permissions in force. Solo shops (staff turned off during setup) run
     * as Owner without ever seeing a PIN screen.
     */
    val permissions: StateFlow<PermissionSet> =
        combine(profile, staffList, _signedInStaffId) { prof, staff, signedId ->
            when {
                prof == null -> PermissionSet.ownerFallback
                !prof.staffEnabled -> PermissionSet.of(StaffRole.OWNER, 0L, prof.name.ifBlank { "Owner" })
                else -> {
                    val member = staff.firstOrNull { it.id == signedId }
                    if (member == null) {
                        PermissionSet.ownerFallback.copy(granted = emptySet())
                    } else {
                        PermissionSet.of(StaffRole.fromName(member.role), member.id, member.name)
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PermissionSet.ownerFallback
        )

    /** True when the PIN screen must be shown before the app can be used. */
    val requiresSignIn: StateFlow<Boolean> =
        combine(profile, staffList, _signedInStaffId) { prof, staff, signedId ->
            prof != null &&
                prof.isConfigured &&
                prof.staffEnabled &&
                staff.any { it.isActive && it.pin.isNotBlank() } &&
                staff.none { it.id == signedId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun can(permission: Permission): Boolean = permissions.value.can(permission)

    /**
     * Runs [block] only if the signed-in user is allowed to; otherwise shows a
     * plain-language explanation. Returns whether it ran.
     */
    fun requirePermission(permission: Permission, block: () -> Unit): Boolean {
        return if (can(permission)) {
            block()
            true
        } else {
            showMessage(permissions.value.denialMessage(permission))
            false
        }
    }

    /** Sign in with a 4 digit PIN. Returns null on success or an error text. */
    fun signInWithPin(pin: String): String? {
        val match = staffList.value.firstOrNull { it.isActive && it.pin == pin && pin.isNotBlank() }
        return if (match == null) {
            "That PIN did not match. Please try again."
        } else {
            _signedInStaffId.value = match.id
            viewModelScope.launch {
                repository.saveProfile(
                    (repository.getProfileSync() ?: BusinessProfileEntity()).copy(
                        activeStaffId = match.id,
                        activeStaffName = match.name,
                        activeStaffRole = match.role
                    )
                )
                repository.recordAudit(
                    staffId = match.id,
                    staffName = match.name,
                    action = "LOGIN",
                    description = "${match.name} signed in as ${match.role}"
                )
            }
            showMessage("Welcome back, ${match.name}")
            null
        }
    }

    fun signOut() {
        val who = permissions.value
        _signedInStaffId.value = null
        _selectedTab.value = PosTab.SELL
        _moreDestination.value = null
        viewModelScope.launch {
            repository.recordAudit(who.staffId, who.staffName, "LOGOUT", "${who.staffName} signed out")
        }
    }

    private suspend fun audit(
        action: String,
        description: String,
        amount: Double = 0.0,
        reference: String = ""
    ) {
        val who = permissions.value
        repository.recordAudit(
            staffId = who.staffId,
            staffName = who.staffName.ifBlank { "Owner" },
            action = action,
            description = description,
            amount = amount,
            reference = reference
        )
    }

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

    /**
     * Sell this line at a different price. Common in retail: a regular customer
     * haggles, a damaged item goes cheap, a wholesale buyer gets a better rate.
     * Gated on CHANGE_PRICE so a cashier cannot quietly undercut the shop.
     */
    fun updateCartItemPrice(index: Int, newPrice: Double) {
        if (newPrice < 0.0) {
            showMessage("Price cannot be less than zero")
            return
        }
        requirePermission(Permission.CHANGE_PRICE) {
            val items = _cart.value.toMutableList()
            if (index in items.indices) {
                items[index] = items[index].copy(unitPrice = newPrice)
                _cart.value = items
            }
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
            val storedSale = sale.copy(id = saleId)
            val storedItems = saleItems.map { it.copy(saleId = saleId) }
            _lastCompletedSale.value = storedSale
            _lastCompletedItems.value = storedItems
            _showSaleSuccessDialog.value = true
            audit(
                action = "SALE",
                description = "Bill ${sale.invoiceNumber} completed (${paymentMethod.lowercase()})",
                amount = finalTotal,
                reference = sale.invoiceNumber
            )
            clearCart()

            // Print straight away when the owner asked for it during setup.
            val prof = repository.getProfileSync()
            if (prof?.autoPrint == true && printerService.isConnected) {
                printBillReceipt(storedSale, storedItems)
            }
        }
    }

    fun dismissSaleSuccess() {
        _showSaleSuccessDialog.value = false
    }

    // --- Hold / Park Sale ---
    fun holdCurrentSale(label: String) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) {
            showMessage("Add something to the bill first")
            return
        }

        val total = (cartItems.sumOf { it.lineTotal } - _billDiscount.value).coerceAtLeast(0.0)
        val customer = _selectedCustomer.value
        val held = HeldSaleEntity(
            label = label.ifBlank { "Bill ${CurrencyUtils.formatTimeOnly(System.currentTimeMillis())}" },
            customerId = customer?.id,
            customerName = customer?.name ?: "Walk-in",
            // The exact cart is stored so resuming restores every line, not a summary.
            cartJson = CartSerializer.encode(cartItems, _billDiscount.value, _billNote.value),
            totalAmount = total,
            itemsCount = cartItems.size
        )

        viewModelScope.launch {
            repository.holdSale(held)
            clearCart()
            showMessage("Bill kept aside: ${held.label}")
        }
    }

    fun resumeHeldSale(held: HeldSaleEntity) {
        viewModelScope.launch {
            val restored = CartSerializer.decode(held.cartJson)
            repository.deleteHeldSale(held.id)
            _cart.value = restored.items
            _billDiscount.value = restored.billDiscount
            _billNote.value = restored.billNote
            _selectedCustomer.value = held.customerId?.let { id ->
                customers.value.firstOrNull { it.id == id }
            }
            selectTab(PosTab.SELL)
            showMessage("Bill restored: ${held.label}")
        }
    }

    fun deleteHeldSale(heldId: Long) {
        viewModelScope.launch {
            repository.deleteHeldSale(heldId)
            showMessage("Kept-aside bill removed")
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
                    if (existing.sellingPrice != sellingPrice) {
                        audit(
                            action = "PRICE_CHANGE",
                            description = "$name price ${CurrencyUtils.formatLkr(existing.sellingPrice)} " +
                                "to ${CurrencyUtils.formatLkr(sellingPrice)}",
                            amount = sellingPrice
                        )
                    }
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
                    // New products join the shop type the business is set up for,
                    // so they show up alongside the right catalogue.
                    shopType = profile.value?.shopTypeKey.orEmpty(),
                    unit = unit,
                    currentStock = openingStock,
                    lowStockThreshold = lowStock,
                    isTracked = isTracked,
                    isFavourite = isFavourite
                )
                repository.insertProduct(newProd)
                audit("PRODUCT_ADDED", "Added product $name", sellingPrice)
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

    /**
     * Record a delivery the simple way: supplier, total bill, how much was paid
     * now. Most small shops get a handwritten invoice and do not want to key in
     * every line item just to remember they owe money.
     */
    fun recordSupplierBill(
        supplier: SupplierEntity?,
        totalAmount: Double,
        paidNow: Double,
        invoiceNumber: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            if (totalAmount <= 0.0) {
                showMessage("Enter the bill amount first")
                return@launch
            }
            val paid = paidNow.coerceIn(0.0, totalAmount)
            val due = totalAmount - paid
            val purchase = PurchaseEntity(
                supplierId = supplier?.id,
                supplierName = supplier?.name.orEmpty().ifBlank { "Supplier" },
                invoiceNumber = invoiceNumber,
                timestamp = System.currentTimeMillis(),
                totalAmount = totalAmount,
                paidAmount = paid,
                dueAmount = due,
                paymentStatus = when {
                    due <= 0.0 -> "PAID"
                    paid > 0.0 -> "PARTIAL"
                    else -> "DUE"
                },
                itemsCount = 0,
                notes = notes
            )
            repository.insertPurchase(purchase, emptyList())
            audit(
                action = "PURCHASE",
                description = "Bill from ${purchase.supplierName} for ${CurrencyUtils.formatLkr(totalAmount)}",
                amount = totalAmount
            )
            showMessage(
                if (due > 0) {
                    "Saved. You still owe ${CurrencyUtils.formatLkr(due)}."
                } else {
                    "Saved and fully paid."
                }
            )
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
            val typeStr = if (type == "CASH_IN") "Money put in" else "Money taken out"
            audit(action = type, description = "$typeStr: $reason", amount = amount)
            showMessage("$typeStr: ${CurrencyUtils.formatLkr(amount)}")
        }
    }

    // --- Refund & Returns ---
    fun processRefund(saleId: Long, refundItems: List<SaleItemEntity>, refundAmount: Double, reason: String) {
        if (!can(Permission.REFUND_SALE)) {
            showMessage(permissions.value.denialMessage(Permission.REFUND_SALE))
            return
        }
        viewModelScope.launch {
            repository.processRefund(saleId, refundItems, refundAmount, reason)
            audit(
                action = "REFUND",
                description = "Refunded ${refundItems.size} item(s): ${reason.ifBlank { "no reason given" }}",
                amount = refundAmount
            )
            showMessage("Money returned: ${CurrencyUtils.formatLkr(refundAmount)}. Stock put back.")
        }
    }

    // --- Staff & roles ---
    fun switchActiveStaff(staff: StaffEntity) {
        viewModelScope.launch {
            val current = repository.getProfileSync() ?: BusinessProfileEntity()
            repository.saveProfile(
                current.copy(
                    activeStaffId = staff.id,
                    activeStaffName = staff.name,
                    activeStaffRole = staff.role
                )
            )
            _signedInStaffId.value = staff.id
            audit("STAFF_SWITCH", "Now serving as ${staff.name} (${staff.role})")
            showMessage("Now serving as ${staff.name}")
        }
    }

    fun saveStaff(id: Long, name: String, phone: String, role: String, pin: String, isActive: Boolean) {
        if (!can(Permission.MANAGE_STAFF)) {
            showMessage(permissions.value.denialMessage(Permission.MANAGE_STAFF))
            return
        }
        viewModelScope.launch {
            if (id > 0) {
                val existing = staffList.value.firstOrNull { it.id == id } ?: return@launch
                repository.updateStaff(
                    existing.copy(
                        name = name,
                        phone = phone,
                        role = role,
                        pin = pin.ifBlank { existing.pin },
                        isActive = isActive
                    )
                )
                audit("STAFF_UPDATED", "Updated $name ($role)")
                showMessage("$name updated")
            } else {
                repository.insertStaff(
                    StaffEntity(
                        name = name,
                        phone = phone,
                        role = role,
                        pin = pin,
                        isActive = isActive
                    )
                )
                audit("STAFF_ADDED", "Added $name as $role")
                showMessage("$name added to your team")
            }
        }
    }

    fun setStaffActive(staff: StaffEntity, active: Boolean) {
        if (!can(Permission.MANAGE_STAFF)) {
            showMessage(permissions.value.denialMessage(Permission.MANAGE_STAFF))
            return
        }
        viewModelScope.launch {
            repository.updateStaff(staff.copy(isActive = active))
            audit("STAFF_UPDATED", "${if (active) "Enabled" else "Paused"} ${staff.name}")
            showMessage(if (active) "${staff.name} can sign in again" else "${staff.name} paused")
        }
    }

    // ------------------------------------------------------------------
    // Printer (real Bluetooth + Wi-Fi)
    // ------------------------------------------------------------------

    val printerStatus: StateFlow<PrinterStatus> = printerService.status
    val bluetoothPrinters: StateFlow<List<PrinterDevice>> = printerService.bluetoothPrinters
    val wifiPrinters: StateFlow<List<PrinterDevice>> = printerService.wifiPrinters
    val isScanningPrinters: StateFlow<Boolean> = printerService.isScanning

    /** Convenience flag for badges: is a printer actually connected right now. */
    val isPrinterConnected: StateFlow<Boolean> = printerService.status
        .map { it is PrinterStatus.Connected || it is PrinterStatus.Printing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun hasBluetoothPermission(): Boolean = printerService.hasBluetoothPermission()

    fun bluetoothPermissions(): Array<String> = printerService.requiredBluetoothPermissions

    fun refreshBluetoothPrinters() {
        val problem = printerService.refreshBluetoothPrinters()
        if (problem != null) showMessage(problem)
    }

    fun scanWifiPrinters(port: Int = 9100) {
        viewModelScope.launch {
            showMessage("Looking for printers on your Wi-Fi…")
            val problem = printerService.scanWifiPrinters(port)
            showMessage(problem ?: "Found ${printerService.wifiPrinters.value.size} Wi-Fi printer(s)")
        }
    }

    fun connectPrinter(device: PrinterDevice) {
        viewModelScope.launch {
            val problem = printerService.connect(device)
            if (problem == null) {
                val current = repository.getProfileSync() ?: BusinessProfileEntity()
                repository.saveProfile(
                    current.copy(
                        printerName = device.name,
                        printerAddress = device.address,
                        printerConnectionType = device.transport.name,
                        printerPort = device.port,
                        printerConnected = true
                    )
                )
                audit("PRINTER", "Connected printer ${device.name} over ${device.transport.label}")
                showMessage("Connected to ${device.name}")
            } else {
                showMessage(problem)
            }
        }
    }

    /** Adds a Wi-Fi printer the user typed in by hand. */
    fun connectWifiPrinterManually(ip: String, port: Int, name: String) {
        viewModelScope.launch {
            if (ip.isBlank()) {
                showMessage("Enter the printer's IP address first")
                return@launch
            }
            val reachable = printerService.checkWifiPrinter(ip, port)
            if (!reachable) {
                showMessage("No printer answered at $ip:$port. Check the address and Wi-Fi.")
                return@launch
            }
            connectPrinter(
                PrinterDevice(
                    name = name.ifBlank { "Printer at $ip" },
                    address = ip,
                    transport = PrinterTransport.WIFI,
                    port = port
                )
            )
        }
    }

    fun disconnectPrinter() {
        printerService.disconnect()
        viewModelScope.launch {
            val current = repository.getProfileSync() ?: BusinessProfileEntity()
            repository.saveProfile(current.copy(printerConnected = false))
            showMessage("Printer disconnected")
        }
    }

    fun forgetPrinter() {
        printerService.disconnect()
        viewModelScope.launch {
            val current = repository.getProfileSync() ?: BusinessProfileEntity()
            repository.saveProfile(
                current.copy(
                    printerName = "",
                    printerAddress = "",
                    printerConnectionType = "NONE",
                    printerConnected = false
                )
            )
            showMessage("Printer removed")
        }
    }

    fun printTestReceipt(paperWidth: String? = null) {
        viewModelScope.launch {
            val p = repository.getProfileSync() ?: BusinessProfileEntity()
            val problem = printerService.printTestPage(
                storeName = p.name,
                phone = p.phone,
                address = p.address,
                paperWidth = paperWidth ?: p.printerPaperWidth
            )
            showMessage(problem ?: "Test page sent to the printer")
        }
    }

    fun printBillReceipt(sale: SaleEntity, items: List<SaleItemEntity>) {
        viewModelScope.launch {
            val p = repository.getProfileSync() ?: BusinessProfileEntity()
            val problem = printerService.printReceipt(
                storeName = p.name,
                phone = p.phone,
                address = p.address,
                invoiceNo = sale.invoiceNumber,
                cashier = sale.cashierName,
                customerName = sale.customerName,
                items = items.map {
                    PrinterService.ReceiptLine(it.productName, it.quantity, it.unitPrice, it.lineTotal)
                },
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
            showMessage(problem ?: "Receipt printed")
        }
    }

    override fun onCleared() {
        super.onCleared()
        printerService.disconnect()
    }

    // ------------------------------------------------------------------
    // Shop type catalogue
    // ------------------------------------------------------------------

    /**
     * Installs the 50 starter items for [shopTypeKey] and removes anything
     * belonging to a different shop type, so categories can never overlap.
     */
    fun installShopCatalog(shopTypeKey: String) {
        viewModelScope.launch {
            val preset = ProductCatalogPresets.findShopType(shopTypeKey) ?: return@launch
            repository.installShopTypeCatalog(preset.key, preset.products)
            audit("CATALOG", "Loaded starter items for ${preset.displayName}")
            showMessage("${preset.products.size} ${preset.displayName} items are ready")
        }
    }

    /** Removes starter items without touching anything the owner added. */
    fun clearStarterCatalog() {
        viewModelScope.launch {
            repository.clearAllProducts()
            audit("CATALOG", "Cleared the product list")
            showMessage("Product list cleared")
        }
    }

    /** Switching shop type wipes the old catalogue so nothing overlaps. */
    fun changeShopType(shopTypeKey: String, loadStarterItems: Boolean) {
        viewModelScope.launch {
            val preset = ProductCatalogPresets.findShopType(shopTypeKey) ?: return@launch
            val current = repository.getProfileSync() ?: BusinessProfileEntity()
            repository.saveProfile(
                current.copy(shopTypeKey = preset.key, businessType = preset.businessType)
            )
            if (loadStarterItems) {
                repository.installShopTypeCatalog(preset.key, preset.products)
            } else {
                repository.pruneProductsOutsideShopType(preset.key)
            }
            audit("CATALOG", "Switched shop type to ${preset.displayName}")
            showMessage("Now set up for ${preset.displayName}")
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
            audit("SETTINGS", "Business details updated")
            showMessage("Saved")
        }
    }

    /**
     * Called at the end of the setup wizard. Writes the profile, installs the
     * chosen shop type's starter items and creates the owner's staff record.
     */
    fun finishSetup(
        profileToSave: BusinessProfileEntity,
        shopTypeKey: String,
        loadStarterItems: Boolean,
        ownerName: String,
        ownerPin: String
    ) {
        viewModelScope.launch {
            val preset = ProductCatalogPresets.findShopType(shopTypeKey)

            var ownerId = 0L
            if (profileToSave.staffEnabled && ownerName.isNotBlank()) {
                val existingOwner = staffList.value.firstOrNull {
                    it.name.equals(ownerName, ignoreCase = true)
                }
                ownerId = existingOwner?.id ?: repository.insertStaff(
                    StaffEntity(
                        name = ownerName,
                        role = "Owner",
                        pin = ownerPin,
                        isActive = true
                    )
                )
                _signedInStaffId.value = ownerId
            }

            repository.saveProfile(
                profileToSave.copy(
                    id = 1,
                    shopTypeKey = preset?.key.orEmpty(),
                    businessType = preset?.businessType ?: profileToSave.businessType,
                    activeStaffId = ownerId,
                    activeStaffName = ownerName.ifBlank { profileToSave.name },
                    activeStaffRole = "Owner",
                    requirePinOnOpen = profileToSave.staffEnabled && ownerPin.isNotBlank(),
                    isConfigured = true
                )
            )

            if (loadStarterItems && preset != null) {
                repository.installShopTypeCatalog(preset.key, preset.products)
            } else if (preset != null) {
                repository.pruneProductsOutsideShopType(preset.key)
            }

            _onboardingStep.value = 0
            audit("SETUP", "Setup finished for ${profileToSave.name}")
            showMessage("You're all set. Start selling!")
        }
    }

    /** Restarts the setup wizard from the settings screen. */
    fun restartSetup() {
        _onboardingStep.value = 1
    }

    /** Looks up a scanned or typed barcode inside the active shop's catalogue. */
    fun addProductByBarcode(barcode: String, onMissing: (String) -> Unit) {
        viewModelScope.launch {
            val shopType = profile.value?.shopTypeKey.orEmpty()
            val match = repository.getProductByBarcode(barcode.trim(), shopType)
            if (match != null) {
                addToCart(match)
            } else {
                onMissing(barcode.trim())
            }
        }
    }
}
