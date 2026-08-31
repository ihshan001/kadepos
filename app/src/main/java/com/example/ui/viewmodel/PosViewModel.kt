package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudBackupManager
import com.example.data.cloud.CloudSettings
import com.example.data.cloud.CloudSettingsRepository
import com.example.data.cloud.CloudSyncScheduler
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
import com.example.data.model.NotificationEntity
import com.example.data.model.NotificationSettingsEntity
import com.example.data.model.NotificationType
import com.example.data.model.Permission
import com.example.data.model.PermissionOverrides
import com.example.data.model.VariantCatalog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** The alert history plus the switches controlling what gets announced. */
    ALERTS,
    PRINTER,
    ACTIVITY_LOG,
    CLOUD,
    SETTINGS
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PosRepository

    private val cloudRepo = CloudSettingsRepository(application)
    private val cloudBackup = CloudBackupManager(application)
    private val _cloudSettings = MutableStateFlow(cloudRepo.load())
    val cloudSettings: StateFlow<CloudSettings> = _cloudSettings.asStateFlow()

    /** Non-persistent in-memory flag while this ViewModel lives. */
    var isProviderUnlocked: Boolean = false
        private set

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

            // Stock and credit totals are caches over their ledgers. Rebuild
            // them once at launch so a figure can never stay wrong: if the app
            // was killed mid-write, or a future sync merges movements from
            // another device, this is what quietly puts it right.
            repository.refreshAllStock()
            repository.refreshAllCredit()
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

        // Keep the optional cloud feature in step with the provider policy.
        refreshCloud()
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
                // Solo shop: the owner said "it is just me" during setup, so
                // nothing is ever locked and no PIN is asked for.
                !prof.staffEnabled -> PermissionSet.soloOwner(prof.name)
                // Rescue hatch: a freshly converted team shop with nobody's PIN
                // set yet must not sign everyone out into a locked screen. Let
                // the owner in so they can set a PIN on someone first.
                staff.none { it.isActive && it.pin.isNotBlank() } ->
                    PermissionSet.soloOwner(prof.activeStaffName.ifBlank { prof.name })
                else -> {
                    val member = staff.firstOrNull { it.id == signedId }
                    if (member == null) {
                        PermissionSet.lockedOut
                    } else {
                        // Role defaults plus/minus whatever the owner tuned
                        // for this specific person.
                        PermissionSet.resolve(
                            role = StaffRole.fromName(member.role),
                            staffId = member.id,
                            staffName = member.name,
                            extraCsv = member.extraPermissions,
                            revokedCsv = member.revokedPermissions
                        )
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

    // --- Notifications ------------------------------------------------------

    /** Only the alerts this person is entitled to see. */
    val notifications: StateFlow<List<NotificationEntity>> =
        combine(repository.notifications, permissions) { list, who ->
            if (who.isSoloOwner) {
                list
            } else {
                list.filter { entry ->
                    val type = NotificationType.fromKey(entry.type)
                    type?.requires == null || who.can(type.requires)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> =
        notifications.map { list -> list.count { !it.isRead } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val notificationSettings: StateFlow<NotificationSettingsEntity> =
        repository.notificationSettings
            .map { it ?: NotificationSettingsEntity() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettingsEntity())

    fun markNotificationRead(id: Long) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    fun clearNotifications() {
        viewModelScope.launch { repository.clearNotifications() }
    }

    /** Turn one kind of alert on or off. */
    fun setNotificationType(type: NotificationType, on: Boolean) {
        viewModelScope.launch {
            val current = repository.notificationSettingsOrDefault()
            repository.saveNotificationSettings(current.withType(type, on))
        }
    }

    /** The master switch: off means the app never interrupts anyone. */
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.notificationSettingsOrDefault()
            repository.saveNotificationSettings(current.copy(enabled = enabled))
        }
    }

    fun setNotificationThresholds(largeSale: Double?, largeDiscount: Double?) {
        viewModelScope.launch {
            val current = repository.notificationSettingsOrDefault()
            repository.saveNotificationSettings(
                current.copy(
                    largeSaleThreshold = largeSale?.coerceAtLeast(0.0)
                        ?: current.largeSaleThreshold,
                    largeDiscountThreshold = largeDiscount?.coerceAtLeast(0.0)
                        ?: current.largeDiscountThreshold
                )
            )
        }
    }

    fun setQuietHours(enabled: Boolean, fromHour: Int? = null, toHour: Int? = null) {
        viewModelScope.launch {
            val current = repository.notificationSettingsOrDefault()
            repository.saveNotificationSettings(
                current.copy(
                    quietHoursEnabled = enabled,
                    quietFromHour = fromHour?.coerceIn(0, 23) ?: current.quietFromHour,
                    quietToHour = toHour?.coerceIn(0, 23) ?: current.quietToHour
                )
            )
        }
    }

    fun can(permission: Permission): Boolean = permissions.value.can(permission)

    /**
     * Runs [block] only if the signed-in user is allowed to; otherwise shows a
     * plain-language explanation. Returns whether it ran.
     */
    /**
     * The single enforcement point. Every function that changes shop data calls
     * this first. Hiding a button in the UI is a courtesy; this is the rule.
     * Denials are written to the activity log so the owner can see who tried.
     */
    private fun allow(permission: Permission): Boolean {
        if (can(permission)) return true
        val who = permissions.value
        showMessage(who.denialMessage(permission))
        viewModelScope.launch {
            repository.recordAudit(
                staffId = who.staffId,
                staffName = who.staffName.ifBlank { "Unknown" },
                action = "BLOCKED",
                description = "Tried to ${permission.label.lowercase()} without permission"
            )
            // Only worth telling the owner about genuinely sensitive attempts.
            if (permission.sensitive && !who.isSoloOwner) {
                repository.notify(
                    type = NotificationType.PERMISSION_BLOCKED,
                    title = "Blocked: ${permission.label}",
                    body = "${who.staffName.ifBlank { "Someone" }} tried to " +
                        "${permission.label.lowercase()} but is not allowed to.",
                    actorName = who.staffName
                )
            }
        }
        return false
    }

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
                repository.notify(
                    type = NotificationType.STAFF_SIGN_IN,
                    title = "${match.name} signed in",
                    body = "Signed in as ${match.role} at the counter",
                    actorName = match.name
                )
            }
            showMessage("Welcome back, ${match.name}")
            null
        }
    }

    fun signOut() {
        // The owner must always be able to get back into the shop. This is what
        // stops the solo-onboarding trap: a cashier could otherwise be the only
        // PIN holder, sign out, and lock the real owner out of the till.
        val ownerCanOpen = staffList.value.any {
            it.isActive && it.role.equals("Owner", ignoreCase = true) && it.pin.isNotBlank()
        }
        if (!ownerCanOpen) {
            showMessage("Set a PIN on your owner card before signing out.")
            return
        }
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
        if (discount > 0.0 && !allow(Permission.GIVE_DISCOUNT)) return
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
        // Clearing a discount is always allowed; adding one is not.
        if (discount > 0.0 && !allow(Permission.GIVE_DISCOUNT)) return
        _billDiscount.value = discount.coerceAtLeast(0.0)
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
        if (!allow(Permission.CREATE_SALE)) return
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return

        val subtotal = cartItems.sumOf { it.lineTotal }
        val finalTotal = (subtotal - _billDiscount.value).coerceAtLeast(0.0)
        val changeGiven = if (paymentMethod == "CASH") (cashReceived - finalTotal).coerceAtLeast(0.0) else 0.0

        // A bill of Rs. 20 can never be completed with Rs. 10. Enforced here as
        // well as in checkout, so a shortcut can't bypass the split rule.
        if (paymentMethod == "CASH" && cashReceived < finalTotal - 0.001) {
            showMessage("Cash received is less than the bill. Split the remaining amount on card or as credit.")
            return
        }
        if ((paymentMethod == "CREDIT" || (paymentMethod == "SPLIT" && creditAmount > 0.0)) &&
            _selectedCustomer.value == null
        ) {
            showMessage("Choose or create a customer before saving a credit sale.")
            return
        }

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

            // Tell the owner what just happened, if they asked to be told.
            announceSale(storedSale, storedItems, cashier)

            // Print straight away when the owner asked for it during setup.
            val prof = repository.getProfileSync()
            if (prof?.autoPrint == true && printerService.isConnected) {
                printBillReceipt(storedSale, storedItems)
            }
        }
    }

    /**
     * Raises the notifications a completed sale can trigger. Deliberately kept
     * out of the sale path itself: a failure to notify must never lose a sale,
     * so everything here is best effort.
     */
    private suspend fun announceSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        cashier: String
    ) {
        val settings = repository.notificationSettingsOrDefault()
        val money = CurrencyUtils.formatLkr(sale.totalAmount)

        // A big sale and every sale are separate switches. Only one fires, so
        // turning both on does not double up.
        if (sale.totalAmount >= settings.largeSaleThreshold) {
            repository.notify(
                type = NotificationType.LARGE_SALE,
                title = "Big sale: $money",
                body = "$cashier sold ${items.size} " +
                    (if (items.size == 1) "item" else "items") + " on ${sale.invoiceNumber}",
                amount = sale.totalAmount,
                actorName = cashier,
                reference = sale.invoiceNumber
            )
        } else {
            repository.notify(
                type = NotificationType.SALE_COMPLETED,
                title = "Sale $money",
                body = "$cashier completed ${sale.invoiceNumber}",
                amount = sale.totalAmount,
                actorName = cashier,
                reference = sale.invoiceNumber
            )
        }

        // Sold below or above the listed price.
        items.filter { it.productId != null }.forEach { line ->
            val product = repository.getProductById(line.productId ?: return@forEach)
            if (product != null && kotlin.math.abs(line.unitPrice - product.sellingPrice) > 0.01) {
                val cheaper = line.unitPrice < product.sellingPrice
                repository.notify(
                    type = NotificationType.PRICE_CHANGED,
                    title = if (cheaper) "Sold below the listed price" else "Sold above the listed price",
                    body = "$cashier sold ${line.productName} at " +
                        "${CurrencyUtils.formatLkr(line.unitPrice)} instead of " +
                        CurrencyUtils.formatLkr(product.sellingPrice),
                    amount = line.unitPrice - product.sellingPrice,
                    actorName = cashier,
                    reference = sale.invoiceNumber
                )
            }
        }

        if (sale.discountAmount >= settings.largeDiscountThreshold) {
            repository.notify(
                type = NotificationType.DISCOUNT_GIVEN,
                title = "Discount of ${CurrencyUtils.formatLkr(sale.discountAmount)}",
                body = "$cashier discounted ${sale.invoiceNumber}",
                amount = sale.discountAmount,
                actorName = cashier,
                reference = sale.invoiceNumber
            )
        }

        if (sale.paymentMethod == "CREDIT" && sale.customerId != null) {
            val customer = repository.getCustomerById(sale.customerId)
            repository.notify(
                type = NotificationType.CREDIT_GIVEN,
                title = "${sale.customerName} took goods on credit",
                body = "$money added to their account on ${sale.invoiceNumber}",
                amount = sale.totalAmount,
                actorName = cashier,
                reference = sale.invoiceNumber
            )
            // Over their agreed limit is a separate, louder warning.
            if (customer != null && customer.creditLimit > 0 &&
                customer.creditBalance > customer.creditLimit
            ) {
                repository.notify(
                    type = NotificationType.CREDIT_LIMIT,
                    title = "${customer.name} is over their credit limit",
                    body = "They owe ${CurrencyUtils.formatLkr(customer.creditBalance)} " +
                        "against a limit of ${CurrencyUtils.formatLkr(customer.creditLimit)}",
                    amount = customer.creditBalance,
                    actorName = cashier,
                    reference = sale.invoiceNumber
                )
            }
        }

        // Stock warnings, once per affected product.
        items.mapNotNull { it.productId }.distinct().forEach { productId ->
            val product = repository.getProductById(productId) ?: return@forEach
            if (!product.isTracked) return@forEach
            when {
                product.currentStock <= 0.0 -> repository.notify(
                    type = NotificationType.OUT_OF_STOCK,
                    title = "${product.name} is finished",
                    body = "Nothing left on the shelf. Customers cannot buy it.",
                    reference = product.name
                )
                product.currentStock <= product.lowStockThreshold -> repository.notify(
                    type = NotificationType.LOW_STOCK,
                    title = "${product.name} is running low",
                    body = "Only ${CurrencyUtils.trimQuantity(product.currentStock)} " +
                        "${product.unit} left. Time to order more.",
                    reference = product.name
                )
            }
        }
    }

    fun dismissSaleSuccess() {
        _showSaleSuccessDialog.value = false
    }

    // --- Hold / Park Sale ---
    fun holdCurrentSale(label: String) {
        if (!allow(Permission.CREATE_SALE)) return
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
        if (!allow(Permission.CREATE_SALE)) return
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
        isFavourite: Boolean,
        subCategory: String = "",
        variants: String = ""
    ) {
        if (!allow(Permission.MANAGE_PRODUCTS)) return
        viewModelScope.launch {
            val rawVariants = variants.trim()
            val combos = VariantCatalog.buildCombinations(rawVariants, sellingPrice)

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
                    val updated = existing.copy(
                        name = name,
                        sellingPrice = sellingPrice,
                        costPrice = costPrice,
                        barcode = barcode,
                        sku = sku,
                        category = category,
                        subCategory = subCategory,
                        variants = rawVariants,
                        unit = unit,
                        lowStockThreshold = lowStock,
                        isTracked = isTracked,
                        isFavourite = isFavourite,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateProduct(updated)
                    // Rewrite the child lines while keeping their stock history.
                    // Options removed from the parent are archived, not deleted.
                    reconcileVariantChildren(id, updated, combos)

                    // Changing the stock figure here is a recount, so it goes
                    // through the ledger like any other correction rather than
                    // overwriting the total behind its back.
                    if (openingStock >= 0 && openingStock != existing.currentStock) {
                        repository.recordStockAdjustment(
                            productId = id,
                            newCount = openingStock,
                            reason = "COUNT",
                            note = "Corrected while editing the item"
                        )
                    }
                    showMessage("Updated $name")
                }
            } else {
                val shopType = profile.value?.shopTypeKey.orEmpty()
                val parent = ProductEntity(
                    name = name,
                    sellingPrice = sellingPrice,
                    costPrice = costPrice,
                    barcode = barcode,
                    sku = sku,
                    category = category,
                    subCategory = subCategory,
                    variants = rawVariants,
                    // New products join the shop type the business is set up for,
                    // so they show up alongside the right catalogue.
                    shopType = shopType,
                    unit = unit,
                    currentStock = openingStock,
                    lowStockThreshold = lowStock,
                    isTracked = isTracked,
                    isFavourite = isFavourite
                )
                val parentId = repository.insertProductWithOpeningStock(parent)

                // Every simple option or nested combination becomes its own
                // purchasable line. Deep rules like "Rice: Basmati|Keeri" +
                // "Portion: Regular|Full" create the 4 lines automatically.
                if (combos.isNotEmpty()) {
                    combos.forEach { combo ->
                        val child = parent.copy(
                            id = 0L,
                            name = VariantCatalog.childName(name, combo),
                            sellingPrice = combo.price,
                            variants = "",
                            parentProductId = parentId,
                            isVariant = true,
                            // A variant starts at zero so it is stocked
                            // separately; the parent's opening stock is not
                            // silently shared across every size/option.
                            currentStock = 0.0,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.insertProductWithOpeningStock(child)
                    }
                    showMessage("Added $name and ${combos.size} option(s)")
                } else {
                    showMessage("Added $name")
                }
                audit("PRODUCT_ADDED", "Added product $name", sellingPrice)
            }
        }
    }

    /**
     * Keeps a parent's generated child rows in step with its current variant
     * definitions. New options become stockable lines, existing ones keep their
     * ledger, and removed ones are archived rather than deleted.
     */
    private suspend fun reconcileVariantChildren(
        parentId: Long,
        parent: ProductEntity,
        combos: List<com.example.data.model.VariantCombination>
    ) {
        val existingChildren = repository.getVariantChildren(parentId)
        val desiredNames = combos.map { VariantCatalog.childName(parent.name, it) }.toSet()

        combos.forEach { combo ->
            val name = VariantCatalog.childName(parent.name, combo)
            val existing = existingChildren.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                repository.updateProduct(
                    existing.copy(
                        name = name,
                        sellingPrice = combo.price,
                        category = parent.category,
                        subCategory = parent.subCategory,
                        shopType = parent.shopType,
                        unit = parent.unit,
                        lowStockThreshold = parent.lowStockThreshold,
                        isTracked = parent.isTracked,
                        variants = ""
                    )
                )
            } else {
                val child = parent.copy(
                    id = 0L,
                    name = name,
                    sellingPrice = combo.price,
                    variants = "",
                    parentProductId = parentId,
                    isVariant = true,
                    currentStock = 0.0,
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertProductWithOpeningStock(child)
            }
        }

        // Anything left over no longer belongs to the current options.
        existingChildren
            .filter { it.name.lowercase() !in desiredNames.map { d -> d.lowercase() } }
            .forEach { repository.archiveProduct(it.id) }
    }

    fun archiveProduct(productId: Long) {
        if (!allow(Permission.MANAGE_PRODUCTS)) return
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
        if (!allow(Permission.MANAGE_CUSTOMERS)) return
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
        if (!allow(Permission.MANAGE_CUSTOMERS)) return
        viewModelScope.launch {
            repository.recordManualCustomerCredit(customerId, amount, reason, note)
            showMessage("Credit debit of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    fun deleteCustomer(id: Long) {
        if (!allow(Permission.DELETE_RECORDS)) return
        viewModelScope.launch {
            repository.deleteCustomer(id)
            showMessage("Customer deleted")
        }
    }

    fun recordCustomerCreditPayment(customerId: Long, amount: Double, paymentMethod: String, note: String) {
        if (!allow(Permission.MANAGE_CUSTOMERS)) return
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
        if (!allow(Permission.MANAGE_SUPPLIERS)) return
        if (name.isBlank()) {
            showMessage("Enter a supplier name")
            return
        }
        viewModelScope.launch {
            runCatching {
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
                }
            }.onSuccess {
                showMessage(if (id > 0) "Updated supplier $name" else "Added supplier $name")
                audit("SUPPLIER", if (id > 0) "Updated supplier $name" else "Added supplier $name")
            }.onFailure {
                showMessage("Could not save the supplier. Please try again.")
            }
        }
    }

    fun deleteSupplier(supplierId: Long) {
        if (!allow(Permission.DELETE_RECORDS)) return
        viewModelScope.launch {
            repository.deleteSupplier(supplierId)
            showMessage("Supplier deleted")
        }
    }

    fun recordSupplierPayment(supplierId: Long, amount: Double, paymentMethod: String, note: String) {
        if (!allow(Permission.MANAGE_SUPPLIERS)) return
        viewModelScope.launch {
            repository.recordSupplierPayment(supplierId, amount, paymentMethod, note)
            showMessage("Supplier payment of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    fun settlePurchaseDue(purchaseId: Long, amount: Double, paymentMethod: String, note: String) {
        if (!allow(Permission.MANAGE_SUPPLIERS)) return
        viewModelScope.launch {
            repository.settlePurchaseDue(purchaseId, amount, paymentMethod, note)
            showMessage("PO Payment of ${CurrencyUtils.formatLkr(amount)} recorded")
        }
    }

    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity> {
        return repository.getPurchaseItems(purchaseId)
    }

    fun deletePurchase(purchaseId: Long) {
        if (!allow(Permission.DELETE_RECORDS)) return
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
        if (!allow(Permission.MANAGE_SUPPLIERS)) return
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
        if (!allow(Permission.MANAGE_SUPPLIERS)) return
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
            runCatching {
                repository.insertPurchase(purchase, emptyList())
            }.onSuccess {
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
            }.onFailure {
                showMessage("Could not save the delivery bill. Please try again.")
            }
        }
    }

    // --- Inventory direct actions ---
    fun receiveStockDirect(productId: Long, qty: Double, unitCost: Double, supplierName: String) {
        if (!allow(Permission.MANAGE_INVENTORY)) return
        viewModelScope.launch {
            repository.receiveStockDirect(productId, qty, unitCost, supplierName)
            showMessage("Received +$qty units")
        }
    }

    fun receiveBatchStockDirect(items: List<Triple<ProductEntity, Double, Double>>, supplierName: String) {
        if (!allow(Permission.MANAGE_INVENTORY)) return
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
        if (!allow(Permission.MANAGE_INVENTORY)) return
        viewModelScope.launch {
            repository.recordStockAdjustment(productId, newCount, reason, note)
            showMessage("Stock adjusted to $newCount")
        }
    }

    // --- Expenses ---
    fun addExpense(category: String, amount: Double, paymentMethod: String, reference: String, note: String) {
        if (!allow(Permission.MANAGE_EXPENSES)) return
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
        if (!allow(Permission.MANAGE_CASH)) return
        viewModelScope.launch {
            repository.openShift(counterName, staffId, staffName, openingCash)
            showMessage("Shift opened for $staffName at $counterName")
        }
    }

    fun closeShift(shiftId: Long, actualCash: Double, reason: String) {
        if (!allow(Permission.MANAGE_CASH)) return
        viewModelScope.launch {
            // Read the expected figure before closing, so the summary can
            // report the difference the owner actually cares about.
            val shift = repository.getCurrentShiftSync()
            val expected = shift?.expectedCash ?: 0.0
            repository.closeShift(shiftId, actualCash, reason)

            val difference = actualCash - expected
            val who = permissions.value.staffName.ifBlank { "Someone" }
            repository.notify(
                type = NotificationType.DAY_CLOSED,
                title = "Day closed with ${CurrencyUtils.formatLkr(actualCash)} in the drawer",
                body = "$who counted the cash. Expected ${CurrencyUtils.formatLkr(expected)}.",
                amount = actualCash,
                actorName = who
            )
            // A mismatch of more than a rupee is worth a separate, louder alert.
            if (kotlin.math.abs(difference) >= 1.0) {
                val short = difference < 0
                repository.notify(
                    type = NotificationType.CASH_SHORTAGE,
                    title = if (short) {
                        "Cash short by ${CurrencyUtils.formatLkr(-difference)}"
                    } else {
                        "Cash over by ${CurrencyUtils.formatLkr(difference)}"
                    },
                    body = "Counted ${CurrencyUtils.formatLkr(actualCash)} against " +
                        "${CurrencyUtils.formatLkr(expected)} expected. " +
                        reason.ifBlank { "No reason given." },
                    amount = difference,
                    actorName = who
                )
            }
            showMessage("Day closed")
        }
    }

    fun recordCashMovement(shiftId: Long, type: String, amount: Double, reason: String, note: String) {
        if (!allow(Permission.MANAGE_CASH)) return
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
            repository.notify(
                type = NotificationType.REFUND_ISSUED,
                title = "Refund of ${CurrencyUtils.formatLkr(refundAmount)}",
                body = "${permissions.value.staffName.ifBlank { "Someone" }} refunded a bill. Reason: " +
                    reason.ifBlank { "not given" },
                amount = refundAmount,
                actorName = permissions.value.staffName,
                reference = saleId.toString()
            )
            showMessage("Money returned: ${CurrencyUtils.formatLkr(refundAmount)}. Stock put back.")
        }
    }

    // --- Staff & roles ---
    fun switchActiveStaff(staff: StaffEntity) {
        if (!allow(Permission.MANAGE_STAFF)) return
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

    /**
     * Adds or updates a team member. [extra] and [revoked] are the per-person
     * tweaks on top of the role; pass null to leave them untouched.
     */
    fun saveStaff(
        id: Long,
        name: String,
        phone: String,
        role: String,
        pin: String,
        isActive: Boolean,
        extra: Set<Permission>? = null,
        revoked: Set<Permission>? = null
    ) {
        if (!allow(Permission.MANAGE_STAFF)) return
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            showMessage("Please enter a name")
            return
        }
        if (pin.isNotBlank() && pin.length != 4) {
            showMessage("A PIN must be exactly 4 numbers")
            return
        }
        viewModelScope.launch {
            // Two people with the same PIN would make sign-in ambiguous.
            val clash = staffList.value.firstOrNull {
                it.id != id && it.pin.isNotBlank() && it.pin == pin
            }
            if (pin.isNotBlank() && clash != null) {
                showMessage("${clash.name} already uses that PIN. Please pick another.")
                return@launch
            }

            val encoded = if (extra != null && revoked != null) {
                PermissionOverrides.encode(extra, revoked)
            } else null

            if (id > 0) {
                val existing = staffList.value.firstOrNull { it.id == id } ?: return@launch
                // Never let the last owner be demoted or the shop locks itself out.
                if (existing.role.equals("Owner", true) && !role.equals("Owner", true)) {
                    val owners = staffList.value.count { it.isActive && it.role.equals("Owner", true) }
                    if (owners <= 1) {
                        showMessage("This is your only owner. Make someone else an owner first.")
                        return@launch
                    }
                }
                repository.updateStaff(
                    existing.copy(
                        name = cleanName,
                        phone = phone.trim(),
                        role = role,
                        pin = pin.ifBlank { existing.pin },
                        isActive = isActive,
                        extraPermissions = encoded?.first ?: existing.extraPermissions,
                        revokedPermissions = encoded?.second ?: existing.revokedPermissions
                    )
                )
                audit("STAFF_UPDATED", "Updated $cleanName ($role)")
                showMessage("$cleanName updated")
            } else {
                repository.insertStaff(
                    StaffEntity(
                        name = cleanName,
                        phone = phone.trim(),
                        role = role,
                        pin = pin,
                        isActive = isActive,
                        extraPermissions = encoded?.first.orEmpty(),
                        revokedPermissions = encoded?.second.orEmpty()
                    )
                )
                audit("STAFF_ADDED", "Added $cleanName as $role")
                showMessage("$cleanName added to your team")
            }
        }
    }

    /** Resolved permissions for a given team member, for the "what can they do" editor. */
    fun permissionsFor(staff: StaffEntity): PermissionSet = PermissionSet.resolve(
        role = StaffRole.fromName(staff.role),
        staffId = staff.id,
        staffName = staff.name,
        extraCsv = staff.extraPermissions,
        revokedCsv = staff.revokedPermissions
    )

    /**
     * Turn one capability on or off for one person, relative to their role.
     * Matching the role default clears the override rather than storing a
     * redundant entry, so changing someone's role later behaves predictably.
     */
    fun setStaffPermission(staff: StaffEntity, permission: Permission, allowed: Boolean) {
        if (!allow(Permission.MANAGE_STAFF)) return
        val role = StaffRole.fromName(staff.role)
        if (role == StaffRole.OWNER) {
            showMessage("Owners always have full access.")
            return
        }
        val extra = PermissionOverrides.decode(staff.extraPermissions).toMutableSet()
        val revoked = PermissionOverrides.decode(staff.revokedPermissions).toMutableSet()
        val roleHasIt = role.permissions.contains(permission)

        extra.remove(permission)
        revoked.remove(permission)
        when {
            allowed && !roleHasIt -> extra.add(permission)
            !allowed && roleHasIt -> revoked.add(permission)
        }

        viewModelScope.launch {
            val (extraCsv, revokedCsv) = PermissionOverrides.encode(extra, revoked)
            repository.updateStaff(
                staff.copy(extraPermissions = extraCsv, revokedPermissions = revokedCsv)
            )
            val verb = if (allowed) "Allowed" else "Blocked"
            audit("PERMISSION", verb + " " + permission.label + " for " + staff.name)
        }
    }

    /** Drop all per-person tweaks and go back to the plain role. */
    fun resetStaffPermissions(staff: StaffEntity) {
        if (!allow(Permission.MANAGE_STAFF)) return
        viewModelScope.launch {
            repository.updateStaff(staff.copy(extraPermissions = "", revokedPermissions = ""))
            audit("PERMISSION", "Reset ${staff.name} to the standard ${staff.role} access")
            showMessage("${staff.name} is back to standard ${staff.role} access")
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
                paperWidth = p.printerPaperWidth,
                timestamp = sale.timestamp,
                headerName = p.receiptHeaderName,
                headerNote = p.receiptHeaderNote,
                showAddress = p.receiptShowAddress,
                showPhone = p.receiptShowPhone,
                showDateTime = p.receiptShowDateTime,
                showCashier = p.receiptShowCashier,
                showItemCount = p.receiptShowItemCount,
                returnNote = p.receiptReturnNote
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
        if (!allow(Permission.MANAGE_PRODUCTS)) return
        viewModelScope.launch {
            val preset = ProductCatalogPresets.findShopType(shopTypeKey) ?: return@launch
            repository.installShopTypeCatalog(preset.key, preset.products)
            audit("CATALOG", "Loaded starter items for ${preset.displayName}")
            showMessage("${preset.products.size} ${preset.displayName} items are ready")
        }
    }

    /** Removes starter items without touching anything the owner added. */
    fun clearStarterCatalog() {
        if (!allow(Permission.MANAGE_PRODUCTS)) return
        viewModelScope.launch {
            repository.clearAllProducts()
            audit("CATALOG", "Cleared the product list")
            showMessage("Product list cleared")
        }
    }

    /** Switching shop type wipes the old catalogue so nothing overlaps. */
    fun changeShopType(shopTypeKey: String, loadStarterItems: Boolean) {
        if (!allow(Permission.MANAGE_SETTINGS)) return
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
        if (!allow(Permission.DELETE_RECORDS)) return
        viewModelScope.launch {
            repository.deleteProduct(productId)
            showMessage("Product deleted from catalog")
        }
    }

    fun deleteExpense(expenseId: Long) {
        if (!allow(Permission.DELETE_RECORDS)) return
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            showMessage("Expense entry deleted")
        }
    }

    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity> {
        return repository.getSaleItems(saleId)
    }

    fun saveBusinessProfile(updated: BusinessProfileEntity) {
        if (!allow(Permission.MANAGE_SETTINGS)) return
        viewModelScope.launch {
            val current = repository.getProfileSync() ?: BusinessProfileEntity()
            var toSave = updated

            // Turning on team mode from Settings must never leave the owner
            // behind as a Cashier. If the shop was "Just me" and has no Owner
            // row yet, create one and put that person at the till.
            if (updated.staffEnabled && !current.staffEnabled) {
                val ownerName = updated.activeStaffName
                    .ifBlank { current.activeStaffName }
                    .ifBlank { current.name }
                    .ifBlank { "Owner" }
                val existingOwner = staffList.value.firstOrNull {
                    it.role.equals("Owner", ignoreCase = true) &&
                        it.name.equals(ownerName, ignoreCase = true)
                } ?: staffList.value.firstOrNull { it.role.equals("Owner", ignoreCase = true) }
                val ownerId = existingOwner?.id ?: repository.insertStaff(
                    StaffEntity(
                        name = ownerName,
                        role = "Owner",
                        isActive = true
                    )
                )
                toSave = updated.copy(
                    staffEnabled = true,
                    activeStaffId = ownerId,
                    activeStaffName = ownerName,
                    activeStaffRole = "Owner"
                )
                _signedInStaffId.value = ownerId
            }

            repository.saveProfile(toSave)
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

    /**
     * Turns a solo shop into a team shop without losing the owner. This is the
     * path behind More → My team → “I have staff now” when the owner chose
     * “Just me” during setup. It creates an Owner row (if there isn't one yet),
     * keeps the business owner at the till, and never silently drops them into
     * a Cashier role.
     */
    fun enableTeamForOwner() {
        viewModelScope.launch {
            val current = repository.getProfileSync() ?: BusinessProfileEntity()
            val alreadyHadOwner = current.staffEnabled && staffList.value.any { it.role.equals("Owner", ignoreCase = true) }
            if (alreadyHadOwner) {
                showMessage("Team mode is already on")
                return@launch
            }
            val ownerName = current.activeStaffName.ifBlank { current.name }.ifBlank { "Owner" }
            val existingOwner = staffList.value.firstOrNull {
                it.role.equals("Owner", ignoreCase = true) && it.name.equals(ownerName, ignoreCase = true)
            } ?: staffList.value.firstOrNull { it.role.equals("Owner", ignoreCase = true) }

            val ownerId = existingOwner?.id ?: repository.insertStaff(
                StaffEntity(
                    name = ownerName,
                    role = "Owner",
                    isActive = true
                )
            )
            val saved = current.copy(
                staffEnabled = true,
                activeStaffId = ownerId,
                activeStaffName = ownerName,
                activeStaffRole = "Owner"
            )
            repository.saveProfile(saved)
            _signedInStaffId.value = ownerId
            audit("SETTINGS", "Team mode switched on for $ownerName")
            showMessage("Team mode is on. You are still the owner. Set a PIN on your card before signing out.")
        }
    }

    /** Wipes every sale, customer, supplier, product, movement and team row. */
    fun clearAllBusinessData() {
        if (!allow(Permission.MANAGE_SETTINGS)) return
        viewModelScope.launch {
            repository.clearAllBusinessData()
            repository.saveProfile(BusinessProfileEntity())
            _signedInStaffId.value = null
            _selectedCustomer.value = null
            _billDiscount.value = 0.0
            _billNote.value = ""
            _lastCompletedSale.value = null
            _lastCompletedItems.value = emptyList()
            _showSaleSuccessDialog.value = false
            clearCart()
            _selectedTab.value = PosTab.SELL
            _moreDestination.value = null
            _onboardingStep.value = 1
            showMessage("All data removed. Let's set the shop up again.")
        }
    }

    /** Returns a spreadsheet-ready CSV of the current catalogue. */
    suspend fun exportProductsCsv(): String = repository.exportProductsCsv()

    /** Imports a CSV exported from Settings into the current catalogue. */
    fun importProductsFromCsv(raw: String) {
        if (!allow(Permission.MANAGE_PRODUCTS)) return
        viewModelScope.launch {
            val (saved, problems) = repository.importProductsFromCsv(
                raw = raw,
                shopType = profile.value?.shopTypeKey.orEmpty()
            )
            if (problems.isEmpty()) {
                showMessage("Imported $saved products")
            } else {
                showMessage("Imported $saved products. ${problems.size} row(s) skipped: ${problems.first().take(160)}")
            }
            audit("CATALOG", "Imported $saved products from CSV")
        }
    }

    /** Looks up a scanned or typed barcode inside the active shop's catalogue. */
    fun addProductByBarcode(
        barcode: String,
        onFound: (ProductEntity) -> Unit,
        onMissing: (String) -> Unit
    ) {
        viewModelScope.launch {
            val shopType = profile.value?.shopTypeKey.orEmpty()
            val match = repository.getProductByBarcode(barcode.trim(), shopType)
            if (match != null) {
                onFound(match)
            } else {
                onMissing(barcode.trim())
            }
        }
    }

    // -----------------------------------------------------------------------
    // Cloud backup / Google Drive (per-device, provider-controlled)
    // -----------------------------------------------------------------------

    /** Re-reads the provider policy and keeps the hourly worker in step. */
    fun refreshCloud() {
        val settings = cloudRepo.load()
        _cloudSettings.value = settings
        if (settings.providerEnabled && settings.hourlySyncEnabled) {
            CloudSyncScheduler.schedule(application)
        } else if (!settings.providerEnabled) {
            CloudSyncScheduler.cancel(application)
        }
    }

    /** Makes a rolling local backup copy of the whole shop database. */
    fun backupNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceName = profile.value?.name.orEmpty().ifBlank { "counter" }
            val result = cloudBackup.createBackup(deviceName)
            val updated = cloudRepo.update {
                it.copy(
                    lastBackupAt = System.currentTimeMillis(),
                    lastBackupFile = result.file?.name.orEmpty(),
                    lastError = if (result.file == null) result.message else ""
                )
            }
            _cloudSettings.value = updated
            withContext(Dispatchers.Main) {
                showMessage(if (result.file != null) "Backup saved on this device" else "Backup could not be created")
            }
        }
    }

    /** Queues a one-off per-device backup/upload to Google Drive. */
    fun syncNow() {
        val settings = _cloudSettings.value
        if (!settings.providerEnabled) {
            showMessage("Ask your POS provider to activate cloud backup first")
            return
        }
        if (settings.ownerGmail.isBlank()) {
            showMessage("Connect a Google account before syncing")
            return
        }
        CloudSyncScheduler.syncNow(application)
        showMessage("Sync queued. It runs as soon as this phone has data.")
    }

    /** Owner-visible action: choose the Gmail that receives this device's backups. */
    fun setOwnerGmail(email: String) {
        val clean = email.trim()
        if (clean.isBlank()) return
        val updated = cloudRepo.update {
            it.copy(
                ownerGmail = clean,
                accountConnected = true,
                lastError = ""
            )
        }
        _cloudSettings.value = updated
        if (updated.providerEnabled && updated.hourlySyncEnabled) {
            CloudSyncScheduler.schedule(application)
        }
        showMessage("Google account connected for backup")
        audit("SETTINGS", "Connected Google account ${clean.take(6)}… for backup", 0.0)
    }

    /** Providers only. Writes the master policy/access code. */
    fun saveProviderCloud(
        enabled: Boolean,
        providerEmail: String,
        hourlySync: Boolean,
        dailyBackup: Boolean,
        accessCode: String
    ) {
        val updated = cloudRepo.update {
            it.copy(
                providerEnabled = enabled,
                providerEmail = providerEmail.trim(),
                hourlySyncEnabled = hourlySync,
                dailyBackupEnabled = dailyBackup
            )
        }
        if (accessCode.isNotBlank()) {
            cloudRepo.setProviderCode(accessCode)
        }
        _cloudSettings.value = cloudRepo.load()
        if (enabled && hourlySync) {
            CloudSyncScheduler.schedule(application)
        } else {
            CloudSyncScheduler.cancel(application)
        }
        showMessage(if (enabled) "Cloud backup activated for this device" else "Cloud backup deactivated")
        audit("SETTINGS", "Provider changed cloud backup settings", 0.0)
    }

    /** Unlocks the provider screen with the company access code + Gmail. */
    fun unlockProvider(email: String, code: String): Boolean {
        val ok = cloudRepo.verifyProviderCode(code, email)
        if (ok) isProviderUnlocked = true
        return ok
    }

    /** Google accounts on this phone, so the owner does not need to type an address. */
    fun googleAccounts(): List<String> = cloudRepo.googleAccounts()
}
