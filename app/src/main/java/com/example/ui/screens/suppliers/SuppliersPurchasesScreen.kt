package com.example.ui.screens.suppliers

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Permission
import com.example.data.model.PurchaseEntity
import com.example.data.model.SupplierEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel

/**
 * Suppliers — "who I buy from, and what I still owe them".
 *
 * The old version made you build a purchase order with line items before you
 * could record anything. Shop owners get a handwritten invoice and a delivery;
 * they want to note the total and how much they paid. That is now the whole
 * flow, and it mirrors the credit book exactly, in reverse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersPurchasesScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val permissions by viewModel.permissions.collectAsState()
    if (permissions.cannot(Permission.MANAGE_SUPPLIERS)) {
        LockedScreenNotice(
            message = permissions.denialMessage(Permission.MANAGE_SUPPLIERS),
            onBack = onBack
        )
        return
    }

    val suppliers by viewModel.suppliers.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val context = LocalContext.current

    var openSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var payingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var billFor by remember { mutableStateOf<SupplierEntity?>(null) }
    var showAddSupplier by remember { mutableStateOf(false) }

    val owed = remember(suppliers) {
        suppliers.filter { it.outstandingBalance > 0.0 }.sortedByDescending { it.outstandingBalance }
    }
    val clear = remember(suppliers) {
        suppliers.filter { it.outstandingBalance <= 0.0 }.sortedBy { it.name.lowercase() }
    }
    val totalOwed = remember(suppliers) {
        suppliers.sumOf { it.outstandingBalance.coerceAtLeast(0.0) }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Suppliers", fontWeight = FontWeight.Bold) },
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
                onClick = { showAddSupplier = true },
                containerColor = BrandTealPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_supplier_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New supplier", fontWeight = FontWeight.Bold)
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
                    label = "YOU OWE SUPPLIERS",
                    amount = totalOwed,
                    caption = when (owed.size) {
                        0 -> "All your suppliers are paid"
                        1 -> "1 supplier waiting to be paid"
                        else -> "${owed.size} suppliers waiting to be paid"
                    },
                    accent = StatusBlue
                )
            }

            if (suppliers.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                    EmptyState(
                        icon = Icons.Default.LocalShipping,
                        title = "No suppliers yet",
                        message = "Add the people you buy stock from. You will see what you owe each of them here."
                    )
                }
            }

            if (owed.isNotEmpty()) {
                item { SectionLabel("To pay") }
                items(owed, key = { it.id }) { supplier ->
                    MoneyPersonRow(
                        name = supplier.name,
                        detail = supplier.phone.ifBlank { "No phone saved" },
                        amount = supplier.outstandingBalance,
                        amountLabel = "you owe",
                        isSettled = false,
                        initialsColor = StatusBlue,
                        onClick = { openSupplier = supplier },
                        trailingAction = {
                            FilledTonalButton(
                                onClick = { payingSupplier = supplier },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = StatusBlueBg,
                                    contentColor = StatusBlue
                                ),
                                modifier = Modifier.testTag("pay_${supplier.id}")
                            ) {
                                Text("Pay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                }
            }

            if (clear.isNotEmpty()) {
                item { SectionLabel("Nothing owing") }
                items(clear, key = { it.id }) { supplier ->
                    MoneyPersonRow(
                        name = supplier.name,
                        detail = supplier.phone.ifBlank { "No phone saved" },
                        amount = 0.0,
                        amountLabel = "",
                        isSettled = true,
                        initialsColor = StatusGreen,
                        onClick = { openSupplier = supplier }
                    )
                }
            }

            if (purchases.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionLabel("Recent deliveries")
                }
                items(purchases.take(15), key = { it.id }) { purchase ->
                    DeliveryRow(purchase)
                }
            }
        }
    }

    // ----- Pay a supplier -----
    payingSupplier?.let { supplier ->
        val oldest = purchases
            .filter { it.supplierId == supplier.id && it.dueAmount > 0.0 }
            .minByOrNull { it.timestamp }
        MoneySheet(
            title = "Pay ${supplier.name}",
            subtitle = "You owe ${CurrencyUtils.formatLkr(supplier.outstandingBalance)}",
            confirmLabel = "Save payment",
            suggestedAmount = supplier.outstandingBalance,
            suggestedLabel = "All of it",
            maxAmount = supplier.outstandingBalance,
            accent = StatusBlue,
            onConfirm = { amount ->
                if (oldest != null) {
                    viewModel.settlePurchaseDue(oldest.id, amount, "CASH", "")
                } else {
                    viewModel.showMessage("No unpaid bill found for ${supplier.name}")
                }
                payingSupplier = null
            },
            onDismiss = { payingSupplier = null }
        )
    }

    // ----- Record a delivery -----
    billFor?.let { supplier ->
        SupplierBillSheet(
            supplier = supplier,
            onSave = { total, paidNow ->
                viewModel.recordSupplierBill(supplier, total, paidNow)
                billFor = null
            },
            onDismiss = { billFor = null }
        )
    }

    openSupplier?.let { supplier ->
        val live = suppliers.firstOrNull { it.id == supplier.id } ?: supplier
        SupplierSheet(
            supplier = live,
            deliveries = purchases.filter { it.supplierId == live.id },
            onPay = { payingSupplier = live; openSupplier = null },
            onNewBill = { billFor = live; openSupplier = null },
            onCall = {
                if (live.phone.isBlank()) {
                    viewModel.showMessage("No phone saved for ${live.name}")
                } else {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${live.phone}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { viewModel.showMessage("Could not open the dialler") }
                }
            },
            onDelete = {
                viewModel.deleteSupplier(live.id)
                openSupplier = null
            },
            onDismiss = { openSupplier = null }
        )
    }

    if (showAddSupplier) {
        AddSupplierSheet(
            onSave = { name, phone ->
                viewModel.saveSupplier(0, name, "", phone, "", "", "")
                showAddSupplier = false
            },
            onDismiss = { showAddSupplier = false }
        )
    }
}

@Composable
private fun DeliveryRow(purchase: PurchaseEntity) {
    val unpaid = purchase.dueAmount > 0.0
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LightSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalShipping,
                contentDescription = null,
                tint = if (unpaid) StatusBlue else StatusGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    purchase.supplierName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    CurrencyUtils.formatDateOnly(purchase.timestamp),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyUtils.formatLkr(purchase.totalAmount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    if (unpaid) "${CurrencyUtils.formatLkr(purchase.dueAmount)} still due" else "paid",
                    fontSize = 10.sp,
                    color = if (unpaid) StatusBlue else StatusGreen
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierSheet(
    supplier: SupplierEntity,
    deliveries: List<PurchaseEntity>,
    onPay: () -> Unit,
    onNewBill: () -> Unit,
    onCall: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
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
                        .background(StatusBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        supplier.name.take(1).uppercase(),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusBlue
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(supplier.name, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text(
                        supplier.phone.ifBlank { "No phone saved" },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                if (supplier.phone.isNotBlank()) {
                    IconButton(onClick = onCall) {
                        Icon(Icons.Default.Phone, contentDescription = "Call", tint = BrandTealPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (supplier.outstandingBalance > 0) StatusBlueBg else StatusGreenBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (supplier.outstandingBalance > 0) "You owe" else "All settled",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (supplier.outstandingBalance > 0) StatusBlue else StatusGreen
                    )
                    Text(
                        CurrencyUtils.formatLkr(supplier.outstandingBalance.coerceAtLeast(0.0)),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (supplier.outstandingBalance > 0) StatusBlue else StatusGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (supplier.outstandingBalance > 0) {
                    Button(
                        onClick = onPay,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Pay them", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onNewBill,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("New delivery", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Deliveries")

            if (deliveries.isEmpty()) {
                Text("Nothing recorded yet.", fontSize = 13.sp, color = TextMuted)
            } else {
                deliveries.sortedByDescending { it.timestamp }.take(10).forEach { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                CurrencyUtils.formatDateOnly(d.timestamp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (d.dueAmount > 0) {
                                Text(
                                    "${CurrencyUtils.formatLkr(d.dueAmount)} still due",
                                    fontSize = 11.sp,
                                    color = StatusBlue
                                )
                            } else {
                                Text("Paid in full", fontSize = 11.sp, color = StatusGreen)
                            }
                        }
                        Text(
                            CurrencyUtils.formatLkr(d.totalAmount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    HorizontalDivider(color = LightBorder)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (confirmDelete) {
                Surface(shape = RoundedCornerShape(12.dp), color = StatusRedBg, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Remove ${supplier.name}?",
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
                    Text("Remove this supplier", color = StatusRed, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Record a delivery: the bill total, and what you handed over today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierBillSheet(
    supplier: SupplierEntity,
    onSave: (total: Double, paidNow: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var totalText by remember { mutableStateOf("") }
    var paidText by remember { mutableStateOf("") }
    val total = totalText.toDoubleOrNull() ?: 0.0
    val paid = (paidText.toDoubleOrNull() ?: 0.0).coerceAtMost(total)
    val due = (total - paid).coerceAtLeast(0.0)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Delivery from ${supplier.name}",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                "Enter the invoice total and how much you paid today.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Bill total (Rs.)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bill_total_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = paidText,
                onValueChange = { paidText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Paid today (Rs.)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { paidText = totalText }) { Text("Paid in full") }
                TextButton(onClick = { paidText = "0" }) { Text("Paying later") }
            }

            if (total > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (due > 0) StatusBlueBg else StatusGreenBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (due > 0) {
                            "You will still owe ${CurrencyUtils.formatLkr(due)}"
                        } else {
                            "Nothing left to pay"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (due > 0) StatusBlue else StatusGreen,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onSave(total, paid) },
                enabled = total > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_bill")
            ) {
                Text("Save delivery", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSupplierSheet(
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
            Text("New supplier", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(
                "The company or person you buy stock from.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Supplier name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("supplier_name_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSave(name.trim(), phone.trim()) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_supplier")
            ) {
                Text("Save supplier", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
