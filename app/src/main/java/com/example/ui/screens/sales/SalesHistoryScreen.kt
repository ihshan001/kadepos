package com.example.ui.screens.sales

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.ui.screens.sell.SaleCompleteDialog
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosTab
import com.example.data.model.Permission
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(
    viewModel: PosViewModel
) {
    val screenPermissions by viewModel.permissions.collectAsState()
    if (screenPermissions.cannot(Permission.VIEW_SALES_HISTORY)) {
        com.example.ui.components.LockedScreenNotice(
            message = screenPermissions.denialMessage(Permission.VIEW_SALES_HISTORY)
        )
        return
    }

    val sales by viewModel.sales.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf("ALL") } // ALL, TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH
    var selectedPaymentFilter by remember { mutableStateOf("ALL") } // ALL, CASH, CARD, CREDIT, REFUNDED

    var viewingSale by remember { mutableStateOf<SaleEntity?>(null) }
    var viewingItems by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    // Date calculations for filtering
    val now = remember { System.currentTimeMillis() }
    val todayStart = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val yesterdayStart = remember(todayStart) { todayStart - (24 * 60 * 60 * 1000L) }
    val thisWeekStart = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val thisMonthStart = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val filteredSales = remember(sales, searchQuery, selectedPeriod, selectedPaymentFilter) {
        sales.filter { sale ->
            val matchPeriod = when (selectedPeriod) {
                "TODAY" -> sale.timestamp >= todayStart
                "YESTERDAY" -> sale.timestamp in yesterdayStart until todayStart
                "THIS_WEEK" -> sale.timestamp >= thisWeekStart
                "THIS_MONTH" -> sale.timestamp >= thisMonthStart
                else -> true
            }

            val matchPayment = when (selectedPaymentFilter) {
                "CASH" -> sale.paymentMethod == "CASH" && sale.status != "REFUNDED"
                "CARD" -> sale.paymentMethod == "CARD" && sale.status != "REFUNDED"
                "CREDIT" -> sale.paymentMethod == "CREDIT" && sale.status != "REFUNDED"
                "REFUNDED" -> sale.status.contains("REFUND", ignoreCase = true)
                else -> true
            }

            val matchQuery = searchQuery.isBlank() ||
                    sale.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    sale.customerName.contains(searchQuery, ignoreCase = true) ||
                    sale.cashierName.contains(searchQuery, ignoreCase = true)

            matchPeriod && matchPayment && matchQuery
        }
    }

    val validSales = remember(filteredSales) { filteredSales.filter { it.status != "VOID" && !it.status.contains("REFUND") } }
    val totalRevenue = remember(validSales) { validSales.sumOf { it.totalAmount } }
    val cashRevenue = remember(validSales) { validSales.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount } }
    val cardRevenue = remember(validSales) { validSales.filter { it.paymentMethod == "CARD" }.sumOf { it.totalAmount } }
    val creditDue = remember(validSales) { validSales.filter { it.paymentMethod == "CREDIT" }.sumOf { it.totalAmount } }
    val avgTicket = remember(validSales, totalRevenue) {
        if (validSales.isNotEmpty()) totalRevenue / validSales.size else 0.0
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sales & Bills Ledger", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text("${filteredSales.size} transactions listed", fontSize = 11.sp, color = TextSecondary)
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
            // 1. KPI Summary Cards Grid
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().testTag("sales_history_kpi_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (selectedPeriod) {
                                    "TODAY" -> "TODAY'S NET REVENUE"
                                    "YESTERDAY" -> "YESTERDAY'S NET REVENUE"
                                    "THIS_WEEK" -> "THIS WEEK'S NET REVENUE"
                                    "THIS_MONTH" -> "THIS MONTH'S NET REVENUE"
                                    else -> "TOTAL FILTERED REVENUE"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandTealDark
                            )
                            Text(
                                CurrencyUtils.formatLkr(totalRevenue),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandTealPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = BrandTealPrimary
                        ) {
                            Text(
                                text = "${validSales.size} Orders",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BrandTealPrimary.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Cash In Hand", fontSize = 10.sp, color = TextSecondary)
                            Text(CurrencyUtils.formatLkr(cashRevenue), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column {
                            Text("Card / Digital", fontSize = 10.sp, color = TextSecondary)
                            Text(CurrencyUtils.formatLkr(cardRevenue), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column {
                            Text("Credit / Due", fontSize = 10.sp, color = TextSecondary)
                            Text(
                                CurrencyUtils.formatLkr(creditDue),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (creditDue > 0) StatusAmber else TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Avg. Bill", fontSize = 10.sp, color = TextSecondary)
                            Text(CurrencyUtils.formatLkr(avgTicket), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandTealDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search invoice #, customer name, cashier...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sales_history_search_input"),
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

            // 3. Time Period Chips Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val periods = listOf(
                    "ALL" to "All Time",
                    "TODAY" to "Today",
                    "YESTERDAY" to "Yesterday",
                    "THIS_WEEK" to "This Week",
                    "THIS_MONTH" to "This Month"
                )
                items(periods) { (key, label) ->
                    val isSelected = selectedPeriod == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) BrandTealPrimary else LightSurface,
                        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.clickable { selectedPeriod = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Payment Method & Status Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Real vector icons, not emoji: emoji are a different size and
                // colour on every phone and cannot be tinted with the selection.
                val filters = listOf(
                    Triple("ALL", "All bills", null),
                    Triple("CASH", "Cash", Icons.Default.Payments),
                    Triple("CARD", "Card", Icons.Default.CreditCard),
                    Triple("CREDIT", "Credit", Icons.Default.Book),
                    Triple("REFUNDED", "Refunded", Icons.AutoMirrored.Filled.Undo)
                )
                items(filters) { (key, label, icon) ->
                    val isSelected = selectedPaymentFilter == key
                    val tint = if (isSelected) Color.White else TextSecondary
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) BrandTealDark else LightSurfaceVariant,
                        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.clickable { selectedPaymentFilter = key }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            if (icon != null) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tint
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Invoices List
            if (filteredSales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(LightSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No matching invoices found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            "Try clearing filters or search terms",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                selectedPeriod = "ALL"
                                selectedPaymentFilter = "ALL"
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset All Filters", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSales) { sale ->
                        SaleHistoryCard(
                            sale = sale,
                            onClick = {
                                coroutineScope.launch {
                                    isLoadingDetails = true
                                    val items = viewModel.getSaleItems(sale.id)
                                    viewingItems = if (items.isNotEmpty()) items else {
                                        listOf(
                                            SaleItemEntity(
                                                saleId = sale.id,
                                                productName = "Sale item #${sale.invoiceNumber}",
                                                unitPrice = sale.totalAmount,
                                                quantity = 1.0,
                                                lineTotal = sale.totalAmount
                                            )
                                        )
                                    }
                                    viewingSale = sale
                                    isLoadingDetails = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Full Sale Details / Thermal Receipt / WhatsApp Dialog
    if (viewingSale != null) {
        SaleCompleteDialog(
            sale = viewingSale!!,
            items = viewingItems,
            profile = profile,
            viewModel = viewModel,
            onDismiss = { viewingSale = null }
        )
    }
}

@Composable
fun SaleHistoryCard(
    sale: SaleEntity,
    onClick: () -> Unit
) {
    val isRefunded = sale.status.contains("REFUND", ignoreCase = true)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sale_card_${sale.invoiceNumber}")
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRefunded -> StatusRedBg
                                sale.paymentMethod == "CASH" -> StatusGreenBg
                                sale.paymentMethod == "CARD" -> BrandMintSurface
                                else -> StatusAmberBg
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            isRefunded -> Icons.Default.RotateLeft
                            sale.paymentMethod == "CASH" -> Icons.Default.Payments
                            sale.paymentMethod == "CARD" -> Icons.Default.CreditCard
                            else -> Icons.Default.MenuBook
                        },
                        contentDescription = null,
                        tint = when {
                            isRefunded -> StatusRed
                            sale.paymentMethod == "CASH" -> StatusGreen
                            sale.paymentMethod == "CARD" -> BrandTealPrimary
                            else -> StatusAmber
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sale.invoiceNumber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PaymentBadge(method = sale.paymentMethod)
                        if (sale.discountAmount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BrandMintSurface
                            ) {
                                Text(
                                    "-${CurrencyUtils.formatLkr(sale.discountAmount)}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandTealPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${CurrencyUtils.formatDateTime(sale.timestamp)} • ${sale.customerName}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatLkr(sale.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (isRefunded) StatusRed else BrandTealPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isRefunded -> StatusRedBg
                        sale.status == "COMPLETED" -> StatusGreenBg
                        else -> StatusAmberBg
                    }
                ) {
                    Text(
                        text = if (isRefunded) "REFUNDED" else sale.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isRefunded -> StatusRed
                            sale.status == "COMPLETED" -> StatusGreen
                            else -> StatusAmber
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentBadge(method: String) {
    val (bg, fg) = when (method) {
        "CASH" -> StatusGreenBg to StatusGreen
        "CARD" -> BrandMintSurface to BrandTealPrimary
        "CREDIT" -> StatusAmberBg to StatusAmber
        else -> LightSurfaceVariant to TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = method,
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
