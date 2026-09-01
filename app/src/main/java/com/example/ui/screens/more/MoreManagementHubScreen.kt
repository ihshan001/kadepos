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
import com.example.data.model.StaffRole
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            MoreDestination.CLOUD -> CloudBackupScreen(viewModel = viewModel, onBack = onBackToHub)
            MoreDestination.PRINTER -> com.example.ui.screens.printer.PrinterSetupScreen(
                viewModel = viewModel,
                profile = viewModel.profile.collectAsState().value,
                onBack = onBackToHub
            )
            MoreDestination.ALERTS -> NotificationsScreen(viewModel = viewModel, onBack = onBackToHub)
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
    val unreadAlerts by viewModel.unreadNotificationCount.collectAsState()
    val cloudSettings by viewModel.cloudSettings.collectAsState()

    val tracksStock = profile?.trackStock == true
    val creditEnabled = profile?.creditEnabled == true
    // Not every shop counts a float in and out of a drawer.
    val usesCashDrawer = profile?.cashDrawerEnabled == true
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
                    IconButton(onClick = { onSelectDestination(MoreDestination.ALERTS) }) {
                        BadgedBox(
                            badge = {
                                if (unreadAlerts > 0) {
                                    Badge(containerColor = StatusRed) {
                                        Text(
                                            if (unreadAlerts > 9) "9+" else "$unreadAlerts",
                                            color = Color.White,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Alerts",
                                tint = BrandPrimary
                            )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Who is signed in and what they are. Without this a shared counter
            // phone gives no clue whose name is going on the next bill.
            item {
                WhoIsUsingCard(
                    shopName = profile?.name.orEmpty().ifBlank { "Your shop" },
                    personName = permissions.staffName,
                    roleName = if (permissions.isSoloOwner) {
                        "Owner"
                    } else {
                        permissions.role.friendlyName
                    },
                    isSolo = permissions.isSoloOwner
                )
            }

            item {
                HubActionCard(
                    title = "Things needing attention",
                    subtitle = "Low stock, money owed to you, bills to pay",
                    icon = Icons.Default.NotificationsActive,
                    badge = if (alertCount > 0) "$alertCount" else null,
                    onClick = { onSelectDestination(MoreDestination.NOTIFICATIONS) }
                )
            }

            item {
                HubActionCard(
                    title = "Alerts",
                    subtitle = if (unreadAlerts > 0) {
                        "$unreadAlerts new since you last looked"
                    } else {
                        "Sales, refunds and warnings as they happen"
                    },
                    icon = Icons.Default.Notifications,
                    badge = if (unreadAlerts > 0) "$unreadAlerts" else null,
                    onClick = { onSelectDestination(MoreDestination.ALERTS) }
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

            if (usesCashDrawer && permissions.can(Permission.MANAGE_CASH)) {
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
                        subtitle = if (profile?.staffEnabled == true) {
                            "Add staff and choose what each person can do"
                        } else {
                            // Solo shops keep the entry as the way to discover
                            // the feature, but the wording must not imply a team.
                            "Working alone. Tap to start adding staff."
                        },
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

            if (permissions.can(Permission.MANAGE_SETTINGS) && cloudSettings?.providerEnabled == true) {
                item {
                    HubActionCard(
                        title = "Backup & Cloud",
                        subtitle = if (cloudSettings?.ownerGmail.isNullOrBlank()) {
                            "Connect a Google account to keep a safe copy"
                        } else {
                            "Connected to ${cloudSettings?.ownerGmail}"
                        },
                        icon = Icons.Default.Cloud,
                        onClick = { onSelectDestination(MoreDestination.CLOUD) }
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

            // Staff cannot open the Team screen, so this is the only way for
            // them to find out why something is greyed out. Answering that
            // question in the app saves a phone call to the owner.
            if (!permissions.isSoloOwner && !permissions.can(Permission.MANAGE_STAFF)) {
                item {
                    var showMine by remember { mutableStateOf(false) }
                    HubActionCard(
                        title = "What I can do",
                        subtitle = "${permissions.granted.size} things you are allowed to use",
                        icon = Icons.Default.VerifiedUser,
                        onClick = { showMine = true }
                    )
                    if (showMine) {
                        MyAccessDialog(
                            roleName = permissions.role.friendlyName,
                            roleSummary = permissions.role.summary,
                            allowed = permissions.granted.toList().sortedBy { it.label },
                            blocked = (Permission.entries.toSet() - permissions.granted)
                                .toList().sortedBy { it.label },
                            onDismiss = { showMine = false }
                        )
                    }
                }
            }

            // A one-person shop has nobody to sign back in as, so offering
            // "Sign out" would just lock them out of their own till.
            if (!permissions.isSoloOwner) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { viewModel.signOut() }) {
                            Text(
                                "Sign out of ${permissions.staffName.ifBlank { "this shop" }}",
                                color = StatusRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
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

/** A read-only view of your own access, for staff who cannot open Team. */
@Composable
private fun MyAccessDialog(
    roleName: String,
    roleSummary: String,
    allowed: List<Permission>,
    blocked: List<Permission>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("You are a $roleName", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextPrimary)
                Text(roleSummary, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            "YOU CAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StatusGreen
                        )
                    }
                    items(allowed, key = { "y" + it.name }) { perm ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(perm.label, fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                    if (blocked.isNotEmpty()) {
                        item {
                            Text(
                                "ASK THE OWNER FOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }
                        items(blocked, key = { "n" + it.name }) { perm ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(perm.label, fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Close") }
            }
        }
    }
}

/**
 * Shows whose hands the till is in. On a shared counter phone this is the
 * difference between "my name goes on this bill" and having no idea.
 */
@Composable
private fun WhoIsUsingCard(
    shopName: String,
    personName: String,
    roleName: String,
    isSolo: Boolean
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrandPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BrandOnPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    personName.take(1).uppercase().ifBlank { "S" },
                    color = BrandOnPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shopName,
                    color = BrandOnPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    if (isSolo) {
                        "Just you — nothing is locked"
                    } else {
                        "$personName · $roleName"
                    },
                    color = BrandOnPrimary.copy(alpha = 0.85f),
                    fontSize = 12.sp
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
                        .background(BrandSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = BrandPrimary) {
                                Text(badge, color = BrandOnPrimary, fontSize = 10.sp)
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
                    colors = CardDefaults.cardColors(containerColor = BrandPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("TOTAL RECORDED TURNOVER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandOnPrimary.copy(alpha = 0.8f))
                        Text(
                            CurrencyUtils.formatLkr(totalTurnover),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandOnPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Total Bills: ${sales.size}", color = BrandOnPrimary, fontSize = 12.sp)
                            Text("Net Profit (Est.): ${CurrencyUtils.formatLkr(estimatedProfit)}", color = BrandOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        ReportMetricRow(label = "Card Sales", value = CurrencyUtils.formatLkr(cardSales), color = BrandPrimary)
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
                containerColor = BrandPrimary,
                contentColor = BrandOnPrimary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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

    // The shop said it does not run a counted drawer. Explain rather than
    // showing an open/close routine they have no use for.
    val cashProfile by viewModel.profile.collectAsState()
    if (cashProfile?.cashDrawerEnabled != true) {
        com.example.ui.components.LockedScreenNotice(
            message = "This shop does not use a cash drawer count. If you want to " +
                "count your float at the start and end of each day, turn on " +
                "\"Cash drawer\" in Settings.",
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
                colors = CardDefaults.cardColors(containerColor = BrandPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("EXPECTED CASH IN DRAWER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandOnPrimary.copy(alpha = 0.8f))
                    Text(
                        CurrencyUtils.formatLkr(shift?.expectedCash ?: 10000.0),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandOnPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Opened by ${shift?.staffName.orEmpty().ifBlank { "you" }} at ${shift?.counterName.orEmpty().ifBlank { "the counter" }} (${CurrencyUtils.formatTimeOnly(shift?.openedAt ?: System.currentTimeMillis())})",
                        fontSize = 12.sp,
                        color = BrandOnPrimary.copy(alpha = 0.9f)
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White),
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.White),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
// ---------------------------------------------------------------------------
// Team. Who works here, what each person is allowed to touch, and who is at
// the till right now. Only reachable with MANAGE_STAFF.
// ---------------------------------------------------------------------------
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

    var editing by remember { mutableStateOf<StaffEntity?>(null) }
    var addingNew by remember { mutableStateOf(false) }
    var permissionsFor by remember { mutableStateOf<StaffEntity?>(null) }

    // A shop that told us "it is just me" has no team to manage. Rather than
    // showing an empty list, explain how to turn the feature on.
    val teamMode = profile?.staffEnabled == true

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("My team", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            if (teamMode) {
                ExtendedFloatingActionButton(
                    onClick = { addingNew = true },
                    containerColor = BrandPrimary,
                    contentColor = BrandOnPrimary
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add person", fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = LightBackground
    ) { padding ->
        if (!teamMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "You run this shop on your own",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Text(
                    "Nothing is locked and no PIN is needed. If someone starts helping " +
                        "you at the counter, switch this on and you can decide exactly " +
                        "what each person is allowed to do.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                // Without this the hub card is a dead end: it tells a solo owner
                // to add staff and then offers no way to do it.
                Button(
                    onClick = { viewModel.enableTeamForOwner() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I have staff now", fontWeight = FontWeight.Bold)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tap a person to change their details, or \"What they can do\" to " +
                        "allow or block things like changing prices.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (staffList.none { it.role.equals("Owner", ignoreCase = true) }) {
                item {
                    com.example.ui.components.HintCard(
                        text = "No Owner role exists yet. Add the person who owns the shop as Owner " +
                            "so you can always get back into a team shop."
                    )
                }
            } else if (staffList.none { it.role.equals("Owner", ignoreCase = true) && it.pin.isNotBlank() }) {
                item {
                    com.example.ui.components.HintCard(
                        text = "Set a PIN on your Owner card before signing out. Without an Owner PIN, " +
                            "the app cannot let you back in if someone else signs out first."
                    )
                }
            }

            if (staffList.isEmpty()) {
                item {
                    com.example.ui.components.HintCard(
                        text = "No one added yet. Tap \"Add person\" to give a staff member " +
                            "their own PIN, so every bill shows who sold it."
                    )
                }
            }

            items(staffList, key = { it.id }) { staff ->
                val resolved = viewModel.permissionsFor(staff)
                val isAtTill = profile?.activeStaffId == staff.id
                StaffCard(
                    staff = staff,
                    permissionCount = resolved.granted.size,
                    isCustomised = resolved.isCustomised(),
                    isAtTill = isAtTill,
                    onEdit = { editing = staff },
                    onPermissions = { permissionsFor = staff },
                    onToggleActive = { viewModel.setStaffActive(staff, !staff.isActive) },
                    onUseAtTill = { viewModel.switchActiveStaff(staff) }
                )
            }
        }
    }

    if (addingNew || editing != null) {
        val defaultNewRole = if (staffList.any { it.role.equals("Owner", ignoreCase = true) }) {
            StaffRole.CASHIER
        } else {
            StaffRole.OWNER
        }
        StaffEditorDialog(
            existing = editing,
            defaultRole = defaultNewRole,
            onSave = { name, phone, role, pin, active ->
                viewModel.saveStaff(
                    id = editing?.id ?: 0L,
                    name = name,
                    phone = phone,
                    role = role,
                    pin = pin,
                    isActive = active
                )
                editing = null
                addingNew = false
            },
            onDismiss = { editing = null; addingNew = false }
        )
    }

    permissionsFor?.let { staff ->
        StaffPermissionsDialog(
            staff = staff,
            resolved = viewModel.permissionsFor(staff),
            onToggle = { perm, allowed -> viewModel.setStaffPermission(staff, perm, allowed) },
            onReset = { viewModel.resetStaffPermissions(staff); permissionsFor = null },
            onDismiss = { permissionsFor = null }
        )
    }
}

@Composable
private fun StaffCard(
    staff: StaffEntity,
    permissionCount: Int,
    isCustomised: Boolean,
    isAtTill: Boolean,
    onEdit: () -> Unit,
    onPermissions: () -> Unit,
    onToggleActive: () -> Unit,
    onUseAtTill: () -> Unit
) {
    val role = StaffRole.fromName(staff.role)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (staff.isActive) LightSurface else LightSurfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (staff.isActive) BrandSurface else LightBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        staff.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (staff.isActive) BrandPrimary else TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            staff.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (staff.isActive) TextPrimary else TextSecondary
                        )
                        if (isAtTill) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = BrandPrimary) {
                                Text(
                                    "AT THE TILL",
                                    color = BrandOnPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        role.friendlyName + if (staff.pin.isNotBlank()) "  ·  PIN set" else "  ·  No PIN",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    if (!staff.isActive) {
                        Text("Paused — cannot sign in", fontSize = 11.sp, color = StatusAmber)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (isCustomised) "$permissionCount things allowed (adjusted)"
                else "$permissionCount things allowed",
                fontSize = 11.sp,
                color = if (isCustomised) StatusBlue else TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPermissions,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("What they can do", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Edit", fontSize = 12.sp)
                }
                if (!isAtTill) {
                    OutlinedButton(
                        onClick = onUseAtTill,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("At till", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onToggleActive) {
                    Text(
                        if (staff.isActive) "Pause" else "Restore",
                        fontSize = 12.sp,
                        color = if (staff.isActive) StatusAmber else StatusGreen
                    )
                }
            }
        }
    }
}

/** Add or edit one person: name, phone, role and their own 4 digit PIN. */
@Composable
private fun StaffEditorDialog(
    existing: StaffEntity?,
    defaultRole: StaffRole = StaffRole.CASHIER,
    onSave: (String, String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var role by remember { mutableStateOf(existing?.let { StaffRole.fromName(it.role) } ?: defaultRole) }
    var pin by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(existing?.isActive ?: true) }

    val nameOk = name.trim().isNotBlank()
    val pinOk = pin.isEmpty() || pin.length == 4
    // A brand new person with no PIN could never sign in.
    val needsPin = existing == null && pin.length != 4

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (existing == null) "Add someone to your team" else "Edit ${existing.name}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Their name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == ' ' } },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("What is their job?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                StaffRole.selectableRoles.forEach { option ->
                    val selected = role == option
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) BrandSurface else LightSurface
                        ),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { role = option }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { role = option })
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    option.friendlyName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(option.summary, fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it.filter(Char::isDigit) },
                    label = {
                        Text(if (existing == null) "Their 4 number PIN" else "New PIN (leave blank to keep)")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = {
                        Text(
                            "They type this to sign in. Every bill records who sold it.",
                            fontSize = 11.sp
                        )
                    },
                    isError = !pinOk,
                    modifier = Modifier.fillMaxWidth()
                )

                if (existing != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Can sign in", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Turn off when someone leaves, instead of deleting them.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { onSave(name, phone, role.roleName, pin, active) },
                        enabled = nameOk && pinOk && !needsPin,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}

/**
 * The permission editor. Grouped by area with plain descriptions, because the
 * person reading it runs a shop, not a server.
 */
@Composable
private fun StaffPermissionsDialog(
    staff: StaffEntity,
    resolved: com.example.data.model.PermissionSet,
    onToggle: (Permission, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val role = StaffRole.fromName(staff.role)
    val isOwner = role == StaffRole.OWNER

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "What ${staff.name} can do",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    role.friendlyName + " · " + role.summary,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (isOwner) {
                    Spacer(modifier = Modifier.height(10.dp))
                    com.example.ui.components.HintCard(
                        text = "Owners always have full access. To limit someone, " +
                            "make them a Manager, Senior staff or Cashier instead."
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Permission.grouped().forEach { (group, perms) ->
                        item(key = "hdr_" + group.name) {
                            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                Text(
                                    group.title.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BrandPrimary
                                )
                                Text(group.blurb, fontSize = 10.sp, color = TextMuted)
                            }
                        }
                        items(perms, key = { it.name }) { perm ->
                            val allowed = resolved.can(perm)
                            val differs = allowed != role.permissions.contains(perm)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            perm.label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        if (perm.sensitive) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Sensitive",
                                                tint = StatusAmber,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        if (differs) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                "changed",
                                                fontSize = 9.sp,
                                                color = StatusBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(perm.description, fontSize = 11.sp, color = TextSecondary)
                                }
                                Switch(
                                    checked = allowed,
                                    enabled = !isOwner,
                                    onCheckedChange = { onToggle(perm, it) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isOwner && resolved.isCustomised()) {
                        OutlinedButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Reset to standard", fontSize = 12.sp) }
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier.weight(1f)
                    ) { Text("Done") }
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
