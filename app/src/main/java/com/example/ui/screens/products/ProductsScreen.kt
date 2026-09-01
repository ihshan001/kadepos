package com.example.ui.screens.products

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ProductEntity
import com.example.data.model.VariantCatalog
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.data.model.Permission
import com.example.ui.viewmodel.PosViewModel

/** Parent rows only. Child variant lines are edited through the parent, so
 * they should never appear as standalone catalogue cards. */
private fun catalogParentRows(products: List<ProductEntity>): List<ProductEntity> =
    products.filter { !it.isVariant }

/** When a search matches a variant line, show its parent card instead. */
private fun parentOfVariant(products: List<ProductEntity>, child: ProductEntity): ProductEntity? =
    child.takeIf { it.isVariant }?.let { c ->
        products.firstOrNull { !it.isVariant && it.id == c.parentProductId }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: PosViewModel,
    /** Sell an item that comes in options: opens its picker on the Sell tab. */
    onSellItem: (ProductEntity) -> Unit
) {
    // The Items tab is hidden for people without this permission, but a
    // hidden tab is not a lock. This is the lock.
    val screenPermissions by viewModel.permissions.collectAsState()
    if (screenPermissions.cannot(Permission.MANAGE_PRODUCTS)) {
        com.example.ui.components.LockedScreenNotice(
            message = screenPermissions.denialMessage(Permission.MANAGE_PRODUCTS)
        )
        return
    }

    val products by viewModel.products.collectAsState()

    // Child variant lines are stock lines, not separate catalogue cards. The
    // Items grid is the catalogue, so it always starts from parent rows only.
    val parentRows = remember(products) { catalogParentRows(products) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, FAVOURITES, LOW_STOCK, OUT_OF_STOCK, or category name
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var restockingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Removing a whole starter list one item at a time is the slowest job in
    // the shop, so the list can be put into a selecting mode: tick the ones
    // that are not yours, then take them out in one go.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val categories = remember(parentRows) {
        buildList {
            addAll(parentRows.map { it.category }.filter { it.isNotBlank() }.distinct().sorted())
            addAll(
                parentRows.filter { it.subCategory.isNotBlank() }
                    .map { listOf(it.category, it.subCategory).filter { it.isNotBlank() }.joinToString(" > ") }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            )
        }.distinct()
    }

    // Flat lists that feed the dropdowns on the Add/Edit product dialog, so a
    // shopkeeper picks the category instead of retyping it each time.
    val categoryOptions = remember(parentRows) {
        parentRows.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    // Sub-categories grouped by their category: picking a category on the
    // Add/Edit screen should narrow the second list, not offer everything.
    val subCategoryOptionsByCategory = remember(parentRows) {
        parentRows
            .filter { it.subCategory.isNotBlank() }
            .groupBy(keySelector = { it.category }, valueTransform = { it.subCategory })
            .mapValues { (_, subs) ->
                subs.flatMap { it.split(">") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            }
    }

    val filteredProducts = remember(products, parentRows, searchQuery, selectedFilter) {
        val base = parentRows
        val matches: (ProductEntity) -> Boolean = { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                p.barcode.contains(searchQuery, ignoreCase = true) ||
                p.sku.contains(searchQuery, ignoreCase = true) ||
                p.category.contains(searchQuery, ignoreCase = true) ||
                p.subCategory.contains(searchQuery, ignoreCase = true)
        }
        val matchFilter: (ProductEntity) -> Boolean = { p ->
            val fullPath = listOf(p.category, p.subCategory)
                .filter { it.isNotBlank() }
                .joinToString(" > ")
            when (selectedFilter) {
                "ALL" -> true
                "FAVOURITES" -> p.isFavourite
                "LOW_STOCK" -> p.isTracked && p.currentStock <= p.lowStockThreshold && p.currentStock > 0
                "OUT_OF_STOCK" -> p.isTracked && p.currentStock <= 0
                else -> p.category.equals(selectedFilter, ignoreCase = true) ||
                    fullPath.equals(selectedFilter, ignoreCase = true) ||
                    fullPath.startsWith("$selectedFilter > ")
            }
        }

        if (searchQuery.isNotBlank()) {
            // If the owner types a variant option name, surface the parent so
            // the option is reached through the normal variant picker.
            val parentsOfMatchedVariants = products
                .filter { it.isVariant && matches(it) }
                .mapNotNull { parentOfVariant(products, it) }
                .filter(matchFilter)
            (base.filter { matchFilter(it) && matches(it) } + parentsOfMatchedVariants)
                .distinctBy { it.id }
                .sortedBy { it.name }
        } else {
            base.filter(matchFilter)
        }
    }

    val totalCatalogValuation = remember(products) {
        products.filter { it.isTracked && it.currentStock > 0 }.sumOf { it.costPrice * it.currentStock }
    }
    val lowStockCount = remember(parentRows) {
        parentRows.count { it.isTracked && it.currentStock <= it.lowStockThreshold }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Product Catalog & Items", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text("${parentRows.size} catalogue items", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_product_top_btn")) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Product", tint = BrandPrimary, modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BrandPrimary,
                contentColor = BrandOnPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Product", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_product_fab")
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
            // 1. Inventory Valuation & Alert Strip
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().testTag("products_summary_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL STOCK VALUATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            CurrencyUtils.formatLkr(totalCatalogValuation),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lowStockCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StatusAmberBg,
                                modifier = Modifier.clickable { selectedFilter = "LOW_STOCK" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("$lowStockCount Low", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandSurface
                        ) {
                            Text(
                                "${parentRows.count { it.isFavourite }} Pinned",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1b. Taking out several items at once.
            // The starter list is a guess, so most shops need to clear a few
            // things they will never sell. Ticking a batch beats opening each
            // one and pressing Remove on its own.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!selectionMode) {
                    TextButton(onClick = {
                        selectionMode = true
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select items", fontWeight = FontWeight.Bold, color = BrandPrimary)
                    }
                } else {
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == filteredProducts.size) {
                            emptySet()
                        } else {
                            filteredProducts.map { it.id }.toSet()
                        }
                    }) {
                        Text(
                            if (selectedIds.size == filteredProducts.size) "Untick all" else "Tick all",
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary
                        )
                    }

                    Text(
                        "${selectedIds.size} chosen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )

                    Row {
                        TextButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Text("Done", color = TextSecondary)
                        }
                        Button(
                            onClick = { showRemoveConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                        ) {
                            Text("Remove", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, category or barcode", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = LightBorder,
                    focusedContainerColor = LightSurface,
                    unfocusedContainerColor = LightSurface
                ),
                modifier = Modifier.fillMaxWidth().testTag("product_search_bar"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Filter Carousel (All, Favourites, Low Stock, Out of Stock, Categories)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    ProductFilterChip(
                        label = "All (${parentRows.size})",
                        isSelected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" }
                    )
                }
                item {
                    ProductFilterChip(
                        label = "Favourites",
                        isSelected = selectedFilter == "FAVOURITES",
                        onClick = { selectedFilter = "FAVOURITES" },
                        icon = Icons.Default.Star
                    )
                }
                item {
                    ProductFilterChip(
                        label = "Running low ($lowStockCount)",
                        isSelected = selectedFilter == "LOW_STOCK",
                        onClick = { selectedFilter = "LOW_STOCK" },
                        highlightAmber = true,
                        icon = Icons.Default.WarningAmber
                    )
                }
                items(categories, key = { it }) { cat ->
                    val countInCat = parentRows.count { p ->
                        val path = listOf(p.category, p.subCategory)
                            .filter { it.isNotBlank() }
                            .joinToString(" > ")
                        p.category.equals(cat, ignoreCase = true) ||
                            path.equals(cat, ignoreCase = true) ||
                            path.startsWith("$cat > ")
                    }
                    ProductFilterChip(
                        label = "$cat ($countInCat)",
                        isSelected = selectedFilter.equals(cat, ignoreCase = true),
                        onClick = { selectedFilter = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Products List
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No products found", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Try a different search or add a new item", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add First Item")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            selectionMode = selectionMode,
                            selected = product.id in selectedIds,
                            onToggleSelect = {
                                selectedIds = if (product.id in selectedIds) {
                                    selectedIds - product.id
                                } else {
                                    selectedIds + product.id
                                }
                            },
                            onEdit = { editingProduct = product },
                            onRestock = { restockingProduct = product },
                            onAddToCart = {
                                // An item with options has to be chosen from
                                // the selling screen, where each option shows
                                // its own price and what is left of it — so
                                // go there with its picker already open.
                                if (product.variants.isNotBlank()) {
                                    onSellItem(product)
                                } else {
                                    viewModel.addToCart(product)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Nothing is taken out until it is confirmed. A mis-tick should cost one
    // more tap, not a rebuilt catalogue.
    if (showRemoveConfirm) {
        val count = selectedIds.size
        Dialog(onDismissRequest = { showRemoveConfirm = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        if (count == 1) "Remove this item?" else "Remove these $count items?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "They stop showing in this list and on the Sell tab. " +
                            "Old bills that mention them stay exactly as they are.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRemoveConfirm = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Keep") }
                        Button(
                            onClick = {
                                showRemoveConfirm = false
                                viewModel.archiveProducts(selectedIds.toList())
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                        ) { Text(if (count == 1) "Remove" else "Remove $count") }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingProduct != null) {
        val edited = editingProduct
        ProductEditScreen(
            product = edited,
            // The stock lines already created for this item, so opening the
            // editor shows the counts the shop is actually working with.
            children = if (edited == null) {
                emptyList()
            } else {
                products.filter { it.isVariant && it.parentProductId == edited.id }
            },
            categoryOptions = categoryOptions,
            subCategoryOptions = subCategoryOptionsByCategory,
            onSave = { request ->
                viewModel.saveProduct(
                    id = request.id,
                    name = request.name,
                    sellingPrice = request.sellingPrice,
                    costPrice = request.costPrice,
                    barcode = request.barcode,
                    sku = request.sku,
                    category = request.category,
                    unit = request.unit,
                    openingStock = request.openingStock,
                    lowStock = request.lowStock,
                    isTracked = request.isTracked,
                    isFavourite = request.isFavourite,
                    subCategory = request.subCategory,
                    variants = request.variants,
                    comboStock = request.comboStock
                )
                showAddDialog = false
                editingProduct = null
            },
            onDelete = if (edited != null) {
                {
                    viewModel.deleteProduct(edited.id)
                    editingProduct = null
                }
            } else null,
            onDismiss = {
                showAddDialog = false
                editingProduct = null
            }
        )
    }

    if (restockingProduct != null) {
        val prod = restockingProduct!!
        QuickRestockDialog(
            product = prod,
            onConfirm = { qty, unitCost, supplier ->
                viewModel.receiveStockDirect(prod.id, qty, unitCost, supplier)
                restockingProduct = null
            },
            onDismiss = { restockingProduct = null }
        )
    }
}

@Composable
fun ProductFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    highlightAmber: Boolean = false,
    /** A real vector icon. Emoji render differently on every phone and do not tint. */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val contentColor = when {
        isSelected -> BrandOnPrimary
        highlightAmber -> StatusAmber
        else -> TextPrimary
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isSelected -> BrandPrimary
            highlightAmber -> StatusAmberBg
            else -> LightSurface
        },
        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onRestock: () -> Unit,
    onAddToCart: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val profit = (product.sellingPrice - product.costPrice).coerceAtLeast(0.0)
    val marginPct = if (product.sellingPrice > 0) ((profit / product.sellingPrice) * 100).toInt() else 0
    val isLowStock = product.isTracked && product.currentStock <= product.lowStockThreshold
    val isOutOfStock = product.isTracked && product.currentStock <= 0

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BrandPrimary.copy(alpha = 0.10f) else LightSurface
        ),
        border = if (selected) {
            BorderStroke(2.dp, BrandPrimary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (selectionMode) onToggleSelect() else onEdit() }
            .testTag("product_item_${product.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = (-6).dp)
                            .testTag("product_select_${product.id}")
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        if (product.isVariant) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.CallSplit, contentDescription = "Variant", tint = TextMuted, modifier = Modifier.size(15.dp))
                        }
                        if (product.isFavourite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Star, contentDescription = "Favourite", tint = StatusAmber, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LightSurfaceVariant
                        ) {
                            Text(
                                text = listOf(product.category, product.subCategory)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" > ")
                                    .ifBlank { "General" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (product.barcode.isNotBlank()) {
                            Text("EAN: ${product.barcode}", fontSize = 10.sp, color = TextMuted)
                        }
                    }

                    if (product.variants.isNotBlank()) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                VariantCatalog.summary(product.variants),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Price & Margin
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyUtils.formatLkr(product.sellingPrice),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = BrandPrimary
                    )

                    if (product.costPrice > 0) {
                        Text(
                            text = "Cost: ${CurrencyUtils.formatLkr(product.costPrice)} (+$marginPct%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = StatusGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = LightBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row: Stock info + Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Badge
                if (product.isTracked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isOutOfStock -> StatusRedBg
                            isLowStock -> StatusAmberBg
                            else -> StatusGreenBg
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isOutOfStock -> StatusRed
                                            isLowStock -> StatusAmber
                                            else -> StatusGreen
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    isOutOfStock -> "Out of Stock"
                                    isLowStock -> "Low: ${product.currentStock.toInt()} ${product.unit}"
                                    else -> "${product.currentStock.toInt()} ${product.unit} on-hand"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isOutOfStock -> StatusRed
                                    isLowStock -> StatusAmber
                                    else -> StatusGreen
                                }
                            )
                        }
                    }
                } else {
                    Text("Non-tracked", fontSize = 11.sp, color = TextMuted)
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (product.isTracked) {
                        OutlinedButton(
                            onClick = onRestock,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(13.dp), tint = BrandPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                        }
                    }

                    FilledTonalButton(
                        onClick = onAddToCart,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Cart", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickRestockDialog(
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
    val projectedStock = product.currentStock + qty

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
                        Text("INTAKE & RESTOCK", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandPrimary)
                        Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stock change preview banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Stock", fontSize = 10.sp, color = TextSecondary)
                            Text("${product.currentStock.toInt()} ${product.unit}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("New Stock Level", fontSize = 10.sp, color = TextSecondary)
                            Text("${projectedStock.toInt()} ${product.unit}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = StatusGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Increment Presets
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 25, 50, 100).forEach { amount ->
                        OutlinedButton(
                            onClick = { qtyText = amount.toString() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("+$amount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Purchase Unit Cost (Rs.)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier / Distributor Name") },
                    placeholder = { Text("e.g. Local Distributor", color = TextMuted) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = LightBorder,
                        cursorColor = BrandPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Cost
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Intake Value:", fontSize = 12.sp, color = TextSecondary)
                    Text(CurrencyUtils.formatLkr(totalExpense), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = BrandPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (qty > 0) {
                            onConfirm(qty, cost, supplier.ifBlank { "Direct Intake" })
                        }
                    },
                    enabled = qty > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONFIRM RESTOCK (+$qty)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
