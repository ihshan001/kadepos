package com.example.ui.screens.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.BorderStroke
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
import com.example.data.model.CustomerEntity
import com.example.data.model.Permission
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel

/**
 * The credit book — "who owes me money".
 *
 * Rewritten from a ledger UI into a to-do list. A shop owner opening this
 * screen has exactly one question, and the answer is the first thing they see.
 * Everyone who owes is at the top, biggest debt first. Settling is two taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersCreditScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val permissions by viewModel.permissions.collectAsState()
    if (permissions.cannot(Permission.MANAGE_CUSTOMERS)) {
        LockedScreenNotice(
            message = permissions.denialMessage(Permission.MANAGE_CUSTOMERS),
            onBack = onBack
        )
        return
    }

    val customers by viewModel.customers.collectAsState()
    val context = LocalContext.current

    var search by remember { mutableStateOf("") }
    var openCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var settling by remember { mutableStateOf<CustomerEntity?>(null) }
    var lending by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAddCustomer by remember { mutableStateOf(false) }

    val owing = remember(customers, search) {
        customers
            .filter { it.creditBalance > 0.0 }
            .filter { it.matches(search) }
            .sortedByDescending { it.creditBalance }
    }
    val settled = remember(customers, search) {
        customers
            .filter { it.creditBalance <= 0.0 }
            .filter { it.matches(search) }
            .sortedBy { it.name.lowercase() }
    }
    val totalOwed = remember(customers) { customers.sumOf { it.creditBalance.coerceAtLeast(0.0) } }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Credit book", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCustomer = true },
                containerColor = BrandGoldPrimary,
                contentColor = BrandOnGold,
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New customer", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            item {
                MoneyHeadline(
                    label = "PEOPLE OWE YOU",
                    amount = totalOwed,
                    caption = when (owing.size) {
                        0 -> "Everyone has paid up"
                        1 -> "1 customer to collect from"
                        else -> "${owing.size} customers to collect from"
                    },
                    accent = StatusAmber
                )
            }

            if (customers.size > 4) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search a name or phone", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = LightSurface,
                            unfocusedContainerColor = LightSurface,
                            focusedBorderColor = BrandGoldPrimary
                        )
                    )
                }
            }

            if (customers.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                    EmptyState(
                        icon = Icons.Default.Book,
                        title = "No customers yet",
                        message = "Add a customer when someone asks to pay later. You will see what they owe here."
                    )
                }
            }

            if (owing.isNotEmpty()) {
                item { SectionLabel("To collect") }
                items(owing, key = { it.id }) { customer ->
                    MoneyPersonRow(
                        name = customer.name,
                        detail = customer.phone.ifBlank { "No phone saved" },
                        amount = customer.creditBalance,
                        amountLabel = "owes you",
                        isSettled = false,
                        initialsColor = StatusAmber,
                        onClick = { openCustomer = customer },
                        trailingAction = {
                            FilledTonalButton(
                                onClick = { settling = customer },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = StatusGreenBg,
                                    contentColor = StatusGreen
                                ),
                                modifier = Modifier.testTag("settle_${customer.id}")
                            ) {
                                Text("Paid", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                }
            }

            if (settled.isNotEmpty()) {
                item { SectionLabel("Nothing owing") }
                items(settled, key = { it.id }) { customer ->
                    MoneyPersonRow(
                        name = customer.name,
                        detail = customer.phone.ifBlank { "No phone saved" },
                        amount = 0.0,
                        amountLabel = "",
                        isSettled = true,
                        initialsColor = StatusGreen,
                        onClick = { openCustomer = customer }
                    )
                }
            }
        }
    }

    // ----- Settle: they handed over money -----
    settling?.let { customer ->
        MoneySheet(
            title = "${customer.name} paid you",
            subtitle = "They owe ${CurrencyUtils.formatLkr(customer.creditBalance)}",
            confirmLabel = "Save payment",
            suggestedAmount = customer.creditBalance,
            suggestedLabel = "All of it",
            maxAmount = customer.creditBalance,
            accent = StatusGreen,
            onConfirm = { amount ->
                viewModel.recordCustomerCreditPayment(customer.id, amount, "CASH", "")
                settling = null
            },
            onDismiss = { settling = null }
        )
    }

    // ----- Lend: goods taken without paying -----
    lending?.let { customer ->
        MoneySheet(
            title = "${customer.name} took goods",
            subtitle = "Add this to what they owe",
            confirmLabel = "Add to their book",
            accent = StatusAmber,
            onConfirm = { amount ->
                viewModel.recordManualCustomerCredit(customer.id, amount, "Goods taken", "")
                lending = null
            },
            onDismiss = { lending = null }
        )
    }

    openCustomer?.let { customer ->
        val live = customers.firstOrNull { it.id == customer.id } ?: customer
        CustomerSheet(
            customer = live,
            viewModel = viewModel,
            onSettle = { settling = live; openCustomer = null },
            onLend = { lending = live; openCustomer = null },
            onRemind = {
                val owed = CurrencyUtils.formatLkr(live.creditBalance)
                val text = "Hello ${live.name}, this is a friendly reminder that $owed is due. Thank you."
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(
                                "https://wa.me/${live.phone.filter(Char::isDigit)}?text=" + Uri.encode(text)
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }.onFailure { viewModel.showMessage("WhatsApp is not installed on this phone") }
            },
            onDelete = {
                viewModel.deleteCustomer(live.id)
                openCustomer = null
            },
            onDismiss = { openCustomer = null }
        )
    }

    if (showAddCustomer) {
        AddCustomerSheet(
            onSave = { name, phone ->
                viewModel.saveCustomer(0, name, phone, "", "", 0.0, "")
                showAddCustomer = false
            },
            onDismiss = { showAddCustomer = false }
        )
    }
}

private fun CustomerEntity.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return name.lowercase().contains(q) || phone.contains(q)
}

/** One customer: what they owe, and their history in sentences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerSheet(
    customer: CustomerEntity,
    viewModel: PosViewModel,
    onSettle: () -> Unit,
    onLend: () -> Unit,
    onRemind: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val transactions by viewModel
        .getCustomerTransactions(customer.id)
        .collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(BrandGoldSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        customer.name.take(1).uppercase(),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandGoldPrimary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(customer.name, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text(
                        customer.phone.ifBlank { "No phone saved" },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (customer.creditBalance > 0) StatusAmberBg else StatusGreenBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (customer.creditBalance > 0) "Owes you" else "All settled",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (customer.creditBalance > 0) StatusAmber else StatusGreen
                    )
                    Text(
                        CurrencyUtils.formatLkr(customer.creditBalance.coerceAtLeast(0.0)),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (customer.creditBalance > 0) StatusAmber else StatusGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (customer.creditBalance > 0) {
                    Button(
                        onClick = onSettle,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("They paid", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onLend,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Took goods", fontWeight = FontWeight.Bold)
                }
            }

            if (customer.phone.isNotBlank() && customer.creditBalance > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRemind,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send a reminder on WhatsApp")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("History")

            if (transactions.isEmpty()) {
                Text("Nothing recorded yet.", fontSize = 13.sp, color = TextMuted)
            } else {
                transactions.take(10).forEach { tx ->
                    val isPayment = tx.type.equals("PAYMENT", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isPayment) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = if (isPayment) StatusGreen else StatusAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isPayment) "Paid you" else "Took goods",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                CurrencyUtils.formatDateTime(tx.timestamp),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Text(
                            CurrencyUtils.formatLkr(tx.amount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPayment) StatusGreen else StatusAmber
                        )
                    }
                    HorizontalDivider(color = LightBorder)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (confirmDelete) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusRedBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Remove ${customer.name} and their history?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusRed
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onDelete,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Yes, remove") }
                            OutlinedButton(
                                onClick = { confirmDelete = false },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Keep") }
                        }
                    }
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Remove this customer", color = StatusRed, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Two fields. A customer is a name and a phone number; nothing else is required. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomerSheet(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("New customer", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(
                "Just a name is enough. Add a phone if you want to send reminders.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_name_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandGoldPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
                label = { Text("Phone (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandGoldPrimary)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSave(name.trim(), phone.trim()) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_customer")
            ) {
                Text("Save customer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
