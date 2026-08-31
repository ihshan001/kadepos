package com.example.ui.screens.expenses

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExpenseEntity
import com.example.data.model.Permission
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel
import java.util.Calendar

/**
 * Expenses — "what did the shop spend today".
 *
 * Recording an expense used to mean filling a form. Now it is: tap the picture
 * of what you spent on, type the amount, done. Two taps and a number.
 */
private data class ExpenseKind(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

private val expenseKinds = listOf(
    ExpenseKind("Rent", Icons.Default.Store, StatusBlue),
    ExpenseKind("Electricity", Icons.Default.Bolt, StatusAmber),
    ExpenseKind("Water", Icons.Default.WaterDrop, StatusBlue),
    ExpenseKind("Transport", Icons.Default.LocalShipping, BrandTealPrimary),
    ExpenseKind("Staff pay", Icons.Default.Groups, StatusGreen),
    ExpenseKind("Tea & food", Icons.Default.Restaurant, StatusAmber),
    ExpenseKind("Repairs", Icons.Default.Build, StatusRed),
    ExpenseKind("Packaging", Icons.Default.Inventory2, TextSecondary),
    ExpenseKind("Phone & internet", Icons.Default.Smartphone, StatusBlue),
    ExpenseKind("Something else", Icons.Default.MoreHoriz, TextSecondary)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val permissions by viewModel.permissions.collectAsState()
    if (permissions.cannot(Permission.MANAGE_EXPENSES)) {
        LockedScreenNotice(
            message = permissions.denialMessage(Permission.MANAGE_EXPENSES),
            onBack = onBack
        )
        return
    }

    val expenses by viewModel.expenses.collectAsState()
    var adding by remember { mutableStateOf<ExpenseKind?>(null) }
    var confirmDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todayTotal = remember(expenses) {
        expenses.filter { it.timestamp >= startOfToday }.sumOf { it.amount }
    }
    val monthTotal = remember(expenses) {
        expenses.filter { it.timestamp >= startOfMonth }.sumOf { it.amount }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Expenses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            item {
                MoneyHeadline(
                    label = "SPENT TODAY",
                    amount = todayTotal,
                    caption = "${CurrencyUtils.formatLkr(monthTotal)} so far this month",
                    accent = StatusRed
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("What did you spend on?")
            }

            // A grid of taps. No form until an amount is needed.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    expenseKinds.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { kind ->
                                ExpenseTile(
                                    kind = kind,
                                    modifier = Modifier.weight(1f),
                                    onClick = { adding = kind }
                                )
                            }
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (expenses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel("Recent")
                }
                items(expenses.take(20), key = { it.id }) { expense ->
                    ExpenseRow(expense = expense, onLongPress = { confirmDelete = expense })
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HintCard(
                        text = "Recording what you spend means your profit figure is real, not just sales.",
                        tone = HintTone.INFO
                    )
                }
            }
        }
    }

    adding?.let { kind ->
        MoneySheet(
            title = kind.label,
            subtitle = "How much did you spend?",
            confirmLabel = "Save expense",
            accent = StatusRed,
            onConfirm = { amount ->
                viewModel.addExpense(kind.label, amount, "CASH", "", "")
                adding = null
            },
            onDismiss = { adding = null }
        )
    }

    confirmDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove this expense?") },
            text = {
                Text("${expense.category} — ${CurrencyUtils.formatLkr(expense.amount)}")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    confirmDelete = null
                }) {
                    Text("Remove", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun ExpenseTile(
    kind: ExpenseKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = LightSurface,
        border = BorderStroke(1.dp, LightBorder),
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick)
            .testTag("expense_${kind.label}")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(kind.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(kind.icon, contentDescription = null, tint = kind.tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                kind.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, onLongPress: () -> Unit) {
    val kind = expenseKinds.firstOrNull { it.label == expense.category }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LightSurface,
        border = BorderStroke(1.dp, LightBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background((kind?.tint ?: TextSecondary).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    kind?.icon ?: Icons.Default.Receipt,
                    contentDescription = null,
                    tint = kind?.tint ?: TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.category,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    CurrencyUtils.formatDateTime(expense.timestamp),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            Text(
                CurrencyUtils.formatLkr(expense.amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = StatusRed
            )
        }
    }
}
