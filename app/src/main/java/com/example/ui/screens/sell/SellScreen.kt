package com.example.ui.screens.sell

import android.Manifest
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.example.data.model.VariantCatalog
import com.example.data.model.VariantCombination
import com.example.data.model.VariantGroup
import com.example.ui.components.BatchReorderDialog
import com.example.ui.components.HintCard
import com.example.ui.components.HintTone
import com.example.ui.components.LowStockRestockDialog
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.util.ReceiptDesign
import com.example.ui.util.ReceiptItemData
import com.example.ui.util.receiptDesign
import com.example.ui.viewmodel.CartItem
import com.example.ui.viewmodel.PosViewModel

/** Horizontal breathing room used by the sell canvas. */
private val SCREEN_PADDING = 14.dp

/** Chip labels that are not real product categories. */
private const val ALL_CATEGORY = "All"
private const val FAVOURITES_CATEGORY = "Favourites"

/** Parent rows only. Child variant lines are stocked separately but are chosen
 * through the parent's picker, so they should never appear as their own card. */
private fun catalogParentRows(products: List<ProductEntity>): List<ProductEntity> =
    products.filter { !it.isVariant }

/** Child lines created from a parent's [ProductEntity.variants] by [saveProduct]. */
private fun variantChildrenOf(products: List<ProductEntity>, parent: ProductEntity): List<ProductEntity> =
    products.filter { it.parentProductId == parent.id && it.isVariant }

/** A product only opens the variant chooser when it actually has options. */
private fun hasVariantOptions(products: List<ProductEntity>, product: ProductEntity): Boolean =
    product.variants.isNotBlank() || variantChildrenOf(products, product).isNotEmpty()

/** When a search matches a variant line, show its parent card instead. */
private fun parentOfVariant(products: List<ProductEntity>, child: ProductEntity): ProductEntity? =
    child.takeIf { it.isVariant }?.let { c ->
        products.firstOrNull { !it.isVariant && it.id == c.parentProductId }
    }

