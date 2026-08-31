package com.example.ui.screens.more

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.CashRegisterShiftEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.StaffEntity
import com.example.data.model.SupplierEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.data.model.Permission
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import com.example.ui.viewmodel.MoreDestination
import com.example.ui.viewmodel.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreManagementHubScreen(
    viewModel: PosViewModel,
    destination: MoreDestination?,
    onSelectDestination: (MoreDestination) -> Unit,
    onBackToHub: () -> Unit
) {
    if (destination == null) {
        HubMenuScreen(
            viewModel = viewModel,
            onSelectDestination = onSelectDestination
        )
    } else {
        when (destination) {
            MoreDestination.DASHBOARD, MoreDestination.REPORTS -> ReportsScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.CUSTOMERS, MoreDestination.CREDIT_BOOK -> com.example.ui.screens.customers.CustomersCreditScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.SUPPLIERS -> com.example.ui.screens.suppliers.SuppliersPurchasesScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.PURCHASES -> com.example.ui.screens.suppliers.SuppliersPurchasesScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.EXPENSES -> com.example.ui.screens.expenses.ExpensesScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.STAFF -> StaffScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.REGISTER -> CashRegisterScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.NOTIFICATIONS -> ActionCenterScreen(
                viewModel = viewModel,
                onBack = onBackToHub,
                onNavigateToDestination = { dest -> onSelectDestination(dest) }
            )
            MoreDestination.SETTINGS -> SettingsConfigurationScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.PRINTER -> com.example.ui.screens.printer.PrinterSetupScreen(
                viewModel = viewModel,
                profile = viewModel.profile.collectAsState().value,
                onBack = onBackToHub
            )
            MoreDestination.ACTIVITY_LOG -> ActivityLogScreen(viewModel = viewModel, onBack = onBackToHub)
        }
    }
}

