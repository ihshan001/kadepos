package com.example.ui.screens.suppliers

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ProductEntity
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SupplierEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.util.WhatsAppHelper
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch

enum class SupplierTab {
    SUPPLIERS,
    PURCHASE_ORDERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersPurchasesScreen(
    viewModel: PosViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(SupplierTab.SUPPLIERS) }

    val suppliers by viewModel.suppliers.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val products by viewModel.products.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, HAS_PAYABLE for suppliers; ALL, DUE, PAID for purchases

    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierToPay by remember { mutableStateOf<SupplierEntity?>(null) }
    var selectedSupplierForDetails by remember { mutableStateOf<SupplierEntity?>(null) }

    var showCreatePoDialog by remember { mutableStateOf(false) }
    var preselectedSupplierForPo by remember { mutableStateOf<SupplierEntity?>(null) }
    var viewingPurchaseDetails by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseToSettle by remember { mutableStateOf<PurchaseEntity?>(null) }

    val totalSupplierPayable = remember(suppliers) { suppliers.sumOf { it.outstandingBalance } }
    val totalPurchasesAmt = remember(purchases) { purchases.sumOf { it.totalAmount } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Suppliers & Purchases",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("suppliers_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (currentTab == SupplierTab.SUPPLIERS) {
                        showAddSupplierDialog = true
                    } else {
                        preselectedSupplierForPo = null
                        showCreatePoDialog = true
                    }
                },
                containerColor = BrandTealPrimary,
                contentColor = Color.White,
                icon = {
                    Icon(
                        if (currentTab == SupplierTab.SUPPLIERS) Icons.Default.PersonAdd else Icons.Default.AddShoppingCart,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        if (currentTab == SupplierTab.SUPPLIERS) "New Supplier" else "New Purchase Order",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.testTag("supplier_fab")
            )
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Tab Selector: Suppliers vs Purchase Orders
            PrimaryTabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = LightSurface,
                contentColor = BrandTealPrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = currentTab == SupplierTab.SUPPLIERS,
                    onClick = { currentTab = SupplierTab.SUPPLIERS },
                    text = { Text("Vendors & Suppliers (${suppliers.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = currentTab == SupplierTab.PURCHASE_ORDERS,
                    onClick = { currentTab = SupplierTab.PURCHASE_ORDERS },
                    text = { Text("Purchase Orders (${purchases.size})", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Outstanding Payables KPI Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (totalSupplierPayable > 0) StatusAmberBg else BrandMintSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "TOTAL OUTSTANDING SUPPLIER PAYABLES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalSupplierPayable > 0) StatusAmber else BrandTealDark
                        )
                        Text(
                            CurrencyUtils.formatLkr(totalSupplierPayable),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                    Badge(containerColor = if (totalSupplierPayable > 0) StatusAmber else BrandTealPrimary) {
                        Text(
                            "${suppliers.count { it.outstandingBalance > 0 }} with Dues",
                            color = Color.White,
                            modifier = Modifier.padding(4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (currentTab == SupplierTab.SUPPLIERS) "Search supplier name, person, phone..."
                        else "Search invoice # or supplier..."
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Filter Chips
            if (currentTab == SupplierTab.SUPPLIERS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All Suppliers (${suppliers.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "HAS_PAYABLE",
                        onClick = { selectedFilter = "HAS_PAYABLE" },
                        label = { Text("Pending Payables (${suppliers.count { it.outstandingBalance > 0 }})") }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All Orders (${purchases.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "DUE",
                        onClick = { selectedFilter = "DUE" },
                        label = { Text("Unpaid / Due (${purchases.count { it.dueAmount > 0 }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "PAID",
                        onClick = { selectedFilter = "PAID" },
                        label = { Text("Fully Settled (${purchases.count { it.dueAmount <= 0 }})") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content Body
            if (currentTab == SupplierTab.SUPPLIERS) {
                val filteredSuppliers = remember(suppliers, searchQuery, selectedFilter) {
                    suppliers.filter { s ->
                        val matchQ = searchQuery.isBlank() ||
                                s.name.contains(searchQuery, ignoreCase = true) ||
                                s.contactPerson.contains(searchQuery, ignoreCase = true) ||
                                s.phone.contains(searchQuery)
                        val matchF = if (selectedFilter == "HAS_PAYABLE") s.outstandingBalance > 0 else true
                        matchQ && matchF
                    }
                }

                if (filteredSuppliers.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No suppliers found", color = TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredSuppliers) { supplier ->
                            SupplierCard(
                                supplier = supplier,
                                onClick = { selectedSupplierForDetails = supplier },
                                onPay = { supplierToPay = supplier },
                                onCreatePo = {
                                    preselectedSupplierForPo = supplier
                                    showCreatePoDialog = true
                                },
                                onCall = {
                                    if (supplier.phone.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${supplier.phone}"))
                                        try { context.startActivity(intent) } catch (_: Exception) {}
                                    }
                                },
                                onWhatsApp = {
                                    if (supplier.phone.isNotBlank()) {
                                        val msg = "Hello ${supplier.name}, contacting from our store regarding inventory supply and purchase orders."
                                        WhatsAppHelper.sendWhatsAppMessage(context, supplier.phone, msg)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                val filteredPurchases = remember(purchases, searchQuery, selectedFilter) {
                    purchases.filter { po ->
                        val matchQ = searchQuery.isBlank() ||
                                po.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                                po.supplierName.contains(searchQuery, ignoreCase = true)
                        val matchF = when (selectedFilter) {
                            "DUE" -> po.dueAmount > 0
                            "PAID" -> po.dueAmount <= 0
                            else -> true
                        }
                        matchQ && matchF
                    }
                }

                if (filteredPurchases.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No purchase orders found", color = TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredPurchases) { po ->
                            PurchaseOrderCard(
                                purchase = po,
                                onClick = { viewingPurchaseDetails = po },
                                onSettle = { purchaseToSettle = po }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add / Edit Supplier
    if (showAddSupplierDialog || editingSupplier != null) {
        val s = editingSupplier
        AddEditSupplierDialog(
            supplier = s,
            onSave = { name, person, phone, email, address, notes ->
                viewModel.saveSupplier(s?.id ?: 0L, name, person, phone, email, address, notes)
                showAddSupplierDialog = false
                editingSupplier = null
            },
            onDismiss = {
                showAddSupplierDialog = false
                editingSupplier = null
            }
        )
    }

    // Dialog: Supplier Details
    selectedSupplierForDetails?.let { sup ->
        SupplierDetailsDialog(
            supplier = sup,
            onEdit = {
                editingSupplier = sup
                selectedSupplierForDetails = null
            },
            onPay = {
                supplierToPay = sup
                selectedSupplierForDetails = null
            },
            onCreatePo = {
                preselectedSupplierForPo = sup
                showCreatePoDialog = true
                selectedSupplierForDetails = null
            },
            onDelete = {
                viewModel.deleteSupplier(sup.id)
                selectedSupplierForDetails = null
            },
            onDismiss = { selectedSupplierForDetails = null }
        )
    }

    // Dialog: Record Supplier Payment / Settle Balance
    supplierToPay?.let { sup ->
        RecordSupplierPaymentDialog(
            supplier = sup,
            onConfirm = { amount, method, note ->
                viewModel.recordSupplierPayment(sup.id, amount, method, note)
                supplierToPay = null
            },
            onDismiss = { supplierToPay = null }
        )
    }

    // Dialog: Create New Purchase Order / Restock Intake
    if (showCreatePoDialog) {
        CreatePurchaseOrderDialog(
            suppliers = suppliers,
            preselectedSupplier = preselectedSupplierForPo,
            products = products,
            onConfirm = { supplier, invoiceNo, items, paidAmount, notes ->
                viewModel.createPurchase(supplier, invoiceNo, items, paidAmount, notes)
                showCreatePoDialog = false
                preselectedSupplierForPo = null
            },
            onDismiss = {
                showCreatePoDialog = false
                preselectedSupplierForPo = null
            }
        )
    }

    // Dialog: View Purchase Order Details
    viewingPurchaseDetails?.let { po ->
        PurchaseOrderDetailsDialog(
            purchase = po,
            viewModel = viewModel,
            onSettle = {
                purchaseToSettle = po
                viewingPurchaseDetails = null
            },
            onDelete = {
                viewModel.deletePurchase(po.id)
                viewingPurchaseDetails = null
            },
            onDismiss = { viewingPurchaseDetails = null }
        )
    }

    // Dialog: Settle Purchase Order Due
    purchaseToSettle?.let { po ->
        SettlePurchaseDueDialog(
            purchase = po,
            onConfirm = { amount, method, note ->
                viewModel.settlePurchaseDue(po.id, amount, method, note)
                purchaseToSettle = null
            },
            onDismiss = { purchaseToSettle = null }
        )
    }
}

// -------------------------------------------------------------------------------------
// Supplier Cards & Dialogs
// -------------------------------------------------------------------------------------

@Composable
fun SupplierCard(
    supplier: SupplierEntity,
    onClick: () -> Unit,
    onPay: () -> Unit,
    onCreatePo: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandMintSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(supplier.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        if (supplier.contactPerson.isNotBlank()) {
                            Text("Contact: ${supplier.contactPerson}", fontSize = 12.sp, color = TextSecondary)
                        }
                        if (supplier.phone.isNotBlank()) {
                            Text(supplier.phone, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (supplier.outstandingBalance > 0) {
                        Text(
                            CurrencyUtils.formatLkr(supplier.outstandingBalance),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = StatusAmber
                        )
                        Text("Payable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                    } else {
                        Badge(containerColor = StatusGreenBg) {
                            Text("Settled", color = StatusGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = LightBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (supplier.phone.isNotBlank()) {
                        IconButton(onClick = onCall, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onWhatsApp, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = StatusGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onCreatePo,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New PO", fontSize = 11.sp)
                    }

                    if (supplier.outstandingBalance > 0) {
                        Button(
                            onClick = onPay,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Pay Due", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditSupplierDialog(
    supplier: SupplierEntity?,
    onSave: (name: String, person: String, phone: String, email: String, address: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(supplier?.name ?: "") }
    var person by remember { mutableStateOf(supplier?.contactPerson ?: "") }
    var phone by remember { mutableStateOf(supplier?.phone ?: "") }
    var email by remember { mutableStateOf(supplier?.email ?: "") }
    var address by remember { mutableStateOf(supplier?.address ?: "") }
    var notes by remember { mutableStateOf(supplier?.notes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    if (supplier == null) "ADD NEW SUPPLIER" else "EDIT SUPPLIER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Supplier / Company Name *") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text("Contact Person Name") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone / WhatsApp Number") },
                    placeholder = { Text("077 123 4567") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Warehouse / Store Address") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Payment Terms, Bank details, etc.)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), person.trim(), phone.trim(), email.trim(), address.trim(), notes.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Supplier", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SupplierDetailsDialog(
    supplier: SupplierEntity,
    onEdit: () -> Unit,
    onPay: () -> Unit,
    onCreatePo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                    Text(supplier.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (supplier.contactPerson.isNotBlank()) {
                    Text("Person: ${supplier.contactPerson}", fontSize = 13.sp, color = TextSecondary)
                }
                if (supplier.phone.isNotBlank()) {
                    Text("Tel: ${supplier.phone}", fontSize = 13.sp, color = TextSecondary)
                }
                if (supplier.address.isNotBlank()) {
                    Text("Address: ${supplier.address}", fontSize = 13.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (supplier.outstandingBalance > 0) StatusAmberBg else BrandMintSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("OUTSTANDING PAYABLE BALANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            CurrencyUtils.formatLkr(supplier.outstandingBalance),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (supplier.outstandingBalance > 0) StatusAmber else BrandTealPrimary
                        )
                        Text("Total Lifetime Purchases: ${CurrencyUtils.formatLkr(supplier.totalPurchased)}", fontSize = 12.sp)
                    }
                }

                if (supplier.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Note: ${supplier.notes}", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = onCreatePo,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New PO")
                    }
                }

                if (supplier.outstandingBalance > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onPay,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payment To Supplier", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusRed),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Delete Supplier", fontSize = 12.sp)
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
                Text("RECORD SUPPLIER PAYMENT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Supplier: ${supplier.name}", fontSize = 13.sp, color = TextSecondary)
                Text("Payable: ${CurrencyUtils.formatLkr(supplier.outstandingBalance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusAmber)

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Payment Amount (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Payment Method", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "BANK", "CHEQUE", "CARD").forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reference / Receipt # (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                onConfirm(amt, method, note.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirm Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Purchase Order Dialogs & Components
// -------------------------------------------------------------------------------------

@Composable
fun PurchaseOrderCard(
    purchase: PurchaseEntity,
    onClick: () -> Unit,
    onSettle: () -> Unit
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
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(purchase.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusColor = when (purchase.paymentStatus) {
                        "PAID" -> StatusGreen
                        "PARTIAL" -> StatusBlue
                        else -> StatusAmber
                    }
                    val statusBg = when (purchase.paymentStatus) {
                        "PAID" -> StatusGreenBg
                        "PARTIAL" -> StatusBlueBg
                        else -> StatusAmberBg
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                        Text(
                            purchase.paymentStatus,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text("Supplier: ${purchase.supplierName}", fontSize = 12.sp, color = TextSecondary)
                Text("${CurrencyUtils.formatDateTime(purchase.timestamp)} • ${purchase.itemsCount} items", fontSize = 11.sp, color = TextMuted)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyUtils.formatLkr(purchase.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                if (purchase.dueAmount > 0) {
                    Text("Due: ${CurrencyUtils.formatLkr(purchase.dueAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = onSettle,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class TempPoItem(
    val productId: Long,
    val productName: String,
    var quantity: Double,
    var costPrice: Double
) {
    val lineTotal: Double get() = quantity * costPrice
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePurchaseOrderDialog(
    suppliers: List<SupplierEntity>,
    preselectedSupplier: SupplierEntity?,
    products: List<ProductEntity>,
    onConfirm: (supplier: SupplierEntity?, invoiceNo: String, items: List<PurchaseItemEntity>, paidAmount: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSupplier by remember { mutableStateOf(preselectedSupplier ?: suppliers.firstOrNull()) }
    var invoiceNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val poItems = remember { mutableStateListOf<TempPoItem>() }
    var selectedProductForAdd by remember { mutableStateOf<ProductEntity?>(null) }
    var addQtyText by remember { mutableStateOf("10") }
    var addCostText by remember { mutableStateOf("") }

    val totalCost = remember(poItems.toList()) { poItems.sumOf { it.lineTotal } }
    var paymentOption by remember { mutableStateOf("PAID_FULL") } // PAID_FULL, PARTIAL, CREDIT_DUE
    var paidAmountText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CREATE PURCHASE ORDER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Supplier Picker
                    item {
                        Text("1. Supplier Details", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (suppliers.isEmpty()) {
                            Text("No suppliers registered yet. Add a supplier first or proceed as Local Vendor.", fontSize = 12.sp, color = StatusAmber)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                suppliers.forEach { sup ->
                                    FilterChip(
                                        selected = selectedSupplier?.id == sup.id,
                                        onClick = { selectedSupplier = sup },
                                        label = { Text(sup.name) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = invoiceNo,
                            onValueChange = { invoiceNo = it },
                            label = { Text("Supplier Invoice # (e.g. INV-8921)") },
                            shape = RoundedCornerShape(10.dp),
                            textStyle = TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // Product Adder
                    item {
                        HorizontalDivider(color = LightBorder)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("2. Add Products to Restock", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Product dropdown / chip picker
                        Text("Select Product:", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            products.take(6).forEach { prod ->
                                FilterChip(
                                    selected = selectedProductForAdd?.id == prod.id,
                                    onClick = {
                                        selectedProductForAdd = prod
                                        addCostText = prod.costPrice.toString()
                                    },
                                    label = { Text(prod.name) }
                                )
                            }
                        }
                    }

                    if (selectedProductForAdd != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Item: ${selectedProductForAdd?.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = addQtyText,
                                            onValueChange = { addQtyText = it },
                                            label = { Text("Quantity") },
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = TextStyle(color = Color.Black),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = addCostText,
                                            onValueChange = { addCostText = it },
                                            label = { Text("Cost Price (Rs.)") },
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = TextStyle(color = Color.Black),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val p = selectedProductForAdd ?: return@Button
                                            val q = addQtyText.toDoubleOrNull() ?: 1.0
                                            val c = addCostText.toDoubleOrNull() ?: p.costPrice
                                            poItems.add(TempPoItem(productId = p.id, productName = p.name, quantity = q, costPrice = c))
                                            selectedProductForAdd = null
                                            addQtyText = "10"
                                            addCostText = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("+ Add to Order", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Added items list
                    if (poItems.isNotEmpty()) {
                        item {
                            Text("Items in Purchase Order (${poItems.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        items(poItems.size) { idx ->
                            val item = poItems[idx]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LightBackground,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${item.quantity} units @ ${CurrencyUtils.formatLkr(item.costPrice)}", fontSize = 11.sp, color = TextSecondary)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(CurrencyUtils.formatLkr(item.lineTotal), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        IconButton(onClick = { poItems.removeAt(idx) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Payment summary
                    item {
                        HorizontalDivider(color = LightBorder)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("3. Payment & Settlement", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total PO Amount:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(CurrencyUtils.formatLkr(totalCost), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = BrandTealPrimary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = paymentOption == "PAID_FULL",
                                onClick = {
                                    paymentOption = "PAID_FULL"
                                    paidAmountText = totalCost.toString()
                                },
                                label = { Text("Paid in Full") }
                            )
                            FilterChip(
                                selected = paymentOption == "PARTIAL",
                                onClick = { paymentOption = "PARTIAL" },
                                label = { Text("Partial Paid") }
                            )
                            FilterChip(
                                selected = paymentOption == "CREDIT_DUE",
                                onClick = {
                                    paymentOption = "CREDIT_DUE"
                                    paidAmountText = "0"
                                },
                                label = { Text("Full Credit Due") }
                            )
                        }

                        if (paymentOption == "PARTIAL") {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = paidAmountText,
                                onValueChange = { paidAmountText = it },
                                label = { Text("Amount Paid Now (Rs.)") },
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (poItems.isNotEmpty()) {
                            val paid = when (paymentOption) {
                                "PAID_FULL" -> totalCost
                                "CREDIT_DUE" -> 0.0
                                else -> paidAmountText.toDoubleOrNull() ?: 0.0
                            }
                            val items = poItems.map {
                                PurchaseItemEntity(
                                    purchaseId = 0,
                                    productId = it.productId,
                                    productName = it.productName,
                                    quantity = it.quantity,
                                    costPrice = it.costPrice,
                                    lineTotal = it.lineTotal
                                )
                            }
                            onConfirm(selectedSupplier, invoiceNo, items, paid, notes)
                        }
                    },
                    enabled = poItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE PURCHASE & RESTOCK INVENTORY", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PurchaseOrderDetailsDialog(
    purchase: PurchaseEntity,
    viewModel: PosViewModel,
    onSettle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var items by remember { mutableStateOf<List<PurchaseItemEntity>>(emptyList()) }

    LaunchedEffect(purchase.id) {
        items = viewModel.getPurchaseItems(purchase.id)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(purchase.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Supplier: ${purchase.supplierName}", fontSize = 13.sp, color = TextSecondary)
                        Text(CurrencyUtils.formatDateTime(purchase.timestamp), fontSize = 11.sp, color = TextMuted)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (purchase.dueAmount > 0) StatusAmberBg else BrandMintSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Invoice Amount:", fontSize = 13.sp)
                            Text(CurrencyUtils.formatLkr(purchase.totalAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount Paid:", fontSize = 13.sp)
                            Text(CurrencyUtils.formatLkr(purchase.paidAmount), fontWeight = FontWeight.Bold, color = StatusGreen, fontSize = 14.sp)
                        }
                        if (purchase.dueAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Outstanding Due:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(CurrencyUtils.formatLkr(purchase.dueAmount), fontWeight = FontWeight.ExtraBold, color = StatusAmber, fontSize = 15.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Received Items (${items.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LightBackground,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${item.quantity} units @ ${CurrencyUtils.formatLkr(item.costPrice)}", fontSize = 11.sp, color = TextSecondary)
                                }
                                Text(CurrencyUtils.formatLkr(item.lineTotal), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val context = LocalContext.current
                val profile by viewModel.profile.collectAsState()
                val suppliers by viewModel.suppliers.collectAsState()
                val supplierPhone = remember(purchase.supplierId, suppliers) {
                    suppliers.find { it.id == purchase.supplierId || it.name.equals(purchase.supplierName, ignoreCase = true) }?.phone ?: ""
                }

                Button(
                    onClick = {
                        val storeName = profile?.name ?: "Our Store"
                        val poMsg = buildString {
                            appendLine("📄 *PURCHASE ORDER INVOICE: ${purchase.invoiceNumber}*")
                            appendLine("🏪 Store: *$storeName*")
                            appendLine("🏢 Supplier: *${purchase.supplierName}*")
                            appendLine("📅 Date: ${CurrencyUtils.formatDateOnly(purchase.timestamp)}")
                            appendLine("--------------------------------")
                            appendLine("📋 *Items Ordered (${items.size}):*")
                            items.forEachIndexed { i, item ->
                                appendLine("${i + 1}. *${item.productName}*")
                                appendLine("   • ${item.quantity} units @ ${CurrencyUtils.formatLkr(item.costPrice)} = ${CurrencyUtils.formatLkr(item.lineTotal)}")
                            }
                            appendLine("--------------------------------")
                            appendLine("💰 *Invoice Total:* *${CurrencyUtils.formatLkr(purchase.totalAmount)}*")
                            appendLine("💵 *Paid Amount:* ${CurrencyUtils.formatLkr(purchase.paidAmount)}")
                            if (purchase.dueAmount > 0) {
                                appendLine("⚠️ *Outstanding Balance Due:* *${CurrencyUtils.formatLkr(purchase.dueAmount)}*")
                            }
                            if (purchase.notes.isNotBlank()) {
                                appendLine("📝 *Note:* ${purchase.notes}")
                            }
                            appendLine("--------------------------------")
                            appendLine("Thank you for your business partnership!")
                        }
                        WhatsAppHelper.sendWhatsAppMessage(context, supplierPhone, poMsg)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PO via WhatsApp", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (purchase.dueAmount > 0) {
                    Button(
                        onClick = onSettle,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pay Remaining Due (${CurrencyUtils.formatLkr(purchase.dueAmount)})", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusRed),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Delete PO Record", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettlePurchaseDueDialog(
    purchase: PurchaseEntity,
    onConfirm: (amount: Double, method: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(purchase.dueAmount.toInt().toString()) }
    var method by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("SETTLE PURCHASE ORDER DUE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Invoice: ${purchase.invoiceNumber} (${purchase.supplierName})", fontSize = 13.sp, color = TextSecondary)
                Text("Due: ${CurrencyUtils.formatLkr(purchase.dueAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusAmber)

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Payment Amount (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Payment Method", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "BANK", "CHEQUE", "CARD").forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Cheque # (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                onConfirm(amt, method, note.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Record Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