/** Parses parent "Name|price" definitions into quick-pick options. */
private fun parseVariantOptions(product: ProductEntity): List<Pair<String, Double>> =
    product.variants
        .split(Regex("""\n|;"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val name = parts.getOrNull(0).orEmpty()
            if (name.isBlank()) null else name to (parts.getOrNull(1)?.toDoubleOrNull() ?: product.sellingPrice)
        }

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
    var showCameraScannerDialog by remember { mutableStateOf(false) }
    var showQuickSaleDialog by remember { mutableStateOf(false) }
    var quickPrefillName by remember { mutableStateOf("") }
    var quickPrefillBarcode by remember { mutableStateOf<String?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showHoldDialog by remember { mutableStateOf(false) }
    var showHeldListDialog by remember { mutableStateOf(false) }
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var showShiftOverviewDialog by remember { mutableStateOf(false) }
    var editingCartItemIndex by remember { mutableStateOf<Int?>(null) }
    var unknownBarcode by remember { mutableStateOf<String?>(null) }
    var variantPickerProduct by remember { mutableStateOf<ProductEntity?>(null) }

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
    // sees pharmacy sections. Sub-category paths are listed as their own chips,
    // allowing deep drill-down (Food > Rice dishes > Biriyani) while the parent
    // category chip still shows everything underneath it. The row scrolls.
    val categories = remember(products) {
        val paths = products.mapNotNull { p ->
            val path = listOf(p.category, p.subCategory)
                .filter { it.isNotBlank() }
                .joinToString(" > ")
            path.ifBlank { null }
        }
        buildList {
            add(ALL_CATEGORY)
            if (products.any { it.isFavourite }) add(FAVOURITES_CATEGORY)
            addAll(products.map { it.category.trim() }.filter { it.isNotBlank() }.distinct().sorted())
            addAll(paths.filter { it.contains(" > ") }.distinct().sorted())
        }
    }

    val categoryListState = rememberLazyListState()

    // If the chosen category disappears (shop type changed, product deleted),
    // quietly fall back to All rather than showing an empty grid.
    LaunchedEffect(categories) {
        if (selectedCategory !in categories) selectedCategory = ALL_CATEGORY
    }

    // Scroll the chosen chip into view so the selection is never off-screen.
    LaunchedEffect(selectedCategory, categories) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) categoryListState.animateScrollToItem(index)
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        val base = catalogParentRows(products)
        fun matches(p: ProductEntity): Boolean =
            p.name.contains(searchQuery, ignoreCase = true) ||
                p.barcode.contains(searchQuery, ignoreCase = true) ||
                p.sku.contains(searchQuery, ignoreCase = true) ||
                p.category.contains(searchQuery, ignoreCase = true) ||
                p.subCategory.contains(searchQuery, ignoreCase = true)

        if (searchQuery.isNotBlank()) {
            // A typed option like "Basmati" should surface the Rice parent so
            // the shopkeeper can pick the size/portion through the normal dialog.
            val parentsOfMatchedVariants = products
                .filter { it.isVariant && matches(it) }
                .mapNotNull { parentOfVariant(products, it) }
            (base.filter { matches(it) } + parentsOfMatchedVariants)
                .distinctBy { it.id }
                .sortedBy { it.name }
        } else {
            when (selectedCategory) {
                ALL_CATEGORY -> base
                FAVOURITES_CATEGORY -> base.filter { it.isFavourite }
                else -> base.filter { p ->
                    val path = listOf(p.category, p.subCategory)
                        .filter { it.isNotBlank() }
                        .joinToString(" > ")
                    p.category.equals(selectedCategory, ignoreCase = true) ||
                        path.equals(selectedCategory, ignoreCase = true) ||
                        path.startsWith("$selectedCategory > ")
                }
            }
        }
    }

    val subtotal = remember(cart) { cart.sumOf { it.lineTotal } }
    val totalAmount = remember(subtotal, billDiscount) { (subtotal - billDiscount).coerceAtLeast(0.0) }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
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
                        color = BrandSurface,
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
                            Icon(Icons.Default.Assessment, contentDescription = "Today Sales", tint = BrandPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                CurrencyUtils.formatLkr(todayTotalSales),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandPrimary
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
                        onClick = { quickPrefillName = ""; quickPrefillBarcode = null; showQuickSaleDialog = true },
                        modifier = Modifier.testTag("quick_sale_icon_button")
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Quick Add", tint = BrandPrimary)
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
                contentPadding = PaddingValues(horizontal = SCREEN_PADDING, vertical = 10.dp),
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
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode Scan", tint = BrandPrimary, modifier = Modifier.size(20.dp))
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

                // 2. Quick action shortcuts: Quick Sale, Quick Item, Customer, Parked bills.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandSurface,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    quickPrefillName = ""
                                    quickPrefillBarcode = null
                                    showQuickSaleDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
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
                // The parent LazyColumn insets everything by 14dp, which boxed
                // this row in and made the last chip look cut off. Negating that
                // inset lets the row run the full width of the screen and scroll
                // edge to edge, with the 14dp moved inside as content padding so
                // the first and last chips still sit correctly when at rest.
                item {
                    LazyRow(
                        state = categoryListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val extra = SCREEN_PADDING.roundToPx() * 2
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        maxWidth = constraints.maxWidth + extra,
                                        minWidth = constraints.maxWidth + extra
                                    )
                                )
                                layout(placeable.width, placeable.height) {
                                    placeable.place(-SCREEN_PADDING.roundToPx(), 0)
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = SCREEN_PADDING)
                    ) {
                        items(categories, key = { it }) { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) BrandPrimary else LightSurface,
                                border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable {
                                        selectedCategory = cat
                                        searchQuery = ""
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) BrandOnPrimary else TextPrimary
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
                            colors = CardDefaults.cardColors(containerColor = BrandSurface),
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
                                    text = "It is not in your catalogue yet. Add it as a new item and categorise it later.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                                )

                                Button(
                                    onClick = {
                                        quickPrefillName = searchQuery
                                        quickPrefillBarcode = null
                                        showQuickSaleDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add \"$searchQuery\" as a new item", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                val hasVariants = hasVariantOptions(products, product)
                                ProductQuickCard(
                                    product = product,
                                    cartQty = cartQty,
                                    hasVariants = hasVariants,
                                    onAdd = {
                                        if (hasVariants) {
                                            variantPickerProduct = product
                                        } else {
                                            viewModel.addToCart(product)
                                        }
                                    }
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
                            Badge(containerColor = if (cart.isNotEmpty()) BrandPrimary else TextMuted) {
                                Text("${cart.sumOf { it.quantity.toInt() }} items", color = BrandOnPrimary, fontSize = 10.sp)
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
                                    Text("Tap any product above, scan barcode, or use Quick Add to sell.", fontSize = 11.sp, color = TextSecondary)
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

            // 7. Sticky checkout bar. Sits flush against the bottom navigation
            // with no gap: the parent Scaffold already reserves that space, so
            // this Surface must not add an inset of its own.
            Surface(
                color = LightSurface,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    HorizontalDivider(color = LightBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
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
                                color = BrandPrimary
                            )
                        }

                        Button(
                            onClick = { showCheckoutSheet = true },
                            enabled = cart.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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

    // Tapping a bill line opens the editor: quantity, real selling price, discount.
    editingCartItemIndex?.let { index ->
        cart.getOrNull(index)?.let { line ->
            EditCartLineSheet(
                item = line,
                canChangePrice = viewModel.can(Permission.CHANGE_PRICE),
                canDiscount = viewModel.can(Permission.GIVE_DISCOUNT),
                onQuantity = { viewModel.updateCartItemQuantity(index, it) },
                onPrice = { viewModel.updateCartItemPrice(index, it) },
                onDiscount = { viewModel.updateCartItemDiscount(index, it) },
                onRemove = { viewModel.removeFromCart(index) },
                onDismiss = { editingCartItemIndex = null }
            )
        }
    }

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
                // there we open Quick Add pre-filled instead of inventing a price.
                viewModel.addProductByBarcode(
                    barcode,
                    onFound = { found ->
                        if (hasVariantOptions(products, found)) {
                            variantPickerProduct = found
                        } else {
                            viewModel.addToCart(found)
                        }
                    },
                    onMissing = { missing ->
                        unknownBarcode = missing
                        quickPrefillBarcode = missing
                        quickPrefillName = ""
                        showQuickSaleDialog = true
                    }
                )
                showBarcodeDialog = false
            },
            onOpenCamera = {
                showBarcodeDialog = false
                showCameraScannerDialog = true
            },
            onDismiss = { showBarcodeDialog = false }
        )
    }

    if (showCameraScannerDialog) {
        CameraScannerDialog(
            onCodeScanned = { code ->
                viewModel.addProductByBarcode(
                    code,
                    onFound = { found ->
                        if (hasVariantOptions(products, found)) {
                            variantPickerProduct = found
                        } else {
                            viewModel.addToCart(found)
                        }
                    },
                    onMissing = { missing ->
                        unknownBarcode = missing
                        quickPrefillBarcode = missing
                        quickPrefillName = ""
                        showQuickSaleDialog = true
                    }
                )
                showCameraScannerDialog = false
            },
            onUseManual = {
                showCameraScannerDialog = false
                showBarcodeDialog = true
            },
            onDismiss = { showCameraScannerDialog = false }
        )
    }

    variantPickerProduct?.let { parent ->
        VariantPickerDialog(
            parent = parent,
            children = variantChildrenOf(products, parent),
            addedCount = cart.count { it.productId == parent.id || it.name.startsWith(parent.name) },
            onAddParent = { qty ->
                viewModel.addToCart(parent, qty)
                variantPickerProduct = null
            },
            onAddVariant = { child, qty ->
                viewModel.addToCart(child, qty)
                variantPickerProduct = null
            },
            onAddDefinition = { lineName, variantPrice, qty ->
                viewModel.addQuickItemToCart(
                    name = lineName,
                    price = variantPrice,
                    quantity = qty
                )
                variantPickerProduct = null
            },
            onDismiss = { variantPickerProduct = null }
        )
    }

    if (showQuickSaleDialog) {
        QuickSaleDialog(
            prefillName = quickPrefillName,
            prefillBarcode = quickPrefillBarcode,
            onSell = { amount ->
                viewModel.addQuickSaleToCart(amount)
                quickPrefillName = ""
                quickPrefillBarcode = null
                showQuickSaleDialog = false
            },
            onAddItem = { name, price, qty, disc, savePermanent ->
                viewModel.addQuickItemToCart(name, price, qty, disc)
                if (savePermanent) {
                    viewModel.saveProduct(
                        id = 0,
                        name = name,
                        sellingPrice = price,
                        costPrice = 0.0,
                        barcode = quickPrefillBarcode.orEmpty(),
                        sku = "",
                        category = "Other",
                        unit = "Piece",
                        openingStock = 0.0,
                        lowStock = 0.0,
                        isTracked = false,
                        isFavourite = true,
                        subCategory = "",
                        variants = ""
                    )
                }
                quickPrefillName = ""
                quickPrefillBarcode = null
                showQuickSaleDialog = false
            },
            onDismiss = {
                quickPrefillName = ""
                quickPrefillBarcode = null
                showQuickSaleDialog = false
            }
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
            onAddCustomer = { name, phone ->
                viewModel.saveCustomer(0, name, phone, "", "", 0.0, "")
            },
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
    hasVariants: Boolean = false,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cartQty > 0) BrandSurface else LightSurface
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
                if (product.isVariant) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.CallSplit,
                        contentDescription = "Variant",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (cartQty > 0) StatusGreen else BrandSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = if (cartQty > 0) Color.White else BrandOnPrimary,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${cartQty.toInt()} in cart",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                        }
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
                    color = BrandPrimary
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

            if (hasVariants) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Tap to choose a variant",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Variant picker: a product with sizes/colours/portions opens before adding it.
// -------------------------------------------------------------------------------------
@Composable
fun VariantPickerDialog(
    parent: ProductEntity,
    children: List<ProductEntity>,
    addedCount: Int = 0,
    onAddParent: (qty: Double) -> Unit,
    onAddVariant: (child: ProductEntity, qty: Double) -> Unit,
    onAddDefinition: (name: String, price: Double, qty: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val groups = VariantCatalog.parseGroups(parent.variants)
    val combos = VariantCatalog.buildCombinations(parent.variants, parent.sellingPrice)

    // Per-option extra price so each chip can show "+Rs.400" instead of hiding
    // how the choice moves the price.
    val extraByOption = remember(parent.variants) {
        VariantCatalog.parseDrafts(parent.variants, parent.sellingPrice)
            .flatMap { it.options }
            .associate { it.name.lowercase() to it.priceAdjustment }
    }

    // Pre-select the first option in every group so the price and the Add
    // button are visible the moment the dialog opens (fewer taps).
    var selected by remember(parent.id, parent.variants) {
        mutableStateOf(groups.associate { g -> g.name to g.options.firstOrNull().orEmpty() })
    }
    var qty by remember(parent.id) { mutableStateOf(1.0) }

    val allSelected = groups.all { g -> selected[g.name]?.isNotBlank() == true }
    val selectedCombo: VariantCombination? = if (allSelected && combos.isNotEmpty()) {
        combos.firstOrNull { combo ->
            groups.withIndex().all { (index, group) ->
                combo.labels.getOrNull(index)
                    ?.equals(selected[group.name].orEmpty(), ignoreCase = true) == true
            }
        }
    } else null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose a variant", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextPrimary)
                        Text(
                            listOf(parent.category, parent.subCategory)
                                .filter { it.isNotBlank() }
                                .joinToString(" > ")
                                .ifBlank { "Product" },
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    if (addedCount > 0) {
                        Surface(shape = RoundedCornerShape(8.dp), color = StatusGreenBg) {
                            Text(
                                "$addedCount in bill",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Base item", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandSurface,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddParent(qty) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(parent.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            if (parent.isTracked) {
                                Text(
                                    "${parent.currentStock.toInt()} ${parent.unit}",
                                    fontSize = 11.sp,
                                    color = if (parent.currentStock <= parent.lowStockThreshold) StatusAmber else TextSecondary
                                )
                            }
                        }
                        Text(CurrencyUtils.formatLkr(parent.sellingPrice), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandPrimary)
                    }
                }

                // Deep options (e.g. Rice type + Portion) show one group at a time,
                // two per row, with the extra price on each chip.
                if (groups.isNotEmpty()) {
                    groups.forEach { group ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(group.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))

                        group.options.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { option ->
                                    val isChosen = selected[group.name] == option
                                    val extra = extraByOption[option.lowercase()] ?: 0.0
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isChosen) BrandSurface else LightSurface,
                                        border = BorderStroke(
                                            width = if (isChosen) 2.dp else 1.dp,
                                            color = if (isChosen) BrandPrimary else LightBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selected = selected + (group.name to option) }
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                option,
                                                fontSize = 13.sp,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                                color = TextPrimary
                                            )
                                            Text(
                                                if (extra == 0.0) "base" else "+${CurrencyUtils.formatLkr(extra)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isChosen) BrandPrimaryDark else TextMuted
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (allSelected && selectedCombo != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${parent.name} · ${selectedCombo.displayName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Text("${CurrencyUtils.formatLkr(selectedCombo.price)} each", fontSize = 10.sp, color = TextSecondary)
                                }
                                Text(
                                    CurrencyUtils.formatLkr(selectedCombo.price * qty),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = BrandPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quantity stepper + Add, one row.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = LightSurfaceVariant,
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { qty = (qty - 1).coerceAtLeast(1.0) }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Less", tint = TextPrimary)
                                    }
                                    Text("${qty.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { qty = (qty + 1).coerceAtMost(999.0) }) {
                                        Icon(Icons.Default.Add, contentDescription = "More", tint = TextPrimary)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val child = VariantCatalog.findChild(children, parent.id, parent.name, selectedCombo)
                                    if (child != null) onAddVariant(child, qty)
                                    else onAddDefinition("${parent.name} - ${selectedCombo.displayName}", selectedCombo.price, qty)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ADD ${selectedCombo.displayName.uppercase()}", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Choose one from each group to see the price.", fontSize = 11.sp, color = TextMuted)
                    }

                    if (children.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Stocked lines", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        children.forEach { child ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = LightSurface,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAddVariant(child, qty) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(child.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                        if (child.isTracked) {
                                            Text(
                                                "${child.currentStock.toInt()} ${child.unit}",
                                                fontSize = 11.sp,
                                                color = if (child.currentStock <= child.lowStockThreshold) StatusAmber else TextSecondary
                                            )
                                        }
                                    }
                                    Text(CurrencyUtils.formatLkr(child.sellingPrice), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = BrandPrimary)
                                }
                            }
                        }
                    }
                } else if (combos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Quick options", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    combos.forEach { combo ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LightSurface,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val child = VariantCatalog.findChild(children, parent.id, parent.name, combo)
                                        if (child != null) onAddVariant(child, qty)
                                        else onAddDefinition("${parent.name} - ${combo.displayName}", combo.price, qty)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${parent.name} - ${combo.displayName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                }
                                Text(CurrencyUtils.formatLkr(combo.price), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = BrandPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Every option is its own stockable line. Deep products such as rice + portion are created automatically from the product's variant rules.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
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
                        colors = CardDefaults.cardColors(containerColor = BrandSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Sales", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text(
                                CurrencyUtils.formatLkr(totalSales),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = BrandPrimary
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
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (showChart) "Hide Revenue Chart" else "View Revenue Growth Trajectory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
                                        color = BrandPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BrandPrimary
                                    ) {
                                        Text(
                                            "RESUME",
                                            color = BrandOnPrimary,
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
                // Tap anywhere on the line to change quantity, price or discount.
                .clickable { onEdit() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPriceChanged) {
                        // Show the old price struck through so the change is obvious.
                        Text(
                            CurrencyUtils.formatLkr(item.listPrice),
                            fontSize = 11.sp,
                            color = TextMuted,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        CurrencyUtils.formatLkr(item.unitPrice) + if (item.discount > 0) " (-${CurrencyUtils.formatLkr(item.discount)})" else "",
                        fontSize = 12.sp,
                        fontWeight = if (item.isPriceChanged) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.isPriceChanged) StatusAmber else TextSecondary
                    )
                }
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
// Barcode Entry Dialog (USB/Bluetooth keyboard scanner or manual entry)
// -------------------------------------------------------------------------------------
@Composable
fun BarcodeEntryDialog(
    onBarcodeEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit
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
                    "Scan with your barcode reader, type the number, or open the in-app camera.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = onOpenCamera,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open camera scanner", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isLetterOrDigit() }
                        barcode = cleaned
                    },
                    label = { Text("Barcode") },
                    leadingIcon = {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BrandPrimary)
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
// In-app camera barcode + QR scanner.
// -------------------------------------------------------------------------------------
@androidx.camera.core.ExperimentalGetImage
@Composable
fun CameraScannerDialog(
    onCodeScanned: (String) -> Unit,
    onUseManual: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var found by remember { mutableStateOf(false) }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

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
                    Text("Scan barcode / QR", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Text(
                    "Point the camera at the product code. When it is found, the item goes straight onto the bill.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (!granted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusAmberBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Camera permission is needed to scan inside the app.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusAmber,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onUseManual,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use keyboard", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { launcher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Allow camera", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analyzer ->
                                            analyzer.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null) {
                                                    val input = InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees
                                                    )
                                                    scanner.process(input)
                                                        .addOnSuccessListener { codes ->
                                                            if (!found && codes.isNotEmpty()) {
                                                                val raw = codes.first().rawValue.orEmpty()
                                                                if (raw.isNotBlank()) {
                                                                    found = true
                                                                    onCodeScanned(raw)
                                                                }
                                                            }
                                                        }
                                                        .addOnCompleteListener { imageProxy.close() }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }
                                        }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "If the scan is slow, tap Use keyboard instead.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    TextButton(onClick = onUseManual, modifier = Modifier.fillMaxWidth()) {
                        Text("Use keyboard entry", fontWeight = FontWeight.Bold)
                    }
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
// Quick Add — one shortcut that does both the old Quick Item and Quick Sale.
// “Item” is for a named product/topup; “Total” is for a bulk amount with no
// item detail. Either way it reaches the same cart in one or two taps.
@Composable
fun QuickSaleDialog(
    prefillName: String = "",
    prefillBarcode: String? = null,
    onSell: (Double) -> Unit,
    onAddItem: (name: String, price: Double, qty: Double, discount: Double, savePermanent: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("ITEM") }
    var name by remember { mutableStateOf(prefillName) }
    var priceText by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1.0) }
    var amountText by remember { mutableStateOf("") }
    var saveAsPermanent by remember { mutableStateOf(prefillBarcode != null || prefillName.isNotBlank()) }

    val price = priceText.toDoubleOrNull() ?: 0.0

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
                    Text("QUICK ADD", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (prefillBarcode != null) {
                    Text(
                        "Barcode $prefillBarcode is not in your items yet. Add it here and it can be saved too.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                } else {
                    Text("For a busy sale — name an item or just take a total.", fontSize = 13.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickModeButton("ITEM", "Name it, price it", mode == "ITEM", { mode = "ITEM" }, Modifier.weight(1f))
                    QuickModeButton("TOTAL", "Just the amount", mode == "TOTAL", { mode = "TOTAL" }, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (mode == "ITEM") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item name / description") },
                        placeholder = { Text("e.g. Custom cup of tea") },
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
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
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
                    Spacer(modifier = Modifier.height(10.dp))
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = saveAsPermanent, onCheckedChange = { saveAsPermanent = it })
                        Text(
                            "Also save to my items. ${name.ifBlank { "this item" }} can be categorised later.",
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { if (price > 0) onAddItem(name.ifBlank { "Quick Item" }, price, qty, 0.0, saveAsPermanent) },
                        enabled = price > 0 && name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("ADD TO BILL", fontWeight = FontWeight.Bold)
                    }
                } else {
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
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("ADD TOTAL TO BILL", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickModeButton(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) BrandPrimary else LightSurfaceVariant,
        border = if (selected) null else CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) BrandOnPrimary else TextPrimary)
            Text(sub, fontSize = 9.sp, color = if (selected) BrandOnPrimary.copy(alpha = 0.8f) else TextSecondary)
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
                                color = if (selected?.id == c.id) BrandSurface else LightSurfaceVariant,
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
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
    onAddCustomer: (name: String, phone: String) -> Unit,
    onComplete: (paymentMethod: String, cashReceived: Double, cardAmount: Double, creditAmount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("CASH") } // CASH, CARD, CREDIT, SPLIT, QR
    var cashReceivedText by remember { mutableStateOf(totalAmount.toInt().toString()) }
    var splitCashText by remember { mutableStateOf((totalAmount / 2).toInt().toString()) }
    var splitRemainingMethod by remember { mutableStateOf("CARD") } // CARD or CREDIT for the rest of a split
    var showAddCustomer by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }

    val cashReceived = cashReceivedText.toDoubleOrNull() ?: 0.0
    val change = (cashReceived - totalAmount).coerceAtLeast(0.0)
    val cashShort = selectedMethod == "CASH" && cashReceived > 0.0 && cashReceived < totalAmount - 0.001

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
                    color = BrandPrimary
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
                        colors = CardDefaults.cardColors(containerColor = BrandSurface),
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
                                color = BrandPrimary
                            )
                        }
                    }

                    if (cashShort) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusAmberBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Cash received is less than the bill.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusAmber
                                )
                                Text(
                                    "The sale can't go through with less than ${CurrencyUtils.formatLkr(totalAmount)}. " +
                                        "Split the remaining ${CurrencyUtils.formatLkr(totalAmount - cashReceived)} on card or as credit instead.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                )
                                TextButton(
                                    onClick = {
                                        splitCashText = cashReceived.toInt().toString()
                                        splitRemainingMethod = "CARD"
                                        selectedMethod = "SPLIT"
                                    }
                                ) {
                                    Text("Split the rest on card", fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        splitCashText = cashReceived.toInt().toString()
                                        splitRemainingMethod = "CREDIT"
                                        selectedMethod = "SPLIT"
                                    }
                                ) {
                                    Text("Split the rest as credit", fontWeight = FontWeight.Bold, color = StatusAmber)
                                }
                            }
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
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp))
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
                                    text = if (customer != null) "Customer: ${customer.name}" else "Choose a customer for credit",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            if (customer != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Previous balance: ${CurrencyUtils.formatLkr(customer.creditBalance)}", fontSize = 12.sp)
                                Text("New balance: ${CurrencyUtils.formatLkr(customer.creditBalance + totalAmount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Please select a customer before saving a credit sale.", fontSize = 11.sp, color = StatusRed)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { showAddCustomer = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create customer now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                "SPLIT" -> {
                    Text("Split payment", fontWeight = FontWeight.Bold)
                    Text(
                        "Enter what the customer is paying cash first. The rest can go on a card or straight into their credit book.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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
                    val remaining = (totalAmount - splitCash).coerceAtLeast(0.0)
                    if (remaining > 0.0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Rest of bill (${CurrencyUtils.formatLkr(remaining)}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (splitRemainingMethod == "CARD") BrandPrimary else LightSurfaceVariant,
                                modifier = Modifier.weight(1f).clickable { splitRemainingMethod = "CARD" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = if (splitRemainingMethod == "CARD") BrandOnPrimary else TextSecondary, modifier = Modifier.size(18.dp))
                                    Text("On card", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (splitRemainingMethod == "CARD") BrandOnPrimary else TextPrimary)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (splitRemainingMethod == "CREDIT") StatusAmber else LightSurfaceVariant,
                                modifier = Modifier.weight(1f).clickable { splitRemainingMethod = "CREDIT" }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = if (splitRemainingMethod == "CREDIT") Color.White else TextSecondary, modifier = Modifier.size(18.dp))
                                    Text("As credit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (splitRemainingMethod == "CREDIT") Color.White else TextPrimary)
                                }
                            }
                        }
                        if (splitRemainingMethod == "CREDIT") {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (customer != null) {
                                Surface(shape = RoundedCornerShape(10.dp), color = StatusAmberBg, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Credit to ${customer.name} — new balance ${CurrencyUtils.formatLkr(customer.creditBalance + remaining)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusAmber,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                Text("Select or create a customer for the credit portion.", fontSize = 11.sp, color = StatusRed)
                                OutlinedButton(
                                    onClick = { showAddCustomer = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Create customer now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Full bill is covered by cash — no remaining amount to split.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val splitCash = splitCashText.toDoubleOrNull() ?: 0.0
            val splitRemaining = (totalAmount - splitCash).coerceAtLeast(0.0)
            val enableComplete = when (selectedMethod) {
                "CASH" -> cashReceived >= totalAmount - 0.001
                "CARD" -> totalAmount > 0.0
                // Credit needs a person. You can create one on the spot below.
                "CREDIT" -> customer != null
                "SPLIT" -> splitCash >= 0.0 && splitRemaining >= 0.0 &&
                    (splitRemainingMethod == "CARD" || customer != null) &&
                    splitCash > 0.0 &&
                    splitCash < totalAmount + 0.001
                else -> false
            }

            Button(
                onClick = {
                    when (selectedMethod) {
                        "CASH" -> onComplete("CASH", cashReceived, 0.0, 0.0)
                        "CARD" -> onComplete("CARD", 0.0, totalAmount, 0.0)
                        "CREDIT" -> onComplete("CREDIT", 0.0, 0.0, totalAmount)
                        "SPLIT" -> {
                            if (splitRemainingMethod == "CREDIT") {
                                onComplete("SPLIT", splitCash, 0.0, splitRemaining)
                            } else {
                                onComplete("SPLIT", splitCash, splitRemaining, 0.0)
                            }
                        }
                    }
                },
                enabled = enableComplete,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("complete_sale_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("COMPLETE SALE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (selectedMethod == "CASH" && cashShort) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter the full amount before completing. The bill is ${CurrencyUtils.formatLkr(totalAmount)}.",
                    fontSize = 11.sp,
                    color = StatusRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAddCustomer) {
        AlertDialog(
            onDismissRequest = { showAddCustomer = false },
            title = { Text("Create customer for credit", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCustomerPhone,
                        onValueChange = { newCustomerPhone = it },
                        label = { Text("Phone (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustomerName.isNotBlank()) {
                            onAddCustomer(newCustomerName.trim(), newCustomerPhone.trim())
                            newCustomerName = ""
                            newCustomerPhone = ""
                            showAddCustomer = false
                        }
                    },
                    enabled = newCustomerName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save & select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomer = false }) { Text("Cancel") }
            }
        )
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
        color = if (isSelected) BrandPrimary else LightSurfaceVariant,
        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) BrandOnPrimary else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) BrandOnPrimary else TextPrimary
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

                    Text("SALE COMPLETE", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextPrimary)
                    Text(
                        CurrencyUtils.formatLkr(sale.totalAmount),
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = BrandPrimary
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

                    // Receipt preview. This is the *exact* text that goes to
                    // the thermal printer - same monospace grid, same columns,
                    // same quantity lines - so what the shopkeeper sees on
                    // screen is what comes out on paper.
                    val receiptText = remember(sale, items, profile) {
                        CurrencyUtils.buildReceiptText(
                            businessName = profile?.name.orEmpty(),
                            businessPhone = profile?.phone.orEmpty(),
                            businessAddress = profile?.address.orEmpty(),
                            invoiceNumber = sale.invoiceNumber,
                            timestamp = sale.timestamp,
                            cashierName = sale.cashierName,
                            customerName = sale.customerName,
                            items = items.map {
                                ReceiptItemData(
                                    name = it.productName,
                                    quantity = it.quantity,
                                    unitPrice = it.unitPrice,
                                    lineTotal = it.lineTotal
                                )
                            },
                            subtotal = sale.subtotal,
                            discount = sale.discountAmount,
                            total = sale.totalAmount,
                            paymentMethod = sale.paymentMethod,
                            cashReceived = sale.cashReceived,
                            change = sale.changeGiven,
                            footerMessage = profile?.receiptFooter.orEmpty(),
                            paperWidth = profile?.printerPaperWidth ?: "58mm",
                            design = profile?.receiptDesign() ?: ReceiptDesign()
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = ReceiptPaper),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = receiptText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                color = ReceiptText,
                                softWrap = false
                            )
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
                            Text(if (printDone) "Printed" else "Print Receipt")
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
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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

// -------------------------------------------------------------------------------------
// Edit a line in the bill: quantity, the price it is actually selling for, and
// any discount. Retail is not fixed-price — a regular haggles, a dented tin
// goes cheap, a bulk buyer gets a better rate. This is where that happens.
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCartLineSheet(
    item: CartItem,
    canChangePrice: Boolean,
    canDiscount: Boolean,
    onQuantity: (Double) -> Unit,
    onPrice: (Double) -> Unit,
    onDiscount: (Double) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var qty by remember(item) { mutableStateOf(item.quantity) }
    var priceText by remember(item) { mutableStateOf(item.unitPrice.trimZeros()) }
    var discountText by remember(item) {
        mutableStateOf(if (item.discount > 0) item.discount.trimZeros() else "")
    }

    val price = priceText.toDoubleOrNull() ?: item.unitPrice
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val lineTotal = ((price * qty) - discount).coerceAtLeast(0.0)
    val below = price < item.listPrice - 0.001
    val above = price > item.listPrice + 0.001

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(
                "Normal price ${CurrencyUtils.formatLkr(item.listPrice)}",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
            )

            // Quantity
            Text("How many", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalIconButton(
                    onClick = { if (qty > 1) qty -= 1.0 },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = LightSurfaceVariant)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Less", tint = TextPrimary)
                }
                Text(
                    qty.trimZeros(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 100.dp)
                        .testTag("edit_qty")
                )
                FilledTonalIconButton(
                    onClick = { qty += 1.0 },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = BrandSurface)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "More", tint = BrandPrimary)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Price for this sale
            if (canChangePrice) {
                Text(
                    "Selling price for one",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    leadingIcon = { Text("Rs.", fontWeight = FontWeight.Bold, color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_price"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPrimary)
                )

                if (below || above) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (below) StatusAmberBg else StatusGreenBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (below) {
                                "Selling ${CurrencyUtils.formatLkr(item.listPrice - price)} cheaper than normal"
                            } else {
                                "Selling ${CurrencyUtils.formatLkr(price - item.listPrice)} above normal"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (below) StatusAmber else StatusGreen,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { priceText = item.listPrice.trimZeros() }) {
                        Text("Normal price", fontSize = 12.sp)
                    }
                    listOf(5, 10).forEach { pct ->
                        TextButton(onClick = {
                            priceText = (item.listPrice * (100 - pct) / 100.0).trimZeros()
                        }) {
                            Text("-$pct%", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Line discount
            if (canDiscount) {
                Text("Take off an amount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("0") },
                    leadingIcon = { Text("Rs.", fontWeight = FontWeight.Bold, color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPrimary)
                )
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Nothing this person may change? Say so plainly instead of showing
            // a sheet that only has a quantity stepper and no explanation.
            if (!canChangePrice && !canDiscount) {
                com.example.ui.components.HintCard(
                    text = "You can change how many, but not the price or discount. " +
                        "Ask the owner if a customer needs a better price."
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BrandSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Line total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        CurrencyUtils.formatLkr(lineTotal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (canChangePrice && kotlin.math.abs(price - item.unitPrice) > 0.001) {
                        onPrice(price)
                    }
                    if (qty != item.quantity) onQuantity(qty)
                    if (discount != item.discount) onDiscount(discount)
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_cart_line")
            ) {
                Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = { onRemove(); onDismiss() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Remove from bill", color = StatusRed, fontSize = 13.sp)
            }
        }
    }
}

/** 12.0 -> "12", 12.50 -> "12.5". Nobody wants to read trailing zeroes. */
private fun Double.trimZeros(): String =
    if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this).trimEnd('0').trimEnd('.')