// -------------------------------------------------------------------------------------
// Hub Main Navigation
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubMenuScreen(
    viewModel: PosViewModel,
    onSelectDestination: (MoreDestination) -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val lowStock by viewModel.lowStockProducts.collectAsState()
    val printerConnected by viewModel.isPrinterConnected.collectAsState()

    val tracksStock = profile?.trackStock == true
    val creditEnabled = profile?.creditEnabled == true
    val customersOwing = customers.count { it.creditBalance > 0 }
    val alertCount = lowStock.size + customersOwing
    val printerSubtitle = when {
        printerConnected -> "Connected to ${profile?.printerName.orEmpty().ifBlank { "your printer" }}"
        profile?.printerName.orEmpty().isNotBlank() -> "Saved, but not connected right now"
        else -> "Connect a Bluetooth or Wi-Fi printer"
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("More", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onSelectDestination(MoreDestination.NOTIFICATIONS) }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Action Center", tint = BrandTealPrimary)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HubActionCard(
                    title = "Things needing attention",
                    subtitle = "Low stock, money owed to you, bills to pay",
                    icon = Icons.Default.NotificationsActive,
                    badge = if (alertCount > 0) "$alertCount" else null,
                    onClick = { onSelectDestination(MoreDestination.NOTIFICATIONS) }
                )
            }

            item { SectionHeading("Money") }

            if (permissions.can(Permission.VIEW_REPORTS)) {
                item {
                    HubActionCard(
                        title = "Reports",
                        subtitle = "What you sold and what you earned",
                        icon = Icons.Default.BarChart,
                        onClick = { onSelectDestination(MoreDestination.REPORTS) }
                    )
                }
            }

            if (creditEnabled && permissions.can(Permission.MANAGE_CUSTOMERS)) {
                item {
                    HubActionCard(
                        title = "Credit book",
                        subtitle = if (customersOwing > 0) {
                            "$customersOwing ${if (customersOwing == 1) "customer owes" else "customers owe"} you money"
                        } else {
                            "Nobody owes you right now"
                        },
                        icon = Icons.Default.Book,
                        onClick = { onSelectDestination(MoreDestination.CREDIT_BOOK) }
                    )
                }
            }

            if (permissions.can(Permission.MANAGE_CASH)) {
                item {
                    HubActionCard(
                        title = "Cash drawer",
                        subtitle = "Open the day, count cash, close the day",
                        icon = Icons.Default.PointOfSale,
                        onClick = { onSelectDestination(MoreDestination.REGISTER) }
                    )
                }
            }

            if (permissions.can(Permission.MANAGE_EXPENSES)) {
                item {
                    HubActionCard(
                        title = "Expenses",
                        subtitle = "Rent, electricity, transport and other costs",
                        icon = Icons.Default.Receipt,
                        onClick = { onSelectDestination(MoreDestination.EXPENSES) }
                    )
                }
            }

            if (tracksStock && permissions.can(Permission.MANAGE_SUPPLIERS)) {
                item { SectionHeading("Stock") }
                item {
                    HubActionCard(
                        title = "Suppliers and purchases",
                        subtitle = "Who you buy from and what you still owe them",
                        icon = Icons.Default.LocalShipping,
                        onClick = { onSelectDestination(MoreDestination.SUPPLIERS) }
                    )
                }
            }

            item { SectionHeading("Shop setup") }

            item {
                HubActionCard(
                    title = "Printer",
                    subtitle = printerSubtitle,
                    icon = Icons.Default.Print,
                    onClick = { onSelectDestination(MoreDestination.PRINTER) }
                )
            }

            if (permissions.can(Permission.MANAGE_STAFF)) {
                item {
                    HubActionCard(
                        title = "My team",
                        subtitle = "Add staff and choose what each person can do",
                        icon = Icons.Default.Badge,
                        onClick = { onSelectDestination(MoreDestination.STAFF) }
                    )
                }
            }

            if (permissions.can(Permission.MANAGE_SETTINGS)) {
                item {
                    HubActionCard(
                        title = "Shop details and receipt",
                        subtitle = "Name, address, phone, what prints on the bill",
                        icon = Icons.Default.Settings,
                        onClick = { onSelectDestination(MoreDestination.SETTINGS) }
                    )
                }
            }

            if (permissions.can(Permission.VIEW_AUDIT)) {
                item {
                    HubActionCard(
                        title = "Activity log",
                        subtitle = "Every refund, price change and sign-in",
                        icon = Icons.Default.History,
                        onClick = { onSelectDestination(MoreDestination.ACTIVITY_LOG) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { viewModel.signOut() }) {
                        Text("Sign out", color = StatusRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Column {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
    }
}

@Composable
fun HubActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BrandMintSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = BrandTealPrimary) {
                                Text(badge, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

// -------------------------------------------------------------------------------------
// Reports Screen
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val screenPermissions by viewModel.permissions.collectAsState()
    if (screenPermissions.cannot(Permission.VIEW_REPORTS)) {
        com.example.ui.components.LockedScreenNotice(
            message = screenPermissions.denialMessage(Permission.VIEW_REPORTS),
            onBack = onBack
        )
        return
    }

    val sales by viewModel.sales.collectAsState()
    val expenses by viewModel.expenses.collectAsState()

    val totalTurnover = remember(sales) { sales.sumOf { it.totalAmount } }
    val cashSales = remember(sales) { sales.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount } }
    val cardSales = remember(sales) { sales.filter { it.paymentMethod == "CARD" }.sumOf { it.totalAmount } }
    val creditSales = remember(sales) { sales.filter { it.paymentMethod == "CREDIT" }.sumOf { it.totalAmount } }
    val totalExpenses = remember(expenses) { expenses.sumOf { it.amount } }

    val estimatedProfit = remember(totalTurnover, totalExpenses) {
        // Estimated 25% gross margin minus expenses
        (totalTurnover * 0.28) - totalExpenses
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandTealPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("TOTAL RECORDED TURNOVER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                        Text(
                            CurrencyUtils.formatLkr(totalTurnover),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Total Bills: ${sales.size}", color = Color.White, fontSize = 12.sp)
                            Text("Net Profit (Est.): ${CurrencyUtils.formatLkr(estimatedProfit)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                com.example.ui.components.SalesTrendsChart(
                    sales = sales,
                    title = "Revenue Trajectory & Trends"
                )
            }

            item {
                Text("PAYMENT BREAKDOWN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReportMetricRow(label = "Cash Sales", value = CurrencyUtils.formatLkr(cashSales), color = StatusGreen)
                        ReportMetricRow(label = "Card Sales", value = CurrencyUtils.formatLkr(cardSales), color = BrandTealPrimary)
                        ReportMetricRow(label = "Credit Sales (Receivables)", value = CurrencyUtils.formatLkr(creditSales), color = StatusAmber)
                        HorizontalDivider(color = LightBorder)
                        ReportMetricRow(label = "Shop Expenses Paid", value = "-${CurrencyUtils.formatLkr(totalExpenses)}", color = StatusRed)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportMetricRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// -------------------------------------------------------------------------------------
// Suppliers & Purchases Screen
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val suppliers by viewModel.suppliers.collectAsState()
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var supplierToPay by remember { mutableStateOf<SupplierEntity?>(null) }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Suppliers & Purchases", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSupplierDialog = true },
                containerColor = BrandTealPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Supplier", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = LightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val totalPayable = suppliers.sumOf { it.outstandingBalance }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusAmberBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SUPPLIER OUTSTANDING PAYABLES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                            Text(CurrencyUtils.formatLkr(totalPayable), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            items(suppliers) { sup ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sup.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Contact: ${sup.contactPerson} (${sup.phone})", fontSize = 12.sp, color = TextSecondary)
                            Text("Total Purchased: ${CurrencyUtils.formatLkr(sup.totalPurchased)}", fontSize = 11.sp, color = TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (sup.outstandingBalance > 0) {
                                Text(CurrencyUtils.formatLkr(sup.outstandingBalance), fontWeight = FontWeight.Bold, color = StatusAmber)
                                Text("Payable", fontSize = 11.sp, color = StatusAmber)
                                Spacer(modifier = Modifier.height(4.dp))
                                FilledTonalButton(
                                    onClick = { supplierToPay = sup },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Pay", fontSize = 11.sp)
                                }
                            } else {
                                Badge(containerColor = StatusGreenBg) {
                                    Text("Settled", color = StatusGreen, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSupplierDialog) {
        AddSupplierDialog(
            onSave = { name, person, phone, email, notes ->
                viewModel.saveSupplier(0, name, person, phone, email, "", notes)
                showAddSupplierDialog = false
            },
            onDismiss = { showAddSupplierDialog = false }
        )
    }

    if (supplierToPay != null) {
        RecordSupplierPaymentDialog(
            supplier = supplierToPay!!,
            onConfirm = { amt, method, note ->
                viewModel.recordSupplierPayment(supplierToPay!!.id, amt, method, note)
                supplierToPay = null
            },
            onDismiss = { supplierToPay = null }
        )
    }
}

@Composable
fun AddSupplierDialog(
    onSave: (name: String, person: String, phone: String, email: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ADD SUPPLIER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Company / Supplier Name *") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text("Contact Person") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (name.isNotBlank()) onSave(name, person, phone, "", "") },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("SAVE SUPPLIER", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RecordSupplierPaymentDialog(
    supplier: SupplierEntity,
    onConfirm: (amount: Double, method: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(supplier.outstandingBalance.toInt().toString()) }
    var method by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("PAY SUPPLIER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(supplier.name, fontSize = 13.sp, color = TextSecondary)
                Text("Payable: ${CurrencyUtils.formatLkr(supplier.outstandingBalance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusAmber)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "BANK", "CHEQUE").forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) onConfirm(amount, method, note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("RECORD PAYMENT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Cash Register & Shifts Screen
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashRegisterScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val screenPermissions by viewModel.permissions.collectAsState()
    if (screenPermissions.cannot(Permission.MANAGE_CASH)) {
        com.example.ui.components.LockedScreenNotice(
            message = screenPermissions.denialMessage(Permission.MANAGE_CASH),
            onBack = onBack
        )
        return
    }

    val shift by viewModel.currentShift.collectAsState()
    var showCashMovementDialog by remember { mutableStateOf(false) }
    var movementType by remember { mutableStateOf("CASH_IN") }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Cash Drawer & Register", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BrandTealPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("EXPECTED CASH IN DRAWER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                    Text(
                        CurrencyUtils.formatLkr(shift?.expectedCash ?: 10000.0),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Opened by ${shift?.staffName.orEmpty().ifBlank { "you" }} at ${shift?.counterName.orEmpty().ifBlank { "the counter" }} (${CurrencyUtils.formatTimeOnly(shift?.openedAt ?: System.currentTimeMillis())})",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        movementType = "CASH_IN"
                        showCashMovementDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Cash In", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        movementType = "CASH_OUT"
                        showCashMovementDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("- Cash Out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCashMovementDialog && shift != null) {
        CashMovementDialog(
            type = movementType,
            onConfirm = { amt, reason, note ->
                viewModel.recordCashMovement(shift!!.id, movementType, amt, reason, note)
                showCashMovementDialog = false
            },
            onDismiss = { showCashMovementDialog = false }
        )
    }
}

@Composable
fun CashMovementDialog(
    type: String,
    onConfirm: (amount: Double, reason: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(if (type == "CASH_IN") "Added Change / Float" else "Owner Withdrawal") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(if (type == "CASH_IN") "DRAWER CASH IN" else "DRAWER CASH OUT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (Rs.) *") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) onConfirm(amt, reason, note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("RECORD MOVEMENT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Staff Screen
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val screenPermissions by viewModel.permissions.collectAsState()
    if (screenPermissions.cannot(Permission.MANAGE_STAFF)) {
        com.example.ui.components.LockedScreenNotice(
            message = screenPermissions.denialMessage(Permission.MANAGE_STAFF),
            onBack = onBack
        )
        return
    }

    val staffList by viewModel.staffList.collectAsState()
    val profile by viewModel.profile.collectAsState()

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Staff & Cashiers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("SWITCH ACTIVE CASHIER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            items(staffList) { staff ->
                val isActive = profile?.activeStaffId == staff.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isActive) BrandMintSurface else LightSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.switchActiveStaff(staff) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(staff.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${staff.role} • PIN: **** • ${staff.phone}", fontSize = 12.sp, color = TextSecondary)
                        }
                        if (isActive) {
                            Badge(containerColor = BrandTealPrimary) {
                                Text("LOGGED IN", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
                            }
                        } else {
                            TextButton(onClick = { viewModel.switchActiveStaff(staff) }) {
                                Text("Switch")
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Settings Screen
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var phone by remember { mutableStateOf(profile?.phone ?: "077 123 4567") }
    var address by remember { mutableStateOf(profile?.address ?: "123 Main Street, Colombo") }
    var footer by remember { mutableStateOf(profile?.receiptFooter ?: "Thank you!") }
    var printerWidth by remember { mutableStateOf(profile?.printerPaperWidth ?: "58mm") }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("STORE INFORMATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shop Name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("RECEIPT & PRINTER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            item {
                OutlinedTextField(
                    value = footer,
                    onValueChange = { footer = it },
                    label = { Text("Receipt Footer Message") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paper Width", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = printerWidth == "58mm", onClick = { printerWidth = "58mm" }, label = { Text("58mm") })
                        FilterChip(selected = printerWidth == "80mm", onClick = { printerWidth = "80mm" }, label = { Text("80mm") })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val current = profile ?: BusinessProfileEntity()
                        viewModel.saveBusinessProfile(
                            current.copy(
                                name = name,
                                phone = phone,
                                address = address,
                                receiptFooter = footer,
                                printerPaperWidth = printerWidth
                            )
                        )
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("SAVE SETTINGS", fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.setOnboardingStep(1) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-run Setup Wizard")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Activity Log — a plain, readable trail of everything that matters
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.auditLog.collectAsState()
    val permissions by viewModel.permissions.collectAsState()

    if (!permissions.can(Permission.VIEW_AUDIT)) {
        com.example.ui.components.LockedScreenNotice(
            message = permissions.denialMessage(Permission.VIEW_AUDIT),
            onBack = onBack
        )
        return
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Activity log", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = LightBackground
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.components.EmptyState(
                    icon = Icons.Default.History,
                    title = "Nothing here yet",
                    message = "Refunds, price changes, sign-ins and settings changes will show up here."
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.description,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "${entry.staffName.ifBlank { "Someone" }} • ${CurrencyUtils.formatDateTime(entry.timestamp)}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
