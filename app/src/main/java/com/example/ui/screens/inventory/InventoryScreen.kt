package com.example.ui.screens.inventory

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Permission
import com.example.data.model.ProductEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel
import kotlin.math.roundToInt

/**
 * Stock — "what am I about to run out of".
 *
 * The old screen led with total asset valuation and a stock-movement audit
 * trail. A shop owner does not open this to value their business; they open it
 * to find out what to buy. So the list is ordered by urgency: out of stock
 * first, running low next, and everything that is fine is collapsed away.
 *
 * Two actions only: "Got more" (a delivery arrived) and "Recount" (the shelf
 * disagrees with the phone).
 */
private enum class StockFilter(val label: String) {
    NEEDS_ATTENTION("Need to buy"),
    ALL("Everything")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: PosViewModel) {
    val permissions by viewModel.permissions.collectAsState()
    if (permissions.cannot(Permission.MANAGE_INVENTORY)) {
        LockedScreenNotice(message = permissions.denialMessage(Permission.MANAGE_INVENTORY))
        return
    }

    val products by viewModel.products.collectAsState()
    val stockMovements by viewModel.stockMovements.collectAsState()
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StockFilter.NEEDS_ATTENTION) }
    var receiving by remember { mutableStateOf<ProductEntity?>(null) }
    var recounting by remember { mutableStateOf<ProductEntity?>(null) }

    val tracked = remember(products) { products.filter { it.isTracked && !it.isArchived } }

    val outOfStock = remember(tracked, search) {
        tracked.filter { it.currentStock <= 0.0 }.filter { it.matches(search) }
            .sortedBy { it.name.lowercase() }
    }
    val runningLow = remember(tracked, search) {
        tracked.filter { it.currentStock > 0.0 && it.currentStock <= it.lowStockThreshold }
            .filter { it.matches(search) }
            .sortedBy { it.currentStock }
    }
    val healthy = remember(tracked, search) {
        tracked.filter { it.currentStock > it.lowStockThreshold }.filter { it.matches(search) }
            .sortedBy { it.name.lowercase() }
    }
    val recentStock = remember(stockMovements) {
        stockMovements
            .filter { it.type == "PURCHASE" || it.type == "INITIAL" || it.type.startsWith("ADJUST_") }
            .take(6)
    }
    val needsAttention = outOfStock.size + runningLow.size

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Stock", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        }
    ) { padding ->
        if (tracked.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Inventory2,
                    title = "Nothing to count yet",
                    message = "Items you choose to count stock for will appear here, so you can see what is running out."
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            // The one sentence that matters.
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (needsAttention == 0) StatusGreen else StatusAmber,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (needsAttention == 0) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                when {
                                    needsAttention == 0 -> "Stock looks good"
                                    needsAttention == 1 -> "1 item needs buying"
                                    else -> "$needsAttention items need buying"
                                },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                if (outOfStock.isEmpty()) {
                                    "${tracked.size} items counted"
                                } else {
                                    "${outOfStock.size} finished completely"
                                },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StockFilter.entries.toList(), key = { it.name }) { option ->
                        val selected = filter == option
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) BrandGoldPrimary else LightSurface,
                            border = BorderStroke(1.dp, if (selected) BrandGoldPrimary else LightBorder),
                            modifier = Modifier
                                .clickable { filter = option }
                                .testTag("stock_filter_${option.name}")
                        ) {
                            Text(
                                if (option == StockFilter.NEEDS_ATTENTION && needsAttention > 0) {
                                    "${option.label} ($needsAttention)"
                                } else {
                                    option.label
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) BrandOnGold else TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            if (recentStock.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        border = BorderStroke(1.dp, LightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Recently stocked", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            recentStock.forEach { movement ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(movement.productName, fontSize = 12.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                                    Text(
                                        CurrencyUtils.formatDateOnly(movement.timestamp),
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        "+${movement.changeQty.clean()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (tracked.size > 6) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search an item", fontSize = 14.sp) },
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

            if (outOfStock.isNotEmpty()) {
                item { SectionLabel("Finished") }
                items(outOfStock, key = { it.id }) { product ->
                    StockRow(product, StockLevel.OUT, { receiving = product }, { recounting = product })
                }
            }

            if (runningLow.isNotEmpty()) {
                item { SectionLabel("Running low") }
                items(runningLow, key = { it.id }) { product ->
                    StockRow(product, StockLevel.LOW, { receiving = product }, { recounting = product })
                }
            }

            if (filter == StockFilter.ALL && healthy.isNotEmpty()) {
                item { SectionLabel("Plenty left") }
                items(healthy, key = { it.id }) { product ->
                    StockRow(product, StockLevel.OK, { receiving = product }, { recounting = product })
                }
            }

            if (filter == StockFilter.NEEDS_ATTENTION && needsAttention == 0) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "Nothing to buy",
                        message = "Every item you count has enough left. Tap Everything to see the full list."
                    )
                }
            }
        }
    }

    receiving?.let { product ->
        ReceiveStockSheet(
            product = product,
            onConfirm = { qty, cost ->
                viewModel.receiveStockDirect(product.id, qty, cost, "")
                receiving = null
            },
            onDismiss = { receiving = null }
        )
    }

    recounting?.let { product ->
        RecountSheet(
            product = product,
            onConfirm = { actual ->
                viewModel.adjustStock(product.id, actual, "Recount", "Counted on the shelf")
                recounting = null
            },
            onDismiss = { recounting = null }
        )
    }
}

