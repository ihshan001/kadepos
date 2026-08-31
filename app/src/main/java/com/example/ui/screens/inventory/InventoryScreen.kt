package com.example.ui.screens.inventory

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ProductEntity
import com.example.data.model.StockMovementEntity
import com.example.ui.components.BatchReorderDialog
import com.example.ui.components.LowStockRestockDialog
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: PosViewModel
) {
    val products by viewModel.products.collectAsState()
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()
    val stockMovements by viewModel.stockMovements.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Stock List & Alerts, 1: Stock Movements Log
    var searchQuery by remember { mutableStateOf("") }
    var movementFilter by remember { mutableStateOf("ALL") } // ALL, SALE, PURCHASE, ADJUST, RETURN

    var adjustingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var receivingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var restockProductForDialog by remember { mutableStateOf<ProductEntity?>(null) }
    var showBatchReorderDialog by remember { mutableStateOf(false) }

    val trackedProducts = remember(products) { products.filter { it.isTracked } }
    val totalInventoryCost = remember(trackedProducts) {
        trackedProducts.filter { it.currentStock > 0 }.sumOf { it.costPrice * it.currentStock }
    }
    val totalRetailValue = remember(trackedProducts) {
        trackedProducts.filter { it.currentStock > 0 }.sumOf { it.sellingPrice * it.currentStock }
    }
    val outOfStockCount = remember(trackedProducts) {
        trackedProducts.count { it.currentStock <= 0 }
    }

    val filteredTrackedProducts = remember(trackedProducts, searchQuery) {
        if (searchQuery.isBlank()) trackedProducts
        else trackedProducts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.barcode.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredMovements = remember(stockMovements, movementFilter) {
        if (movementFilter == "ALL") stockMovements
        else stockMovements.filter { it.type.contains(movementFilter, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Inventory & Stock Control", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text("${trackedProducts.size} items monitored in warehouse", fontSize = 11.sp, color = TextSecondary)
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
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 1. Inventory Health & Valuation KPI Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().testTag("inventory_kpi_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL ASSET VALUATION (COST)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary)
                            Text(
                                CurrencyUtils.formatLkr(totalInventoryCost),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandTealPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EST. RETAIL VALUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(
                                CurrencyUtils.formatLkr(totalRetailValue),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                        }
                    }

                    if (lowStockProducts.isNotEmpty() || outOfStockCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = LightBorder)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (lowStockProducts.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = StatusAmberBg
                                    ) {
                                        Text(
                                            "${lowStockProducts.size} Low Stock",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (outOfStockCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = StatusRedBg
                                    ) {
                                        Text(
                                            "$outOfStockCount Out of Stock",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (lowStockProducts.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF25D366),
                                    modifier = Modifier.clickable { showBatchReorderDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Batch Reorder", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Segmented Tab Selector
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = LightSurface,
                contentColor = BrandTealPrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "📦 Stock On Hand (${trackedProducts.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "📋 Audit Movements (${stockMovements.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TAB 0: Stock On Hand
            if (selectedTab == 0) {
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter tracked stock by name, barcode, category...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("inventory_search_bar"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        focusedContainerColor = LightSurface,
                        unfocusedContainerColor = LightSurface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredTrackedProducts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No tracked products match criteria", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredTrackedProducts,
                            key = { it.id }
                        ) { product ->
                            StockItemCard(
                                product = product,
                                onReceive = { receivingProduct = product },
                                onAdjust = { adjustingProduct = product },
                                onReorder = { restockProductForDialog = product },
                                onQuickStep = { delta ->
                                    val newCount = (product.currentStock + delta).coerceAtLeast(0.0)
                                    viewModel.adjustStock(product.id, newCount, "Quick Count", "Direct step adjustment")
                                }
                            )
                        }
                    }
                }
            } else {
                // TAB 1: Audit Movements
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf(
                        "ALL" to "All History",
                        "SALE" to "🛒 Sales",
                        "PURCHASE" to "📦 Intake / PO",
                        "ADJUST" to "⚖️ Adjustments",
                        "RETURN" to "↩️ Returns"
                    )
                    items(filters) { (key, label) ->
                        val isSel = movementFilter == key
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSel) BrandTealPrimary else LightSurface,
                            border = if (isSel) null else CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.clickable { movementFilter = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredMovements.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No stock movement audit records found", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredMovements) { mov ->
                            StockMovementCard(movement = mov)
                        }
                    }
                }
            }
        }
    }

    if (adjustingProduct != null) {
        StockAdjustmentDialog(
            product = adjustingProduct!!,
            onConfirm = { newCount, reason, note ->
                viewModel.adjustStock(adjustingProduct!!.id, newCount, reason, note)
                adjustingProduct = null
            },
            onDismiss = { adjustingProduct = null }
        )
    }

    if (receivingProduct != null) {
        ReceiveStockDialog(
            product = receivingProduct!!,
            onConfirm = { qty, cost, supplier ->
                viewModel.receiveStockDirect(receivingProduct!!.id, qty, cost, supplier)
                receivingProduct = null
            },
            onDismiss = { receivingProduct = null }
        )
    }

    if (restockProductForDialog != null) {
        LowStockRestockDialog(
            initialProduct = restockProductForDialog!!,
            lowStockList = lowStockProducts,
            suppliers = suppliers,
            profile = profile,
            onReceiveStock = { prodId, qty, cost, supName ->
                viewModel.receiveStockDirect(prodId, qty, cost, supName)
            },
            onOpenBatchReorder = {
                restockProductForDialog = null
                showBatchReorderDialog = true
            },
            onDismiss = { restockProductForDialog = null }
        )
    }

    if (showBatchReorderDialog && lowStockProducts.isNotEmpty()) {
        BatchReorderDialog(
            lowStockList = lowStockProducts,
            suppliers = suppliers,
            profile = profile,
            onBulkReceiveStock = { items, supName ->
                viewModel.receiveBatchStockDirect(items, supName)
            },
            onDismiss = { showBatchReorderDialog = false }
        )
    }
}

@Composable
fun StockItemCard(
    product: ProductEntity,
    onReceive: () -> Unit,
    onAdjust: () -> Unit,
    onReorder: () -> Unit,
    onQuickStep: (delta: Double) -> Unit
) {
    val isLow = product.currentStock <= product.lowStockThreshold
    val isOut = product.currentStock <= 0
    val itemValuation = product.currentStock * product.costPrice

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth().testTag("stock_item_${product.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            product.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        if (isOut) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = StatusRedBg) {
                                Text("OUT", color = StatusRed, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        } else if (isLow) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = StatusAmberBg) {
                                Text("LOW", color = StatusAmber, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${product.category} • Cost: ${CurrencyUtils.formatLkr(product.costPrice)} • Val: ${CurrencyUtils.formatLkr(itemValuation)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // Current On-Hand Count Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isOut -> StatusRedBg
                        isLow -> StatusAmberBg
                        else -> BrandMintSurface
                    }
                ) {
                    Text(
                        text = "${product.currentStock.toInt()} ${product.unit}",
                        color = when {
                            isOut -> StatusRed
                            isLow -> StatusAmber
                            else -> BrandTealPrimary
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = LightBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Row with Step + / - and Modal Triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick +/- 1 Step
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { onQuickStep(-1.0) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("-1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = { onQuickStep(1.0) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("+1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Modal Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isLow) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF25D366),
                            modifier = Modifier.clickable(onClick = onReorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reorder", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onReceive,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp), tint = BrandTealPrimary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("+ Intake", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandTealPrimary)
                    }

                    OutlinedButton(
                        onClick = onAdjust,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Audit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StockMovementCard(movement: StockMovementEntity) {
    val isPos = movement.changeQty > 0
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isPos) StatusGreenBg else StatusRedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPos) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isPos) StatusGreen else StatusRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(movement.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                    Text(
                        "${movement.type} • ${CurrencyUtils.formatDateTime(movement.timestamp)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (movement.reason.isNotBlank()) {
                        Text(movement.reason, fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (isPos) "+" else "") + "${movement.changeQty.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (isPos) StatusGreen else StatusRed
                )
                Text("Bal: ${movement.stockAfter.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            }
        }
    }
}

@Composable
fun StockAdjustmentDialog(
    product: ProductEntity,
    onConfirm: (newCount: Double, reason: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCountText by remember { mutableStateOf(product.currentStock.toInt().toString()) }
    var reason by remember { mutableStateOf("Count Audit") }
    var note by remember { mutableStateOf("") }

    val currentCount = product.currentStock
    val newCount = newCountText.toDoubleOrNull() ?: currentCount
    val delta = newCount - currentCount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PHYSICAL STOCK AUDIT", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandTealPrimary)
                        Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Before -> After Box
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("System Stock", fontSize = 10.sp, color = TextSecondary)
                            Text("${currentCount.toInt()} ${product.unit}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Adjustment Delta", fontSize = 10.sp, color = TextSecondary)
                            Text(
                                (if (delta >= 0) "+$delta" else "$delta") + " ${product.unit}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = if (delta >= 0) StatusGreen else StatusRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("New System Count", fontSize = 10.sp, color = TextSecondary)
                            Text("${newCount.toInt()} ${product.unit}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandTealPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newCountText,
                    onValueChange = { newCountText = it },
                    label = { Text("Actual Physical Stock Count") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandTealPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Audit Reason", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Count Audit", "Damaged Goods", "Expired / Spoiled", "Found Extra").forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r, fontSize = 10.sp, fontWeight = if (reason == r) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Audit Note (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandTealPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val count = newCountText.toDoubleOrNull() ?: currentCount
                        onConfirm(count, reason, note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("SAVE ADJUSTED COUNT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ReceiveStockDialog(
    product: ProductEntity,
    onConfirm: (qty: Double, unitCost: Double, supplierName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember { mutableStateOf("10") }
    var costText by remember { mutableStateOf(if (product.costPrice > 0) product.costPrice.toInt().toString() else "") }
    var supplier by remember { mutableStateOf("") }

    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val cost = costText.toDoubleOrNull() ?: product.costPrice
    val totalExpense = qty * cost

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("RECEIVE GOODS / INTAKE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandTealPrimary)
                        Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 25, 50, 100).forEach { amt ->
                        OutlinedButton(
                            onClick = { qtyText = amt.toString() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(30.dp)
                        ) {
                            Text("+$amt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantity to Add (+)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandTealPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Unit Cost (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandTealPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier / Vendor Name") },
                    placeholder = { Text("e.g. Colombo Wholesale Agency", color = TextMuted) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandTealPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Intake Value:", fontSize = 12.sp, color = TextSecondary)
                    Text(CurrencyUtils.formatLkr(totalExpense), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = BrandTealPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (qty > 0) {
                            onConfirm(qty, cost, supplier.ifBlank { "Direct Intake" })
                        }
                    },
                    enabled = qty > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONFIRM STOCK INTAKE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
