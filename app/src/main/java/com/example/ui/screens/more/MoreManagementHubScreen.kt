package com.example.ui.screens.more

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
            MoreDestination.EXPENSES -> ExpensesScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.STAFF -> StaffScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.REGISTER -> CashRegisterScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.NOTIFICATIONS -> ActionCenterScreen(
                viewModel = viewModel,
                onBack = onBackToHub,
                onNavigateToDestination = { dest -> onSelectDestination(dest) }
            )
            MoreDestination.SETTINGS -> SettingsConfigurationScreen(viewModel = viewModel, onBack = onBackToHub)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Management & Tools", fontWeight = FontWeight.Bold) },
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
                    title = "Action Center & Alerts",
                    subtitle = "Low stock alerts, pending payables, overdue Khata balance",
                    icon = Icons.Default.NotificationsActive,
                    badge = "Action",
                    onClick = { onSelectDestination(MoreDestination.NOTIFICATIONS) }
                )
            }

            item {
                Text("BUSINESS HUBS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            item {
                HubActionCard(
                    title = "Business Reports & Profit",
                    subtitle = "Turnover, gross profit, sales breakdown",
                    icon = Icons.Default.BarChart,
                    badge = "Insights",
                    onClick = { onSelectDestination(MoreDestination.REPORTS) }
                )
            }

            item {
                HubActionCard(
                    title = "Customer Credit Book",
                    subtitle = "Customer receivables, pay later balances, WhatsApp reminders",
                    icon = Icons.Default.Book,
                    badge = "Khata",
                    onClick = { onSelectDestination(MoreDestination.CREDIT_BOOK) }
                )
            }

            item {
                HubActionCard(
                    title = "Suppliers & Purchases",
                    subtitle = "Manage vendors, restock invoices, payables",
                    icon = Icons.Default.LocalShipping,
                    onClick = { onSelectDestination(MoreDestination.SUPPLIERS) }
                )
            }

            item {
                HubActionCard(
                    title = "Shop Expenses Tracker",
                    subtitle = "Electricity, rent, transport, tea, packaging",
                    icon = Icons.Default.Receipt,
                    onClick = { onSelectDestination(MoreDestination.EXPENSES) }
                )
            }

            item {
                HubActionCard(
                    title = "Cash Register & Shifts",
                    subtitle = "Counter drawer cash in/out, shift handovers",
                    icon = Icons.Default.PointOfSale,
                    onClick = { onSelectDestination(MoreDestination.REGISTER) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text("TEAM & SETTINGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            item {
                HubActionCard(
                    title = "Staff & Cashiers",
                    subtitle = "Current cashier: ${profile?.activeStaffName ?: "Staff"}",
                    icon = Icons.Default.Badge,
                    onClick = { onSelectDestination(MoreDestination.STAFF) }
                )
            }

            item {
                HubActionCard(
                    title = "Store & Printer Settings",
                    subtitle = "Business profile, thermal printer, receipt footer",
                    icon = Icons.Default.Settings,
                    onClick = { onSelectDestination(MoreDestination.SETTINGS) }
                )
            }
        }
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
// Expenses Screen
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shop Expenses", fontWeight = FontWeight.Bold) },
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
                onClick = { showAddDialog = true },
                containerColor = BrandTealPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Expense", fontWeight = FontWeight.Bold) }
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
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL EXPENSES PAID", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                        Text(
                            CurrencyUtils.formatLkr(expenses.sumOf { it.amount }),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = StatusRed
                        )
                    }
                }
            }

            items(expenses) { exp ->
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
                        Column {
                            Text(exp.category, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${CurrencyUtils.formatDateTime(exp.timestamp)} • ${exp.paymentMethod}", fontSize = 12.sp, color = TextSecondary)
                            if (exp.note.isNotBlank()) {
                                Text(exp.note, fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Text(
                            "-${CurrencyUtils.formatLkr(exp.amount)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = StatusRed
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onConfirm = { cat, amt, method, ref, note ->
                viewModel.addExpense(cat, amt, method, ref, note)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddExpenseDialog(
    onConfirm: (category: String, amount: Double, method: String, reference: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf("Electricity") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("RECORD SHOP EXPENSE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Electricity", "Rent", "Transport", "Tea/Snacks", "Packaging").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
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
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Description") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) onConfirm(category, amt, method, "", note)
                    },
                    enabled = amountText.toDoubleOrNull() != null,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("SAVE EXPENSE", fontWeight = FontWeight.Bold)
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
    val shift by viewModel.currentShift.collectAsState()
    var showCashMovementDialog by remember { mutableStateOf(false) }
    var movementType by remember { mutableStateOf("CASH_IN") }

    Scaffold(
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
                        "Opened by ${shift?.staffName ?: "Aslam"} at ${shift?.counterName ?: "Counter 01"} (${CurrencyUtils.formatTimeOnly(shift?.openedAt ?: System.currentTimeMillis())})",
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
    val staffList by viewModel.staffList.collectAsState()
    val profile by viewModel.profile.collectAsState()

    Scaffold(
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
    var name by remember { mutableStateOf(profile?.name ?: "ABC Stores") }
    var phone by remember { mutableStateOf(profile?.phone ?: "077 123 4567") }
    var address by remember { mutableStateOf(profile?.address ?: "123 Main Street, Colombo") }
    var footer by remember { mutableStateOf(profile?.receiptFooter ?: "Thank you!") }
    var printerWidth by remember { mutableStateOf(profile?.printerPaperWidth ?: "58mm") }

    Scaffold(
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
