package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SupplierEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.util.WhatsAppHelper

data class BatchReorderItemState(
    val product: ProductEntity,
    var quantityText: String,
    var costText: String
) {
    val quantity: Double get() = quantityText.toDoubleOrNull() ?: 0.0
    val unitCost: Double get() = costText.toDoubleOrNull() ?: product.costPrice
    val lineTotal: Double get() = quantity * unitCost
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchReorderDialog(
    lowStockList: List<ProductEntity>,
    suppliers: List<SupplierEntity>,
    profile: BusinessProfileEntity?,
    onBulkReceiveStock: (List<Triple<ProductEntity, Double, Double>>, supplierName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val itemStates = remember(lowStockList) {
        mutableStateListOf<BatchReorderItemState>().apply {
            addAll(
                lowStockList.map { p ->
                    val deficit = (p.lowStockThreshold * 2.0) - p.currentStock
                    val suggested = maxOf(10.0, if (deficit > 0) kotlin.math.ceil(deficit) else 10.0)
                    val cost = if (p.costPrice > 0) p.costPrice else (p.sellingPrice * 0.75)
                    BatchReorderItemState(
                        product = p,
                        quantityText = if (suggested % 1.0 == 0.0) suggested.toInt().toString() else suggested.toString(),
                        costText = if (cost % 1.0 == 0.0) cost.toInt().toString() else "%.2f".format(cost)
                    )
                }
            )
        }
    }

    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var customSupplierPhone by remember { mutableStateOf("") }
    var batchNotes by remember { mutableStateOf("") }

    val grandTotal = itemStates.sumOf { it.lineTotal }
    val effectiveSupplierName = selectedSupplier?.name ?: "Supplier"
    val effectiveSupplierPhone = selectedSupplier?.phone ?: customSupplierPhone

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .testTag("batch_reorder_dialog")
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
                                .background(WhatsAppGreenBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = WhatsAppGreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "BATCH STOCK REORDER",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                "${itemStates.size} items requiring stock replenish",
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

                // Supplier Selector & WhatsApp destination
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WhatsAppGreenBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("REORDER TARGET SUPPLIER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = WhatsAppGreenDark)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (suppliers.isNotEmpty()) {
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
                                        label = { Text(sup.name.take(12), fontSize = 11.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        OutlinedTextField(
                            value = customSupplierPhone.ifBlank { selectedSupplier?.phone ?: "" },
                            onValueChange = { customSupplierPhone = it },
                            label = { Text("Supplier WhatsApp Number") },
                            placeholder = { Text("077 123 4567") },
                            shape = RoundedCornerShape(10.dp),
                            textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp),
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
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Low stock items list with quantities
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(itemStates) { index, itemState ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = LightSurface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            itemState.product.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            "Stock: ${itemState.product.currentStock.toInt()} ${itemState.product.unit} (Min: ${itemState.product.lowStockThreshold.toInt()})",
                                            fontSize = 11.sp,
                                            color = StatusRed
                                        )
                                    }

                                    Text(
                                        CurrencyUtils.formatLkr(itemState.lineTotal),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = itemState.quantityText,
                                        onValueChange = { newVal ->
                                            itemStates[index] = itemState.copy(quantityText = newVal)
                                        },
                                        label = { Text("Order Qty (${itemState.product.unit})") },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.2f),
                                        textStyle = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = itemState.costText,
                                        onValueChange = { newVal ->
                                            itemStates[index] = itemState.copy(costText = newVal)
                                        },
                                        label = { Text("Cost (Rs.)") },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary & Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL ESTIMATED ORDER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            CurrencyUtils.formatLkr(grandTotal),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }

                    Badge(containerColor = WhatsAppGreen) {
                        Text(
                            "${itemStates.size} Items",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: WhatsApp Dispatch & 1-Tap Bulk Intake
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val storeName = profile?.name ?: "Our Store"
                            val storePhone = profile?.phone ?: ""
                            val listToOrder = itemStates.map { Triple(it.product, it.quantity, it.unitCost) }
                            val msg = WhatsAppHelper.buildBatchReorderMessage(
                                storeName = storeName,
                                storePhone = storePhone,
                                supplierName = effectiveSupplierName,
                                items = listToOrder,
                                notes = batchNotes
                            )
                            WhatsAppHelper.sendWhatsAppMessage(context, effectiveSupplierPhone, msg)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("batch_whatsapp_send_button")
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SEND BATCH PO VIA WHATSAPP", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val listToReceive = itemStates.filter { it.quantity > 0 }.map { Triple(it.product, it.quantity, it.unitCost) }
                            if (listToReceive.isNotEmpty()) {
                                onBulkReceiveStock(listToReceive, effectiveSupplierName)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("bulk_receive_stock_button")
                    ) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Tap Receive & Restock All Items", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
