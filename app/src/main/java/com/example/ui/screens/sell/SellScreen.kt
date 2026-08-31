package com.example.ui.screens.sell

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.Permission
import com.example.data.model.CustomerEntity
import com.example.data.model.HeldSaleEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.ui.components.BatchReorderDialog
import com.example.ui.components.HintCard
import com.example.ui.components.HintTone
import com.example.ui.components.LowStockRestockDialog
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.CartItem
import com.example.ui.viewmodel.PosViewModel

/** Chip labels that are not real product categories. */
private const val ALL_CATEGORY = "All"
private const val FAVOURITES_CATEGORY = "Favourites"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    viewModel: PosViewModel,
    profile: BusinessProfileEntity?,
    products: List<ProductEntity>,
    cart: List<CartItem>,
    selectedCustomer: CustomerEntity?,
    billDiscount: Double,
    billNote: String,
    onOpenMore: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var showQuickItemDialog by remember { mutableStateOf(false) }
    var showQuickSaleDialog by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showHoldDialog by remember { mutableStateOf(false) }
    var showHeldListDialog by remember { mutableStateOf(false) }
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var showShiftOverviewDialog by remember { mutableStateOf(false) }
    var editingCartItemIndex by remember { mutableStateOf<Int?>(null) }
    var unknownBarcode by remember { mutableStateOf<String?>(null) }

    val permissions by viewModel.permissions.collectAsState()

    val lastCompletedSale by viewModel.lastCompletedSale.collectAsState()
    val lastCompletedItems by viewModel.lastCompletedItems.collectAsState()
    val showSaleSuccessDialog by viewModel.showSaleSuccessDialog.collectAsState()
    val sales by viewModel.sales.collectAsState()
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()
    val heldSales by viewModel.heldSales.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()

    var productForLowStockRestock by remember { mutableStateOf<ProductEntity?>(null) }
    var showBatchReorderDialog by remember { mutableStateOf(false) }

    val todayDate = remember { CurrencyUtils.formatDateOnly(System.currentTimeMillis()) }
    val todaySalesList = remember(sales, todayDate) {
        sales.filter { CurrencyUtils.formatDateOnly(it.timestamp) == todayDate && it.status != "VOID" }
    }
    val todayTotalSales = remember(todaySalesList) { todaySalesList.sumOf { it.totalAmount } }
    val todayCashSales = remember(todaySalesList) { todaySalesList.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount } }
    val todayCreditSales = remember(todaySalesList) { todaySalesList.filter { it.paymentMethod == "CREDIT" }.sumOf { it.totalAmount } }
    val todayOrderCount = todaySalesList.size

    // Categories come from the shop's own products only, so a grocery never
    // sees pharmacy sections. Every one of them is listed — the row scrolls.
    val categories = remember(products) {
        buildList {
            add(ALL_CATEGORY)
            if (products.any { it.isFavourite }) add(FAVOURITES_CATEGORY)
            addAll(
                products.map { it.category.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            )
        }
    }

    val categoryListState = rememberLazyListState()

    // If the chosen category disappears (shop type changed, product deleted),
    // quietly fall back to All rather than showing an empty grid.
    LaunchedEffect(categories) {
        if (selectedCategory !in categories) selectedCategory = ALL_CATEGORY
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        if (searchQuery.isNotBlank()) {
            products.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.barcode.contains(searchQuery, ignoreCase = true) ||
                        it.sku.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        } else {
            when (selectedCategory) {
                ALL_CATEGORY -> products
                FAVOURITES_CATEGORY -> products.filter { it.isFavourite }
                // Strict match: a category shows its own items and nothing else.
                else -> products.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            }
        }
    }

    val subtotal = remember(cart) { cart.sumOf { it.lineTotal } }
    val totalAmount = remember(subtotal, billDiscount) { (subtotal - billDiscount).coerceAtLeast(0.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = profile?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${profile?.activeStaffName ?: "Staff"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Today's Sales Pill (Compact, opens full breakdown)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BrandMintSurface,
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { showShiftOverviewDialog = true }
                            .testTag("today_shift_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = "Today Sales", tint = BrandTealPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                CurrencyUtils.formatLkr(todayTotalSales),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandTealPrimary
                            )
                        }
                    }

                    // Low Stock Alert Pill (Only shown if low stock items exist)
                    if (lowStockProducts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = StatusRedBg,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { productForLowStockRestock = lowStockProducts.firstOrNull() }
                                .testTag("low_stock_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = "Low Stock Alert", tint = StatusRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "${lowStockProducts.size} Low",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusRed
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showQuickSaleDialog = true },
                        modifier = Modifier.testTag("quick_sale_icon_button")
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Quick Sale", tint = StatusAmber)
                    }

                    IconButton(
                        onClick = onOpenMore,
                        modifier = Modifier.testTag("menu_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
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
        ) {
            // Main Sales Canvas
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Search Bar with Barcode Scanner & Clear Buttons
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_product_input"),
                        placeholder = { Text("Search products, SKU, barcode...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { showBarcodeDialog = true },
                                    modifier = Modifier.testTag("barcode_scanner_button")
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode Scan", tint = BrandTealPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = LightSurface,
                            unfocusedContainerColor = LightSurface
                        )
                    )
                }

                // 2. Quick Action Shortcuts Row: [ ⚡ Quick Sale ] [ ➕ Quick Item ] [ 👤 Customer ] [ ⏸️ Parked ]
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StatusAmberBg,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showQuickSaleDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Sale", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandMintSurface,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showQuickItemDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandTealPrimary)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = LightSurface,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .weight(1.1f)
                                .clickable { showCustomerPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PersonOutline, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = selectedCustomer?.name?.take(10) ?: "Walk-in",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (heldSales.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEFF6FF),
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .clickable { showHeldListDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PauseCircleFilled, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${heldSales.size} Parked", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                }
                            }
                        }
                    }
                }

                // 3. Category chips.
                // A LazyRow, not a Row: every category the shop actually has is
                // reachable by swiping sideways, so the last chip is never cut
                // off or wrapped onto a second line.
                item {
                    LazyRow(
                        state = categoryListState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(categories, key = { it }) { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) BrandTealPrimary else LightSurface,
                                border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable {
                                        selectedCategory = cat
                                        searchQuery = ""
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Products Grid Section
                if (searchQuery.isNotBlank() && filteredProducts.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "No exact product found for \"$searchQuery\"",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Add and sell it directly as a quick item without interruption:",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                                )
                                Button(
                                    onClick = {
                                        viewModel.addQuickItemToCart(name = searchQuery, price = 100.0, quantity = 1.0)
                                        searchQuery = ""
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Sell \"$searchQuery\" (Rs. 100)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (cart.isEmpty()) 420.dp else 220.dp)
                        ) {
                            items(filteredProducts) { product ->
                                val cartQty = cart.find { it.productId == product.id }?.quantity ?: 0.0
                                ProductQuickCard(
                                    product = product,
                                    cartQty = cartQty,
                                    onAdd = { viewModel.addToCart(product) }
                                )
                            }
                        }
                    }
                }

                // 5. Current Bill Header & Controls
                item {
                    HorizontalDivider(color = LightBorder, modifier = Modifier.padding(vertical = 2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "CURRENT BILL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = if (cart.isNotEmpty()) BrandTealPrimary else TextMuted) {
                                Text("${cart.sumOf { it.quantity.toInt() }} items", color = Color.White, fontSize = 10.sp)
                            }
                        }

                        if (cart.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                AssistChip(
                                    onClick = { showDiscountDialog = true },
                                    label = { Text(if (billDiscount > 0) "-${CurrencyUtils.formatLkr(billDiscount)}" else "+ Disc", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                                AssistChip(
                                    onClick = { showHoldDialog = true },
                                    label = { Text("Hold", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                                TextButton(
                                    onClick = { viewModel.clearCart() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Clear", color = StatusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 6. Cart Items List
                if (cart.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LightSurface,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Cart is empty", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Tap any product above, scan barcode, or use Quick Item to sell.", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(cart) { index, item ->
                        CartItemRow(
                            item = item,
                            onQtyChange = { newQty -> viewModel.updateCartItemQuantity(index, newQty) },
                            onRemove = { viewModel.removeFromCart(index) },
                            onEdit = { editingCartItemIndex = index }
                        )
                    }
                }
            }

            // 7. Sticky Bottom Checkout Bar
            Surface(
                color = LightSurface,
                shadowElevation = 10.dp,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (billDiscount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal: ${CurrencyUtils.formatLkr(subtotal)}", fontSize = 11.sp, color = TextSecondary)
                            Text("Discount: -${CurrencyUtils.formatLkr(billDiscount)}", fontSize = 11.sp, color = StatusGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL AMOUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(
                                CurrencyUtils.formatLkr(totalAmount),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = BrandTealPrimary
                            )
                        }

                        Button(
                            onClick = { showCheckoutSheet = true },
                            enabled = cart.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("charge_button")
                        ) {
                            Text(
                                if (cart.isNotEmpty()) "CHARGE ${CurrencyUtils.formatLkr(totalAmount)}" else "CHARGE",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs & BottomSheets ---

    if (showShiftOverviewDialog) {
        TodayShiftOverviewDialog(
            todayDate = todayDate,
            totalSales = todayTotalSales,
            orderCount = todayOrderCount,
            cashSales = todayCashSales,
            creditSales = todayCreditSales,
            sales = sales,
            onDismiss = { showShiftOverviewDialog = false }
        )
    }

    if (showHeldListDialog) {
        HeldSalesPickerDialog(
            heldSales = heldSales,
            onResume = { held ->
                viewModel.resumeHeldSale(held)
                showHeldListDialog = false
            },
            onDismiss = { showHeldListDialog = false }
        )
    }

    if (showBarcodeDialog) {
        BarcodeEntryDialog(
            onBarcodeEntered = { barcode ->
                // Looks the code up in this shop's own catalogue. If it isn't
                // there we open Quick Item pre-filled instead of inventing a price.
                viewModel.addProductByBarcode(barcode) { missing ->
                    unknownBarcode = missing
                    showQuickItemDialog = true
                }
                showBarcodeDialog = false
            },
            onDismiss = { showBarcodeDialog = false }
        )
    }

    if (showQuickItemDialog) {
        QuickItemDialog(
            prefillBarcode = unknownBarcode,
            onAdd = { name, price, qty, disc, savePermanent ->
                viewModel.addQuickItemToCart(name, price, qty, disc)
                if (savePermanent) {
                    viewModel.saveProduct(
                        id = 0,
                        name = name,
                        sellingPrice = price,
                        costPrice = 0.0,
                        barcode = unknownBarcode.orEmpty(),
                        sku = "",
                        category = "Other",
                        unit = "Piece",
                        openingStock = 0.0,
                        lowStock = 0.0,
                        isTracked = false,
                        isFavourite = true
                    )
                }
                unknownBarcode = null
                showQuickItemDialog = false
            },
            onDismiss = {
                unknownBarcode = null
                showQuickItemDialog = false
            }
        )
    }

    if (showQuickSaleDialog) {
        QuickSaleDialog(
            onSell = { amount ->
                viewModel.addQuickSaleToCart(amount)
                showQuickSaleDialog = false
            },
            onDismiss = { showQuickSaleDialog = false }
        )
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = viewModel.customers.collectAsState().value,
            selected = selectedCustomer,
            onSelect = {
                viewModel.selectCustomer(it)
                showCustomerPicker = false
            },
            onAddNew = { name, phone ->
                viewModel.saveCustomer(0, name, phone, "", "", 0.0, "")
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    if (showDiscountDialog) {
        DiscountDialog(
            currentDiscount = billDiscount,
            subtotal = subtotal,
            onApply = {
                viewModel.setBillDiscount(it)
                showDiscountDialog = false
            },
            onDismiss = { showDiscountDialog = false }
        )
    }

    if (showHoldDialog) {
        HoldSaleDialog(
            totalAmount = totalAmount,
            onHold = { label ->
                viewModel.holdCurrentSale(label)
                showHoldDialog = false
            },
            onDismiss = { showHoldDialog = false }
        )
    }

    if (showCheckoutSheet) {
        CheckoutSheet(
            totalAmount = totalAmount,
            customer = selectedCustomer,
            onComplete = { method, cashReceived, cardAmt, creditAmt ->
                viewModel.completeSale(method, cashReceived, cardAmt, creditAmt)
                showCheckoutSheet = false
            },
            onDismiss = { showCheckoutSheet = false }
        )
    }

    if (showSaleSuccessDialog && lastCompletedSale != null) {
        SaleCompleteDialog(
            sale = lastCompletedSale!!,
            items = lastCompletedItems,
            profile = profile,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissSaleSuccess() }
        )
    }

    if (productForLowStockRestock != null) {
        LowStockRestockDialog(
            initialProduct = productForLowStockRestock!!,
            lowStockList = lowStockProducts,
            suppliers = suppliers,
            profile = profile,
            onReceiveStock = { prodId, qty, cost, supName ->
                viewModel.receiveStockDirect(prodId, qty, cost, supName)
            },
            onOpenBatchReorder = {
                productForLowStockRestock = null
                showBatchReorderDialog = true
            },
            onDismiss = { productForLowStockRestock = null }
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

// -------------------------------------------------------------------------------------
// Component Views
// -------------------------------------------------------------------------------------

@Composable
fun ProductQuickCard(
    product: ProductEntity,
    cartQty: Double = 0.0,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cartQty > 0) Color(0xFFF0FDF4) else LightSurface
        ),
        border = if (cartQty > 0) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusGreen)) else CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (cartQty > 0) StatusGreen else BrandMintSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = if (cartQty > 0) Color.White else BrandTealPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (cartQty > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusGreenBg
                    ) {
                        Text(
                            text = "✓ ${cartQty.toInt()} in cart",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    CurrencyUtils.formatLkr(product.sellingPrice),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = BrandTealPrimary
                )
                if (product.isTracked) {
                    val isLow = product.currentStock <= product.lowStockThreshold
                    Text(
                        "${product.currentStock.toInt()} ${product.unit}",
                        fontSize = 10.sp,
                        fontWeight = if (isLow) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLow) StatusAmber else TextSecondary
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Today's Shift & Sales Overview Modal Dialog
// -------------------------------------------------------------------------------------
@Composable
fun TodayShiftOverviewDialog(
    todayDate: String,
    totalSales: Double,
    orderCount: Int,
    cashSales: Double,
    creditSales: Double,
    sales: List<SaleEntity>,
    onDismiss: () -> Unit
) {
    var showChart by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TODAY'S SALES OVERVIEW", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextPrimary)
                        Text(todayDate, fontSize = 11.sp, color = TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Metric Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Sales", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text(
                                CurrencyUtils.formatLkr(totalSales),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = BrandTealPrimary
                            )
                            Text("$orderCount ${if (orderCount == 1) "bill" else "bills"}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Cash In", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text(
                                CurrencyUtils.formatLkr(cashSales),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text("Collected", fontSize = 10.sp, color = StatusGreen)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Credit/Due", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text(
                                CurrencyUtils.formatLkr(creditSales),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (creditSales > 0) StatusAmber else TextPrimary
                            )
                            Text("Receivable", fontSize = 10.sp, color = if (creditSales > 0) StatusAmber else TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Visual Growth Chart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showChart = !showChart },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (showChart) Icons.Default.ExpandLess else Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = BrandTealPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (showChart) "Hide Revenue Chart" else "View Revenue Growth Trajectory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTealPrimary
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showChart,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        com.example.ui.components.SalesTrendsChart(
                            sales = sales,
                            title = "Daily & Weekly Trajectory"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Held / Parked Bills Picker Dialog
// -------------------------------------------------------------------------------------
@Composable
fun HeldSalesPickerDialog(
    heldSales: List<HeldSaleEntity>,
    onResume: (HeldSaleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PARKED / HELD BILLS", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextPrimary)
                        Text("${heldSales.size} parked cart(s) waiting", fontSize = 12.sp, color = TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(heldSales.size) { idx ->
                        val held = heldSales[idx]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LightSurfaceVariant,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResume(held) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(held.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    Text(
                                        "Customer: ${held.customerName} • ${held.itemsCount} item(s)",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        CurrencyUtils.formatDateTime(held.timestamp),
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        CurrencyUtils.formatLkr(held.totalAmount),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = BrandTealPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BrandTealPrimary
                                    ) {
                                        Text(
                                            "RESUME",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onQtyChange: (Double) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(
                    CurrencyUtils.formatLkr(item.unitPrice) + if (item.discount > 0) " (-${CurrencyUtils.formatLkr(item.discount)})" else "",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Quantity stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LightSurfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { onQtyChange(item.quantity - 1.0) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${item.quantity.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = { onQtyChange(item.quantity + 1.0) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                CurrencyUtils.formatLkr(item.lineTotal),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}

// -------------------------------------------------------------------------------------
// Barcode Scanner Simulator Dialog
// -------------------------------------------------------------------------------------
@Composable
fun BarcodeEntryDialog(
    onBarcodeEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // Most shops use a cheap USB or Bluetooth scanner, which behaves exactly
    // like a keyboard and ends with Enter. Keeping this field focused means the
    // cashier can just scan, with no tapping at all.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

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
                    Text("Scan barcode", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    "Scan with your barcode reader, or type the number.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isLetterOrDigit() }
                        barcode = cleaned
                    },
                    label = { Text("Barcode") },
                    leadingIcon = {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BrandTealPrimary)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("barcode_input"),
                    textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { if (barcode.isNotBlank()) onBarcodeEntered(barcode) }
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (barcode.isNotBlank()) onBarcodeEntered(barcode) },
                    enabled = barcode.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Find item", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Quick Item Dialog
// -------------------------------------------------------------------------------------
@Composable
fun QuickItemDialog(
    prefillBarcode: String? = null,
    onAdd: (name: String, price: Double, qty: Double, discount: Double, saveProduct: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1.0) }
    var saveAsPermanent by remember { mutableStateOf(false) }

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
                    Text("QUICK ITEM", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (prefillBarcode != null) {
                    Text(
                        "Barcode $prefillBarcode is not in your items yet. Add it here.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                } else {
                    Text("What are you selling?", fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name / Description") },
                    placeholder = { Text("Item name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price (Rs.)") },
                    leadingIcon = { Text("Rs.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), color = Color.Black) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quantity", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (qty > 1) qty -= 1.0 }) {
                            Icon(Icons.Default.Remove, contentDescription = null)
                        }
                        Text("${qty.toInt()}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { qty += 1.0 }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = saveAsPermanent, onCheckedChange = { saveAsPermanent = it })
                    Text("Also save to my items", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val price = priceText.toDoubleOrNull() ?: 0.0
                        if (price > 0) {
                            onAdd(name.ifBlank { "Quick Item" }, price, qty, 0.0, saveAsPermanent)
                        }
                    },
                    enabled = priceText.toDoubleOrNull() != null,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("ADD TO BILL", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Quick Sale Dialog
// -------------------------------------------------------------------------------------
@Composable
fun QuickSaleDialog(
    onSell: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }

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
                    Text("QUICK SALE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Text("Sell immediately by total amount without tracking items.", fontSize = 12.sp, color = TextSecondary)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (Rs.)") },
                    placeholder = { Text("2,500") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("500", "1000", "2500", "5000").forEach { preset ->
                        OutlinedButton(
                            onClick = { amountText = preset },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(preset, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) onSell(amount)
                    },
                    enabled = amountText.toDoubleOrNull() != null,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("ADD TO BILL", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Customer Picker Dialog
// -------------------------------------------------------------------------------------
@Composable
fun CustomerPickerDialog(
    customers: List<CustomerEntity>,
    selected: CustomerEntity?,
    onSelect: (CustomerEntity?) -> Unit,
    onAddNew: (name: String, phone: String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val filtered = remember(customers, search) {
        if (search.isBlank()) customers else customers.filter {
            it.name.contains(search, ignoreCase = true) || it.phone.contains(search)
        }
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
                    Text("SELECT CUSTOMER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (!isAddingNew) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search name or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { onSelect(null) }) {
                            Text("Default (Walk-in)")
                        }
                        TextButton(onClick = { isAddingNew = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ New Customer")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered.size) { idx ->
                            val c = filtered[idx]
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected?.id == c.id) BrandMintSurface else LightSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(c) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(c.phone, fontSize = 12.sp, color = TextSecondary)
                                    }
                                    if (c.creditBalance > 0) {
                                        Badge(containerColor = StatusAmberBg) {
                                            Text("Due: ${CurrencyUtils.formatLkr(c.creditBalance)}", color = StatusAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Add customer without losing active cart", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Customer Name") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Medium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("077 123 4567") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Medium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (newName.isNotBlank()) onAddNew(newName, newPhone)
                        },
                        enabled = newName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save & Select Customer", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { isAddingNew = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to list")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Discount Dialog
// -------------------------------------------------------------------------------------
@Composable
fun DiscountDialog(
    currentDiscount: Double,
    subtotal: Double,
    onApply: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var discountText by remember { mutableStateOf(if (currentDiscount > 0) currentDiscount.toString() else "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("APPLY DISCOUNT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Subtotal: ${CurrencyUtils.formatLkr(subtotal)}", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Discount Amount (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50.0, 100.0, 200.0, 500.0).forEach { disc ->
                        OutlinedButton(
                            onClick = { discountText = disc.toInt().toString() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Rs.${disc.toInt()}", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onApply(0.0) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Remove")
                    }
                    Button(
                        onClick = {
                            val disc = discountText.toDoubleOrNull() ?: 0.0
                            onApply(disc)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Hold Sale Dialog
// -------------------------------------------------------------------------------------
@Composable
fun HoldSaleDialog(
    totalAmount: Double,
    onHold: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("Customer ${(100..999).random()}") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("HOLD / PARK BILL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Temporarily park this bill (${CurrencyUtils.formatLkr(totalAmount)}) to serve the next customer.", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Bill Identifier / Note") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onHold(label) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("HOLD BILL", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Checkout Sheet
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutSheet(
    totalAmount: Double,
    customer: CustomerEntity?,
    onComplete: (paymentMethod: String, cashReceived: Double, cardAmount: Double, creditAmount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("CASH") } // CASH, CARD, CREDIT, SPLIT, QR
    var cashReceivedText by remember { mutableStateOf(totalAmount.toInt().toString()) }
    var splitCashText by remember { mutableStateOf((totalAmount / 2).toInt().toString()) }

    val cashReceived = cashReceivedText.toDoubleOrNull() ?: 0.0
    val change = (cashReceived - totalAmount).coerceAtLeast(0.0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LightSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CHECKOUT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    CurrencyUtils.formatLkr(totalAmount),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = BrandTealPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment modes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentModeButton(
                    modifier = Modifier.weight(1f),
                    title = "CASH",
                    icon = Icons.Default.Payments,
                    isSelected = selectedMethod == "CASH",
                    onClick = { selectedMethod = "CASH" }
                )
                PaymentModeButton(
                    modifier = Modifier.weight(1f),
                    title = "CARD",
                    icon = Icons.Default.CreditCard,
                    isSelected = selectedMethod == "CARD",
                    onClick = { selectedMethod = "CARD" }
                )
                PaymentModeButton(
                    modifier = Modifier.weight(1f),
                    title = "CREDIT",
                    icon = Icons.Default.AccountBalanceWallet,
                    isSelected = selectedMethod == "CREDIT",
                    onClick = { selectedMethod = "CREDIT" }
                )
                PaymentModeButton(
                    modifier = Modifier.weight(1f),
                    title = "SPLIT",
                    icon = Icons.Default.CallSplit,
                    isSelected = selectedMethod == "SPLIT",
                    onClick = { selectedMethod = "SPLIT" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedMethod) {
                "CASH" -> {
                    Text("Cash Received", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cashReceivedText,
                        onValueChange = { cashReceivedText = it },
                        modifier = Modifier.fillMaxWidth().testTag("cash_received_input"),
                        leadingIcon = { Text("Rs.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), color = Color.Black) },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(totalAmount.toInt(), 500, 1000, 2000, 5000).distinct().forEach { amt ->
                            OutlinedButton(
                                onClick = { cashReceivedText = amt.toString() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text("Rs.$amt", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Change to Return:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                CurrencyUtils.formatLkr(change),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = BrandTealPrimary
                            )
                        }
                    }
                }
                "CARD" -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Swipe or Tap Card on POS Terminal", fontWeight = FontWeight.Bold)
                            Text("Full amount ${CurrencyUtils.formatLkr(totalAmount)} will be charged.", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
                "CREDIT" -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusAmberBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = StatusAmber)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (customer != null) "Customer: ${customer.name}" else "⚠ Customer Required for Credit",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            if (customer != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Previous balance: ${CurrencyUtils.formatLkr(customer.creditBalance)}", fontSize = 12.sp)
                                Text("New balance: ${CurrencyUtils.formatLkr(customer.creditBalance + totalAmount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Please select a customer on the cart before saving a credit sale.", fontSize = 11.sp, color = StatusRed)
                            }
                        }
                    }
                }
                "SPLIT" -> {
                    Text("Split: Cash + Card", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = splitCashText,
                        onValueChange = { splitCashText = it },
                        label = { Text("Cash Portion (Rs.)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    val splitCash = splitCashText.toDoubleOrNull() ?: 0.0
                    val splitCard = (totalAmount - splitCash).coerceAtLeast(0.0)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Card Portion: ${CurrencyUtils.formatLkr(splitCard)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandTealPrimary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    when (selectedMethod) {
                        "CASH" -> onComplete("CASH", cashReceived, 0.0, 0.0)
                        "CARD" -> onComplete("CARD", 0.0, totalAmount, 0.0)
                        "CREDIT" -> onComplete("CREDIT", 0.0, 0.0, totalAmount)
                        "SPLIT" -> {
                            val splitCash = splitCashText.toDoubleOrNull() ?: 0.0
                            val splitCard = (totalAmount - splitCash).coerceAtLeast(0.0)
                            onComplete("SPLIT", splitCash, splitCard, 0.0)
                        }
                    }
                },
                enabled = selectedMethod != "CREDIT" || customer != null,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("complete_sale_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("COMPLETE SALE ✓", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun PaymentModeButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BrandTealPrimary else LightSurfaceVariant,
        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else TextPrimary
            )
        }
    }
}

// -------------------------------------------------------------------------------------
// Sale Complete & Thermal Receipt Dialog
// -------------------------------------------------------------------------------------
@Composable
fun SaleCompleteDialog(
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    profile: BusinessProfileEntity?,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var isPrinting by remember { mutableStateOf(false) }
    var printDone by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(StatusGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("SALE COMPLETE ✓", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextPrimary)
                    Text(
                        CurrencyUtils.formatLkr(sale.totalAmount),
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = BrandTealPrimary
                    )

                    if (sale.paymentMethod == "CASH" && sale.changeGiven > 0) {
                        Text(
                            "Change: ${CurrencyUtils.formatLkr(sale.changeGiven)}",
                            fontWeight = FontWeight.Bold,
                            color = StatusAmber,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Thermal receipt preview
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = ReceiptPaper),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text(profile?.name?.uppercase() ?: "ABC STORES", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ReceiptText)
                                Text(profile?.phone ?: "077 123 4567", fontSize = 10.sp, color = ReceiptText)
                                Text("Invoice: ${sale.invoiceNumber}", fontSize = 10.sp, color = ReceiptText)
                                Text("Date: ${CurrencyUtils.formatDateTime(sale.timestamp)}", fontSize = 10.sp, color = ReceiptText)
                                Text("--------------------------------", fontFamily = FontFamily.Monospace, color = ReceiptDashed)
                            }

                            items(items.size) { idx ->
                                val itm = items[idx]
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(itm.productName, fontSize = 11.sp, color = ReceiptText)
                                    Text(CurrencyUtils.formatLkr(itm.lineTotal), fontSize = 11.sp, color = ReceiptText)
                                }
                            }

                            item {
                                Text("--------------------------------", fontFamily = FontFamily.Monospace, color = ReceiptDashed)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ReceiptText)
                                    Text(CurrencyUtils.formatLkr(sale.totalAmount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ReceiptText)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("PAID (${sale.paymentMethod})", fontSize = 10.sp, color = ReceiptText)
                                    Text(CurrencyUtils.formatLkr(if (sale.cashReceived > 0) sale.cashReceived else sale.totalAmount), fontSize = 10.sp, color = ReceiptText)
                                }
                                Text("--------------------------------", fontFamily = FontFamily.Monospace, color = ReceiptDashed)
                                Text(profile?.receiptFooter ?: "Thank you!", fontSize = 10.sp, color = ReceiptText, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Printing / Sharing Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isPrinting = true
                                printDone = true
                                viewModel.printBillReceipt(sale, items)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (printDone) "Printed ✓" else "Print Receipt")
                        }

                        OutlinedButton(
                            onClick = { /* Share simulated WhatsApp message */ },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Bill")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("new_sale_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("NEW SALE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
