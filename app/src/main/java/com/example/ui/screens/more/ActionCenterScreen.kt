package com.example.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CustomerEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SupplierEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.MoreDestination
import com.example.ui.viewmodel.PosTab
import com.example.ui.viewmodel.PosViewModel

enum class AlertPriority {
    CRITICAL,
    ATTENTION,
    REMINDER
}

data class ActionItem(
    val id: String,
    val title: String,
    val description: String,
    val priority: AlertPriority,
    val category: String,
    val actionLabel: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val targetProduct: ProductEntity? = null,
    val targetCustomer: CustomerEntity? = null,
    val targetSupplier: SupplierEntity? = null,
    val destination: MoreDestination? = null,
    val tab: PosTab? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCenterScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit,
    onNavigateToDestination: ((MoreDestination) -> Unit)? = null
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val profile by viewModel.profile.collectAsState()

    var selectedPriorityFilter by remember { mutableStateOf<AlertPriority?>(null) }
    var resolvedActionIds by remember { mutableStateOf(setOf<String>()) }

    // Dialog state for fast restock
    var restockProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var restockQtyText by remember { mutableStateOf("24") }
    var restockSupplier by remember { mutableStateOf("Main Supplier") }

    // Compute dynamic action alerts from live database state
    val actionItems = remember(products, customers, suppliers, expenses, profile, resolvedActionIds) {
        val list = mutableListOf<ActionItem>()

        // 1. Out of Stock items (CRITICAL)
        val outOfStock = products.filter { it.currentStock <= 0 && it.isTracked }
        outOfStock.forEach { prod ->
            val id = "out_stock_${prod.id}"
            if (!resolvedActionIds.contains(id)) {
                list.add(
                    ActionItem(
                        id = id,
                        title = "${prod.name} is Out of Stock",
                        description = "0 units remaining in shop. Customers cannot buy this item.",
                        priority = AlertPriority.CRITICAL,
                        category = "Inventory",
                        actionLabel = "Restock Now",
                        icon = Icons.Default.ProductionQuantityLimits,
                        targetProduct = prod,
                        tab = PosTab.INVENTORY
                    )
                )
            }
        }

        // 2. Low Stock items (ATTENTION)
        val lowStock = products.filter { it.currentStock > 0 && it.currentStock <= it.lowStockThreshold && it.isTracked }
        lowStock.forEach { prod ->
            val id = "low_stock_${prod.id}"
            if (!resolvedActionIds.contains(id)) {
                list.add(
                    ActionItem(
                        id = id,
                        title = "${prod.name} is Running Low",
                        description = "Only ${prod.currentStock.toInt()} units left (Threshold: ${prod.lowStockThreshold.toInt()}). Restock suggested.",
                        priority = AlertPriority.ATTENTION,
                        category = "Inventory",
                        actionLabel = "Restock",
                        icon = Icons.Default.WarningAmber,
                        targetProduct = prod,
                        tab = PosTab.INVENTORY
                    )
                )
            }
        }

        // 3. Customer Overdue / Outstanding Credit (ATTENTION)
        val customersWithDue = customers.filter { it.creditBalance > 0 }
        if (customersWithDue.isNotEmpty()) {
            val totalDue = customersWithDue.sumOf { it.creditBalance }
            val id = "total_credit_due"
            if (!resolvedActionIds.contains(id)) {
                list.add(
                    ActionItem(
                        id = id,
                        title = "${CurrencyUtils.formatLkr(totalDue)} Customer Credit Due",
                        description = "${customersWithDue.size} customers have active credit tabs in your Khata book.",
                        priority = AlertPriority.ATTENTION,
                        category = "Finance",
                        actionLabel = "View Khata",
                        icon = Icons.Default.Book,
                        destination = MoreDestination.CREDIT_BOOK
                    )
                )
            }

            // High credit alert for top debtor
            val topDebtor = customersWithDue.maxByOrNull { it.creditBalance }
            if (topDebtor != null && topDebtor.creditBalance >= 3000.0) {
                val debtorId = "customer_due_${topDebtor.id}"
                if (!resolvedActionIds.contains(debtorId)) {
                    list.add(
                        ActionItem(
                            id = debtorId,
                            title = "${topDebtor.name} owes ${CurrencyUtils.formatLkr(topDebtor.creditBalance)}",
                            description = "Phone: ${topDebtor.phone.ifEmpty { "N/A" }}. Tap to send WhatsApp reminder.",
                            priority = AlertPriority.ATTENTION,
                            category = "Customers",
                            actionLabel = "WhatsApp Remind",
                            icon = Icons.Default.Person,
                            targetCustomer = topDebtor,
                            destination = MoreDestination.CREDIT_BOOK
                        )
                    )
                }
            }
        }

        // 4. Supplier Due Payables (REMINDER / ATTENTION)
        val suppliersDue = suppliers.filter { it.outstandingBalance > 0 }
        suppliersDue.forEach { sup ->
            val id = "supplier_due_${sup.id}"
            if (!resolvedActionIds.contains(id)) {
                list.add(
                    ActionItem(
                        id = id,
                        title = "${sup.name} payable ${CurrencyUtils.formatLkr(sup.outstandingBalance)}",
                        description = "Supplier balance pending settlement.",
                        priority = AlertPriority.REMINDER,
                        category = "Suppliers",
                        actionLabel = "Pay Supplier",
                        icon = Icons.Default.LocalShipping,
                        targetSupplier = sup,
                        destination = MoreDestination.SUPPLIERS
                    )
                )
            }
        }

        // 5. Hardware / Printer reminder
        if (profile?.autoPrint != true) {
            val id = "printer_autoprint"
            if (!resolvedActionIds.contains(id)) {
                list.add(
                    ActionItem(
                        id = id,
                        title = "Enable Quick Auto-Print",
                        description = "Connect Bluetooth thermal printer for instant 1-tap receipts on sale completion.",
                        priority = AlertPriority.REMINDER,
                        category = "Hardware",
                        actionLabel = "Configure Printer",
                        icon = Icons.Default.Print,
                        destination = MoreDestination.SETTINGS
                    )
                )
            }
        }

        list
    }

    val filteredItems = remember(actionItems, selectedPriorityFilter) {
        if (selectedPriorityFilter == null) actionItems
        else actionItems.filter { it.priority == selectedPriorityFilter }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Action Center", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                            if (actionItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = StatusRed,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        "${actionItems.size}",
                                        color = BrandOnGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            if (actionItems.isEmpty()) "All clear • Everything is running normally"
                            else "${actionItems.size} items require attention",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (actionItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                resolvedActionIds = resolvedActionIds + actionItems.map { it.id }
                            }
                        ) {
                            Text("Clear All", color = BrandGoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = LightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Priority Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPriorityFilter == null,
                        onClick = { selectedPriorityFilter = null },
                        label = { Text("All (${actionItems.size})", fontSize = 12.sp) }
                    )
                    val critCount = actionItems.count { it.priority == AlertPriority.CRITICAL }
                    if (critCount > 0) {
                        FilterChip(
                            selected = selectedPriorityFilter == AlertPriority.CRITICAL,
                            onClick = { selectedPriorityFilter = AlertPriority.CRITICAL },
                            label = { Text("Critical ($critCount)", fontSize = 12.sp, color = StatusRed) }
                        )
                    }
                    val attnCount = actionItems.count { it.priority == AlertPriority.ATTENTION }
                    if (attnCount > 0) {
                        FilterChip(
                            selected = selectedPriorityFilter == AlertPriority.ATTENTION,
                            onClick = { selectedPriorityFilter = AlertPriority.ATTENTION },
                            label = { Text("Attention ($attnCount)", fontSize = 12.sp, color = StatusAmber) }
                        )
                    }
                    val remCount = actionItems.count { it.priority == AlertPriority.REMINDER }
                    if (remCount > 0) {
                        FilterChip(
                            selected = selectedPriorityFilter == AlertPriority.REMINDER,
                            onClick = { selectedPriorityFilter = AlertPriority.REMINDER },
                            label = { Text("Reminders ($remCount)", fontSize = 12.sp, color = BrandGoldPrimary) }
                        )
                    }
                }
            }

            // All clear banner if empty
            if (filteredItems.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(BrandGoldSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BrandGoldPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("All Clear!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Good news — all inventory levels are healthy, receipts are configured, and customer balances are up to date.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    ActionItemCard(
                        item = item,
                        onAction = {
                            if (item.targetProduct != null) {
                                restockProduct = item.targetProduct
                                restockQtyText = "24"
                            } else if (item.targetCustomer != null) {
                                // Launch WhatsApp reminder
                                val cust = item.targetCustomer
                                val msg = "Hi ${cust.name}, your outstanding balance at ${profile?.name.orEmpty()} is ${CurrencyUtils.formatLkr(cust.creditBalance)}. Thank you!"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?phone=${cust.phone}&text=${Uri.encode(msg)}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // WhatsApp not installed, fallback
                                }
                            } else if (item.destination != null) {
                                onNavigateToDestination?.invoke(item.destination)
                            } else if (item.tab != null) {
                                viewModel.selectTab(item.tab)
                                onBack()
                            }
                        },
                        onDismiss = {
                            resolvedActionIds = resolvedActionIds + item.id
                        }
                    )
                }
            }

            // System Status Footnote
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Offline Billing Active", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Text("Room DB • Local Durable Storage", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }

    // Fast Restock Dialog
    if (restockProduct != null) {
        val prod = restockProduct!!
        Dialog(onDismissRequest = { restockProduct = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FAST RESTOCK", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        IconButton(onClick = { restockProduct = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Text(prod.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandGoldPrimary)
                    Text("Current stock: ${prod.currentStock.toInt()} • Cost: ${CurrencyUtils.formatLkr(prod.costPrice)}", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = restockQtyText,
                        onValueChange = { restockQtyText = it },
                        label = { Text("Quantity to add (${prod.unit})") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = restockSupplier,
                        onValueChange = { restockSupplier = it },
                        label = { Text("Supplier / Vendor") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val qty = restockQtyText.toDoubleOrNull() ?: 0.0
                            if (qty > 0) {
                                viewModel.receiveStockDirect(
                                    productId = prod.id,
                                    qty = qty,
                                    unitCost = prod.costPrice,
                                    supplierName = restockSupplier.ifBlank { "Direct Restock" }
                                )
                                resolvedActionIds = resolvedActionIds + "out_stock_${prod.id}" + "low_stock_${prod.id}"
                                restockProduct = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("RECEIVE & RESTOCK STOCK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionItemCard(
    item: ActionItem,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val borderColor = when (item.priority) {
        AlertPriority.CRITICAL -> StatusRed.copy(alpha = 0.3f)
        AlertPriority.ATTENTION -> StatusAmber.copy(alpha = 0.3f)
        AlertPriority.REMINDER -> BrandGoldPrimary.copy(alpha = 0.3f)
    }

    val iconTint = when (item.priority) {
        AlertPriority.CRITICAL -> StatusRed
        AlertPriority.ATTENTION -> StatusAmber
        AlertPriority.REMINDER -> BrandGoldPrimary
    }

    val badgeColor = when (item.priority) {
        AlertPriority.CRITICAL -> StatusRed
        AlertPriority.ATTENTION -> StatusAmber
        AlertPriority.REMINDER -> BrandGoldPrimary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            item.priority.name,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(item.category, fontSize = 11.sp, color = TextSecondary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.description, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (item.priority) {
                        AlertPriority.CRITICAL -> StatusRed
                        AlertPriority.ATTENTION -> BrandGoldPrimary
                        AlertPriority.REMINDER -> BrandGoldPrimary
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(item.actionLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
