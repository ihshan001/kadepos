package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SupplierEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.util.WhatsAppHelper

val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppGreenDark = Color(0xFF128C7E)
val WhatsAppGreenBg = Color(0xFFE8F8EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowStockRestockDialog(
    initialProduct: ProductEntity,
    lowStockList: List<ProductEntity>,
    suppliers: List<SupplierEntity>,
    profile: BusinessProfileEntity?,
    onReceiveStock: (productId: Long, qty: Double, unitCost: Double, supplierName: String) -> Unit,
    onOpenBatchReorder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentProduct by remember { mutableStateOf(initialProduct) }

    // Suggested calculation: max(10, threshold * 2 - currentStock)
    val calculatedSuggested = remember(currentProduct) {
        val deficit = (currentProduct.lowStockThreshold * 2.0) - currentProduct.currentStock
        maxOf(10.0, if (deficit > 0) kotlin.math.ceil(deficit) else 10.0)
    }

    var reorderQtyText by remember(currentProduct) {
        mutableStateOf(if (calculatedSuggested % 1.0 == 0.0) calculatedSuggested.toInt().toString() else calculatedSuggested.toString())
    }

    var unitCostText by remember(currentProduct) {
        val cost = if (currentProduct.costPrice > 0) currentProduct.costPrice else (currentProduct.sellingPrice * 0.75)
        mutableStateOf(if (cost % 1.0 == 0.0) cost.toInt().toString() else "%.2f".format(cost))
    }

    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var customSupplierPhone by remember { mutableStateOf("") }
    var customSupplierName by remember { mutableStateOf("") }
    var orderNote by remember { mutableStateOf("") }
    var showSupplierDropdown by remember { mutableStateOf(false) }

    val qty = reorderQtyText.toDoubleOrNull() ?: 0.0
    val unitCost = unitCostText.toDoubleOrNull() ?: 0.0
    val totalEstimatedCost = qty * unitCost

    val effectiveSupplierName = selectedSupplier?.name ?: customSupplierName.ifBlank { "Distributor / Supplier" }
    val effectiveSupplierPhone = selectedSupplier?.phone ?: customSupplierPhone

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("low_stock_restock_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StatusRedBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "LOW STOCK RESTOCK",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = StatusRed
                            )
                            Text(
                                "Instant Reorder & Stock Intake",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // If multiple items are low, display switcher chips
                if (lowStockList.size > 1) {
                    Text(
                        "LOW STOCK ITEMS (${lowStockList.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(lowStockList) { prod ->
                            val isSelected = prod.id == currentProduct.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) StatusRed else StatusRedBg,
                                border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.clickable {
                                    currentProduct = prod
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        prod.name.take(14),
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "(${prod.currentStock.toInt()})",
                                        color = if (isSelected) Color.White else StatusRed,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Product Summary Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BrandSurface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            currentProduct.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            "Category: ${currentProduct.category} ${if (currentProduct.barcode.isNotBlank()) "• Code: ${currentProduct.barcode}" else ""}",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = StatusRed
                                    ) {
                                        Text(
                                            "DEFICIT",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = LightBorder)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Current On-Hand", fontSize = 11.sp, color = TextSecondary)
                                        val currStr = if (currentProduct.currentStock % 1.0 == 0.0) currentProduct.currentStock.toInt().toString() else currentProduct.currentStock.toString()
                                        Text(
                                            "$currStr ${currentProduct.unit}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = StatusRed
                                        )
                                    }

                                    Column {
                                        Text("Min Threshold", fontSize = 11.sp, color = TextSecondary)
                                        val threshStr = if (currentProduct.lowStockThreshold % 1.0 == 0.0) currentProduct.lowStockThreshold.toInt().toString() else currentProduct.lowStockThreshold.toString()
                                        Text(
                                            "$threshStr ${currentProduct.unit}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                    }

                                    Column {
                                        Text("Suggested Order", fontSize = 11.sp, color = TextSecondary)
                                        Text(
                                            "+${calculatedSuggested.toInt()} ${currentProduct.unit}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = BrandPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Order Quantity & Cost Input Form
                    item {
                        Text(
                            "REORDER QUANTITY & COST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = reorderQtyText,
                                onValueChange = { reorderQtyText = it },
                                label = { Text("Order Quantity (${currentProduct.unit}) *") },
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = BrandPrimary,
                                    unfocusedBorderColor = LightBorder,
                                    cursorColor = BrandPrimary
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.2f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = unitCostText,
                                onValueChange = { unitCostText = it },
                                label = { Text("Unit Cost (Rs.)") },
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = BrandPrimary,
                                    unfocusedBorderColor = LightBorder,
                                    cursorColor = BrandPrimary
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick increment pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(5, 10, 20, 50, 100).forEach { inc ->
                                SuggestionChip(
                                    onClick = {
                                        val currentVal = reorderQtyText.toDoubleOrNull() ?: 0.0
                                        val nextVal = (currentVal + inc).coerceAtLeast(1.0)
                                        reorderQtyText = if (nextVal % 1.0 == 0.0) nextVal.toInt().toString() else nextVal.toString()
                                    },
                                    label = { Text("+$inc", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // Supplier & WhatsApp Connection Section
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = WhatsAppGreenBg),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Chat,
                                            contentDescription = null,
                                            tint = WhatsAppGreenDark,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "SUPPLIER WHATSAPP CONNECT",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = WhatsAppGreenDark
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Supplier selection or custom input
                                if (suppliers.isNotEmpty()) {
                                    Text("Select Registered Supplier:", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        suppliers.take(3).forEach { sup ->
                                            val isSel = selectedSupplier?.id == sup.id
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    selectedSupplier = sup
                                                    customSupplierPhone = sup.phone
                                                },
                                                label = {
                                                    Text(
                                                        sup.name.take(12),
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                OutlinedTextField(
                                    value = customSupplierPhone.ifBlank { selectedSupplier?.phone ?: "" },
                                    onValueChange = { customSupplierPhone = it },
                                    label = { Text("Supplier WhatsApp / Phone Number") },
                                    placeholder = { Text("e.g. 077 123 4567") },
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = WhatsAppGreenDark,
                                        unfocusedBorderColor = LightBorder,
                                        cursorColor = WhatsAppGreenDark
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = orderNote,
                                    onValueChange = { orderNote = it },
                                    label = { Text("Purchase Note / Urgency (Optional)") },
                                    placeholder = { Text("e.g. Urgent morning delivery required") },
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = WhatsAppGreenDark,
                                        unfocusedBorderColor = LightBorder,
                                        cursorColor = WhatsAppGreenDark
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Total Order Valuation Pill
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Estimated PO Total:", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text(
                                CurrencyUtils.formatLkr(totalEstimatedCost),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: WhatsApp Reorder & Direct Restock
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1-Tap WhatsApp Reorder
                    Button(
                        onClick = {
                            val storeName = profile?.name ?: "Our Store"
                            val storePhone = profile?.phone ?: ""
                            val msg = WhatsAppHelper.buildStockReorderMessage(
                                storeName = storeName,
                                storePhone = storePhone,
                                product = currentProduct,
                                requestedQty = qty,
                                unitCost = unitCost,
                                supplierName = effectiveSupplierName,
                                note = orderNote
                            )
                            WhatsAppHelper.sendWhatsAppMessage(context, effectiveSupplierPhone, msg)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("whatsapp_reorder_button")
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "REORDER VIA WHATSAPP",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    // Direct Receive / Restock Into Database
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (qty > 0) {
                                    onReceiveStock(currentProduct.id, qty, unitCost, effectiveSupplierName)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("direct_restock_intake_button")
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Direct Restock (+$qty)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (lowStockList.size > 1) {
                            OutlinedButton(
                                onClick = onOpenBatchReorder,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("batch_reorder_button")
                            ) {
                                Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Batch All (${lowStockList.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