private fun ProductEntity.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return name.lowercase().contains(q) ||
        barcode.contains(q) ||
        category.lowercase().contains(q)
}

private enum class StockLevel { OUT, LOW, OK }

@Composable
private fun StockRow(
    product: ProductEntity,
    level: StockLevel,
    onReceive: () -> Unit,
    onRecount: () -> Unit
) {
    val (tint, bg, word) = when (level) {
        StockLevel.OUT -> Triple(StatusRed, StatusRedBg, "Finished")
        StockLevel.LOW -> Triple(StatusAmber, StatusAmberBg, "Only ${product.currentStock.clean()} left")
        StockLevel.OK -> Triple(StatusGreen, StatusGreenBg, "${product.currentStock.clean()} ${product.unit}")
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = LightSurface,
        border = BorderStroke(1.dp, LightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        product.currentStock.clean(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tint
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(word, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onReceive,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGoldPrimary),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("receive_${product.id}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Got more", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onRecount,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recount_${product.id}")
                ) {
                    Text("Recount", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A delivery arrived: how many, and what it cost. Cost is optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveStockSheet(
    product: ProductEntity,
    onConfirm: (qty: Double, unitCost: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var qty by remember { mutableStateOf(0) }
    var costText by remember { mutableStateOf(if (product.costPrice > 0) product.costPrice.clean() else "") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Got more stock", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(
                product.name,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "You have ${product.currentStock.clean()} ${product.unit} now",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            Stepper(
                value = qty,
                onChange = { qty = it.coerceAtLeast(0) },
                testTag = "receive_qty"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 12, 24, 50).forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BrandGoldSurface,
                        modifier = Modifier.clickable { qty = preset }
                    ) {
                        Text(
                            "+$preset",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandGoldPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Cost for one (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandGoldPrimary)
            )

            if (qty > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandGoldSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "You will have ${(product.currentStock + qty).clean()} ${product.unit}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandGoldPrimary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onConfirm(qty.toDouble(), costText.toDoubleOrNull() ?: product.costPrice) },
                enabled = qty > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_receive")
            ) {
                Text("Add to stock", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The shelf disagrees with the phone. Type what is really there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecountSheet(
    product: ProductEntity,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var actual by remember { mutableStateOf(product.currentStock.roundToInt()) }
    val diff = actual - product.currentStock

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Recount", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(product.name, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            Text(
                "Count what is actually on the shelf",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            Stepper(value = actual, onChange = { actual = it.coerceAtLeast(0) }, testTag = "recount_qty")

            Spacer(modifier = Modifier.height(16.dp))

            if (diff != 0.0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (diff < 0) StatusRedBg else StatusGreenBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (diff < 0) {
                            "${(-diff).clean()} ${product.unit} fewer than the phone thought"
                        } else {
                            "${diff.clean()} ${product.unit} more than the phone thought"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (diff < 0) StatusRed else StatusGreen,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Button(
                onClick = { onConfirm(actual.toDouble()) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_recount")
            ) {
                Text("Save count", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Big plus/minus with the number between them. Works with one thumb. */
@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, testTag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = LightSurfaceVariant)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Less", tint = TextPrimary)
        }
        Text(
            "$value",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            modifier = Modifier
                .widthIn(min = 110.dp)
                .testTag(testTag),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = BrandGoldSurface)
        ) {
            Icon(Icons.Default.Add, contentDescription = "More", tint = BrandGoldPrimary)
        }
    }
}

/** 12.0 -> "12", 12.5 -> "12.5". Shop owners do not want trailing zeroes. */
private fun Double.clean(): String =
    if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)
