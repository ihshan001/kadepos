package com.example.ui.screens.products

import androidx.compose.animation.AnimatedVisibility
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
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: PosViewModel
) {
    val products by viewModel.products.collectAsState()
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, FAVOURITES, LOW_STOCK, OUT_OF_STOCK, or category name
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var restockingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = remember(products) {
        products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredProducts = remember(products, searchQuery, selectedFilter) {
        products.filter { p ->
            val matchFilter = when (selectedFilter) {
                "ALL" -> true
                "FAVOURITES" -> p.isFavourite
                "LOW_STOCK" -> p.isTracked && p.currentStock <= p.lowStockThreshold && p.currentStock > 0
                "OUT_OF_STOCK" -> p.isTracked && p.currentStock <= 0
                else -> p.category.equals(selectedFilter, ignoreCase = true)
            }

            val matchQuery = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcode.contains(searchQuery, ignoreCase = true) ||
                    p.sku.contains(searchQuery, ignoreCase = true) ||
                    p.category.contains(searchQuery, ignoreCase = true)

            matchFilter && matchQuery
        }
    }

    val totalCatalogValuation = remember(products) {
        products.filter { it.isTracked && it.currentStock > 0 }.sumOf { it.costPrice * it.currentStock }
    }
    val lowStockCount = remember(products) {
        products.count { it.isTracked && it.currentStock <= it.lowStockThreshold }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Product Catalog & Items", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text("${products.size} total items registered", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_product_top_btn")) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Product", tint = BrandTealPrimary, modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BrandTealPrimary,
                contentColor = Color.White,
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
                            color = BrandTealPrimary
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
                            color = BrandMintSurface
                        ) {
                            Text(
                                "${products.count { it.isFavourite }} Pinned",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandTealDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search product name, category, barcode, SKU...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(20.dp)) },
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
                    focusedBorderColor = BrandTealPrimary,
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
                        label = "All (${products.size})",
                        isSelected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" }
                    )
                }
                item {
                    ProductFilterChip(
                        label = "⭐ Favourites",
                        isSelected = selectedFilter == "FAVOURITES",
                        onClick = { selectedFilter = "FAVOURITES" }
                    )
                }
                item {
                    ProductFilterChip(
                        label = "⚠️ Low Stock ($lowStockCount)",
                        isSelected = selectedFilter == "LOW_STOCK",
                        onClick = { selectedFilter = "LOW_STOCK" },
                        highlightAmber = true
                    )
                }
                items(categories) { cat ->
                    val countInCat = products.count { it.category.equals(cat, ignoreCase = true) }
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
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary)
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
                            onEdit = { editingProduct = product },
                            onRestock = { restockingProduct = product },
                            onAddToCart = { viewModel.addToCart(product) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingProduct != null) {
        ProductEditDialog(
            product = editingProduct,
            onSave = { id, name, sellPrice, costPrice, barcode, sku, cat, unit, stock, low, tracked, fav ->
                viewModel.saveProduct(id, name, sellPrice, costPrice, barcode, sku, cat, unit, stock, low, tracked, fav)
                showAddDialog = false
                editingProduct = null
            },
            onDelete = if (editingProduct != null) {
                {
                    viewModel.deleteProduct(editingProduct!!.id)
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
    highlightAmber: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isSelected -> BrandTealPrimary
            highlightAmber -> StatusAmberBg
            else -> LightSurface
        },
        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isSelected -> Color.White
                highlightAmber -> StatusAmber
                else -> TextPrimary
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onRestock: () -> Unit,
    onAddToCart: () -> Unit
) {
    val profit = (product.sellingPrice - product.costPrice).coerceAtLeast(0.0)
    val marginPct = if (product.sellingPrice > 0) ((profit / product.sellingPrice) * 100).toInt() else 0
    val isLowStock = product.isTracked && product.currentStock <= product.lowStockThreshold
    val isOutOfStock = product.isTracked && product.currentStock <= 0

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("product_item_${product.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
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
                                text = product.category.ifBlank { "General" },
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
                }

                // Price & Margin
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyUtils.formatLkr(product.sellingPrice),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = BrandTealPrimary
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
                            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(13.dp), tint = BrandTealPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandTealPrimary)
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
                        Text("INTAKE & RESTOCK", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandTealPrimary)
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
                    colors = CardDefaults.cardColors(containerColor = BrandMintSurface)
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
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(16.dp))
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
                    label = { Text("Purchase Unit Cost (Rs.)") },
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
                    label = { Text("Supplier / Distributor Name") },
                    placeholder = { Text("e.g. Local Distributor", color = TextMuted) },
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

                // Summary Cost
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
                    Text("CONFIRM RESTOCK (+$qty)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ProductEditDialog(
    product: ProductEntity?,
    onSave: (
        id: Long,
        name: String,
        sellPrice: Double,
        costPrice: Double,
        barcode: String,
        sku: String,
        category: String,
        unit: String,
        stock: Double,
        lowStock: Double,
        isTracked: Boolean,
        isFavourite: Boolean
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isEdit = product != null
    var name by remember { mutableStateOf(product?.name ?: "") }
    var sellPriceText by remember { mutableStateOf(product?.sellingPrice?.toInt()?.toString() ?: "") }
    var costPriceText by remember { mutableStateOf(if (product != null && product.costPrice > 0) product.costPrice.toInt().toString() else "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var sku by remember { mutableStateOf(product?.sku ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "General") }
    var unit by remember { mutableStateOf(product?.unit ?: "Piece") }
    var stockText by remember { mutableStateOf(product?.currentStock?.toInt()?.toString() ?: "10") }
    var lowStockText by remember { mutableStateOf(product?.lowStockThreshold?.toInt()?.toString() ?: "3") }
    var isTracked by remember { mutableStateOf(product?.isTracked ?: true) }
    var isFavourite by remember { mutableStateOf(product?.isFavourite ?: false) }

    val sellPrice = sellPriceText.toDoubleOrNull() ?: 0.0
    val costPrice = costPriceText.toDoubleOrNull() ?: 0.0
    val profit = (sellPrice - costPrice).coerceAtLeast(0.0)
    val margin = if (sellPrice > 0) ((profit / sellPrice) * 100).toInt() else 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.90f)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEdit) "EDIT PRODUCT" else "NEW PRODUCT ENTRY",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = BrandTealPrimary
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // 1. Basic Details
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name *") },
                        placeholder = { Text("e.g. Milk 1L, Red Rice 1kg, Coca Cola...") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BrandTealPrimary,
                            unfocusedBorderColor = LightBorder,
                            cursorColor = BrandTealPrimary
                        ),
                        singleLine = true
                    )
                }

                // Category & Unit Presets
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            placeholder = { Text("Beverages, Grocery...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f),
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

                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Unit") },
                            placeholder = { Text("Piece, Kg...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f),
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
                    }
                }

                // Unit Presets quick click
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Piece", "Kg", "Pack", "Bottle", "Box", "Litre", "Meter").forEach { u ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (unit.equals(u, ignoreCase = true)) BrandTealPrimary else LightSurfaceVariant,
                                    modifier = Modifier.clickable { unit = u }
                                ) {
                                    Text(
                                        u,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (unit.equals(u, ignoreCase = true)) Color.White else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Pricing & Live Margin
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandMintSurface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sellPriceText,
                                    onValueChange = { sellPriceText = it },
                                    label = { Text("Selling Price (Rs.) *") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
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

                                OutlinedTextField(
                                    value = costPriceText,
                                    onValueChange = { costPriceText = it },
                                    label = { Text("Cost Price (Rs.)") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
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
                            }

                            if (sellPrice > 0 && costPrice > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Profit: ${CurrencyUtils.formatLkr(profit)} / unit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                                    Text("Gross Margin: $margin%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandTealDark)
                                }
                            }
                        }
                    }
                }

                // 3. Barcode / SKU
                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode / EAN") },
                        placeholder = { Text("Scan or enter barcode") },
                        trailingIcon = {
                            IconButton(onClick = { barcode = "890" + (10000000..99999999).random().toString() }) {
                                Icon(Icons.Default.QrCode, contentDescription = "Auto Generate", tint = BrandTealPrimary)
                            }
                        },
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
                }

                // 4. Inventory tracking controls
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Track Inventory Count", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Auto-deduct on checkout", fontSize = 11.sp, color = TextSecondary)
                                }
                                Switch(checked = isTracked, onCheckedChange = { isTracked = it })
                            }

                            if (isTracked) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = stockText,
                                        onValueChange = { stockText = it },
                                        label = { Text("Opening Stock") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
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

                                    OutlinedTextField(
                                        value = lowStockText,
                                        onValueChange = { lowStockText = it },
                                        label = { Text("Low Alert") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
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
                                }
                            }
                        }
                    }
                }

                // 5. Pin to Favourites
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pin to Favourites / Quick POS Grid", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Shows prominently on Sell screen", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = isFavourite, onCheckedChange = { isFavourite = it })
                    }
                }

                // 6. Action buttons
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val sell = sellPriceText.toDoubleOrNull() ?: 0.0
                            val cost = costPriceText.toDoubleOrNull() ?: 0.0
                            val stock = stockText.toDoubleOrNull() ?: 0.0
                            val low = lowStockText.toDoubleOrNull() ?: 3.0
                            if (name.isNotBlank() && sell > 0) {
                                onSave(
                                    product?.id ?: 0L,
                                    name,
                                    sell,
                                    cost,
                                    barcode,
                                    sku,
                                    category.ifBlank { "General" },
                                    unit.ifBlank { "Piece" },
                                    stock,
                                    low,
                                    isTracked,
                                    isFavourite
                                )
                            }
                        },
                        enabled = name.isNotBlank() && sellPriceText.toDoubleOrNull() != null,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (isEdit) "SAVE CHANGES" else "CREATE PRODUCT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (isEdit && onDelete != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete Product from Catalog", color = StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
