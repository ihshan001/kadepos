package com.example.ui.screens.customers

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
import com.example.data.model.CreditTransactionEntity
import com.example.data.model.CustomerEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersCreditScreen(
    viewModel: PosViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, CREDIT_DUE

    var selectedCustomerForDetails by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToPay by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForManualCredit by remember { mutableStateOf<CustomerEntity?>(null) }

    val totalCreditReceivables = remember(customers) { customers.sumOf { it.creditBalance } }
    val countWithDues = remember(customers) { customers.count { it.creditBalance > 0 } }

    val filteredCustomers = remember(customers, searchQuery, selectedFilter) {
        customers.filter { c ->
            val matchQuery = searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery)
            val matchFilter = if (selectedFilter == "CREDIT_DUE") c.creditBalance > 0 else true
            matchQuery && matchFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Credit Book & Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("credit_book_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                containerColor = BrandTealPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("New Customer", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_customer_fab")
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
            // Outstanding Credit Receivables KPI Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (totalCreditReceivables > 0) StatusAmberBg else BrandMintSurface
                ),
                border = if (selectedFilter == "CREDIT_DUE") CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusAmber), width = 2.dp) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedFilter = if (selectedFilter == "CREDIT_DUE") "ALL" else "CREDIT_DUE"
                    }
                    .testTag("credit_book_kpi_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "TOTAL OUTSTANDING CREDIT RECEIVABLES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalCreditReceivables > 0) StatusAmber else BrandTealDark
                        )
                        Text(
                            CurrencyUtils.formatLkr(totalCreditReceivables),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (selectedFilter == "CREDIT_DUE") "Showing debtors only • Tap to show all" else "Tap to filter debtors with dues",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                    Badge(containerColor = if (totalCreditReceivables > 0) StatusAmber else BrandTealPrimary) {
                        Text(
                            "$countWithDues with Dues",
                            color = Color.White,
                            modifier = Modifier.padding(4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer name, phone number...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All Customers (${customers.size})") }
                )
                FilterChip(
                    selected = selectedFilter == "CREDIT_DUE",
                    onClick = { selectedFilter = "CREDIT_DUE" },
                    label = { Text("With Outstanding Due ($countWithDues)") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Customer List
            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "No customers matching '$searchQuery'" else "No customer accounts found",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomers) { customer ->
                        CustomerCreditCard(
                            customer = customer,
                            onClick = { selectedCustomerForDetails = customer },
                            onPay = { customerToPay = customer },
                            onAddCredit = { customerForManualCredit = customer },
                            onCall = {
                                if (customer.phone.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            },
                            onWhatsApp = {
                                if (customer.phone.isNotBlank()) {
                                    val formattedPhone = customer.phone.replace("+", "").replace(" ", "").replace("-", "")
                                    val msg = "Dear ${customer.name},\nThis is a friendly reminder from our store regarding your outstanding credit balance of ${CurrencyUtils.formatLkr(customer.creditBalance)}. Kindly arrange settlement at your earliest convenience.\nThank you!"
                                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}")
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Add / Edit Customer
    if (showAddCustomerDialog || editingCustomer != null) {
        val cust = editingCustomer
        AddEditCustomerDialog(
            customer = cust,
            onSave = { name, phone, email, address, limit, notes ->
                viewModel.saveCustomer(cust?.id ?: 0L, name, phone, email, address, limit, notes)
                showAddCustomerDialog = false
                editingCustomer = null
            },
            onDismiss = {
                showAddCustomerDialog = false
                editingCustomer = null
            }
        )
    }

    // Dialog: Customer Details & Full Transaction Ledger
    selectedCustomerForDetails?.let { customer ->
        // Refresh customer from list if updated
        val liveCustomer = customers.find { it.id == customer.id } ?: customer
        CustomerLedgerDetailsDialog(
            customer = liveCustomer,
            viewModel = viewModel,
            onEdit = {
                editingCustomer = liveCustomer
                selectedCustomerForDetails = null
            },
            onPay = {
                customerToPay = liveCustomer
                selectedCustomerForDetails = null
            },
            onAddCredit = {
                customerForManualCredit = liveCustomer
                selectedCustomerForDetails = null
            },
            onDelete = {
                viewModel.deleteCustomer(liveCustomer.id)
                selectedCustomerForDetails = null
            },
            onSendWhatsApp = {
                val formattedPhone = liveCustomer.phone.replace("+", "").replace(" ", "").replace("-", "")
                val msg = "Dear ${liveCustomer.name},\nYour current outstanding credit balance is ${CurrencyUtils.formatLkr(liveCustomer.creditBalance)} (Credit Limit: ${CurrencyUtils.formatLkr(liveCustomer.creditLimit)}).\nThank you for your business!"
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}")
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
            },
            onDismiss = { selectedCustomerForDetails = null }
        )
    }

    // Dialog: Record Payment Settlement
    customerToPay?.let { customer ->
        RecordCreditPaymentDialog(
            customer = customer,
            onConfirm = { amount, method, note ->
                viewModel.recordCustomerCreditPayment(customer.id, amount, method, note)
                customerToPay = null
            },
            onDismiss = { customerToPay = null }
        )
    }

    // Dialog: Manual Credit Entry / Charge
    customerForManualCredit?.let { customer ->
        AddManualCreditDialog(
            customer = customer,
            onConfirm = { amount, reason, note ->
                viewModel.recordManualCustomerCredit(customer.id, amount, reason, note)
                customerForManualCredit = null
            },
            onDismiss = { customerForManualCredit = null }
        )
    }
}

// -------------------------------------------------------------------------------------
// Customer Card & Dialogs
// -------------------------------------------------------------------------------------

@Composable
fun CustomerCreditCard(
    customer: CustomerEntity,
    onClick: () -> Unit,
    onPay: () -> Unit,
    onAddCredit: () -> Unit,
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
            .testTag("customer_card_${customer.id}")
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
                            .background(if (customer.creditBalance > 0) StatusAmberBg else BrandMintSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            customer.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (customer.creditBalance > 0) StatusAmber else BrandTealPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        if (customer.phone.isNotBlank()) {
                            Text(customer.phone, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (customer.creditBalance > 0) {
                        Text(
                            CurrencyUtils.formatLkr(customer.creditBalance),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = StatusAmber
                        )
                        Text(
                            "Limit: ${CurrencyUtils.formatLkr(customer.creditLimit)}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    } else {
                        Badge(containerColor = StatusGreenBg) {
                            Text("No Dues", color = StatusGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
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
                    if (customer.phone.isNotBlank()) {
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
                        onClick = onAddCredit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Credit", fontSize = 11.sp)
                    }

                    if (customer.creditBalance > 0) {
                        Button(
                            onClick = onPay,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Settle Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerLedgerDetailsDialog(
    customer: CustomerEntity,
    viewModel: PosViewModel,
    onEdit: () -> Unit,
    onPay: () -> Unit,
    onAddCredit: () -> Unit,
    onDelete: () -> Unit,
    onSendWhatsApp: () -> Unit,
    onDismiss: () -> Unit
) {
    var transactions by remember { mutableStateOf<List<CreditTransactionEntity>>(emptyList()) }

    LaunchedEffect(customer.id) {
        viewModel.getCustomerTransactions(customer.id).collectLatest {
            transactions = it
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        if (customer.phone.isNotBlank()) {
                            Text("Tel: ${customer.phone}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Balance summary
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (customer.creditBalance > 0) StatusAmberBg else BrandMintSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("CURRENT CREDIT BALANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            CurrencyUtils.formatLkr(customer.creditBalance),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (customer.creditBalance > 0) StatusAmber else BrandTealPrimary
                        )
                        Text("Credit Limit: ${CurrencyUtils.formatLkr(customer.creditLimit)}", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Action Buttons Row
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
                        onClick = onAddCredit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Debit")
                    }

                    if (customer.creditBalance > 0) {
                        Button(
                            onClick = onPay,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("Settle Pay", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (customer.creditBalance > 0 && customer.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSendWhatsApp,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send WhatsApp Balance Reminder", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = LightBorder)
                Spacer(modifier = Modifier.height(10.dp))

                // Ledger History Header
                Text("Ledger History & Statements (${transactions.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                if (transactions.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No ledger transactions recorded yet.", fontSize = 12.sp, color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(transactions) { tx ->
                            val isDebit = tx.type == "SALE_CREDIT" || tx.type == "ADJUSTMENT"
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
                                        Text(
                                            when (tx.type) {
                                                "SALE_CREDIT" -> "Credit Sale"
                                                "PAYMENT" -> "Payment Settlement"
                                                else -> "Manual Adjustment"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isDebit) StatusAmber else StatusGreen
                                        )
                                        if (tx.note.isNotBlank()) {
                                            Text(tx.note, fontSize = 11.sp, color = TextSecondary)
                                        }
                                        Text(CurrencyUtils.formatDateTime(tx.timestamp), fontSize = 10.sp, color = TextMuted)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${if (isDebit) "+" else "-"}${CurrencyUtils.formatLkr(tx.amount)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (isDebit) StatusAmber else StatusGreen
                                        )
                                        Text("Bal: ${CurrencyUtils.formatLkr(tx.balanceAfter)}", fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusRed),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Delete Customer Account", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AddEditCustomerDialog(
    customer: CustomerEntity?,
    onSave: (name: String, phone: String, email: String, address: String, limit: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var address by remember { mutableStateOf(customer?.address ?: "") }
    var limitText by remember { mutableStateOf(customer?.creditLimit?.toInt()?.toString() ?: "25000") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    if (customer == null) "ADD NEW CUSTOMER" else "EDIT CUSTOMER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Medium),
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
                    label = { Text("Customer Address") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Credit Limit (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val limit = limitText.toDoubleOrNull() ?: 25000.0
                            if (name.isNotBlank()) {
                                onSave(name.trim(), phone.trim(), "", address.trim(), limit, notes.trim())
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Customer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordCreditPaymentDialog(
    customer: CustomerEntity,
    onConfirm: (amount: Double, method: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(customer.creditBalance.toInt().toString()) }
    var method by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("RECORD SETTLEMENT / PAYMENT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Customer: ${customer.name}", fontSize = 13.sp, color = TextSecondary)
                Text("Outstanding Due: ${CurrencyUtils.formatLkr(customer.creditBalance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusAmber)

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
                    listOf("CASH", "CARD", "BANK").forEach { m ->
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
                    label = { Text("Note / Receipt # (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                onConfirm(amount, method, note.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirm Settlement", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddManualCreditDialog(
    customer: CustomerEntity,
    onConfirm: (amount: Double, reason: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Manual Store Credit") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ADD CREDIT CHARGE / DEBIT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Customer: ${customer.name}", fontSize = 13.sp, color = TextSecondary)
                Text("Current Due: ${CurrencyUtils.formatLkr(customer.creditBalance)}", fontSize = 13.sp, color = TextSecondary)

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Credit Amount to Add (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (e.g. Phone Order, Opening Dues)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Bill Reference (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                onConfirm(amount, reason.trim(), note.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add to Credit Ledger", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
