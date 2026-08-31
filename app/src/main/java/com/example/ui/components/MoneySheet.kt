package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import kotlin.math.roundToLong

/**
 * The one way money is entered anywhere in KadePOS.
 *
 * Settling a customer's credit, paying a supplier, recording an expense and
 * receiving stock are all the same action to a shop owner: type a number and
 * confirm. They used to be four different dialogs with four different layouts.
 * Now they are this sheet, so learning it once is enough.
 *
 * Deliberate choices:
 *  - Its own big number pad. The Android keyboard is small, slow, and puts
 *    letters next to digits. Nobody mistypes on a pad this size.
 *  - "Pay all" and rounded shortcuts, because most payments are the full
 *    amount or a round number.
 *  - The amount is echoed in words-sized type so it is checkable at a glance
 *    before money changes hands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneySheet(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    suggestedAmount: Double? = null,
    suggestedLabel: String = "Pay all",
    maxAmount: Double? = null,
    accent: Color = BrandTealPrimary,
    footer: (@Composable () -> Unit)? = null
) {
    var digits by remember { mutableStateOf("") }
    val amount = remember(digits) { (digits.toLongOrNull() ?: 0L).toDouble() }
    val tooMuch = maxAmount != null && amount > maxAmount + 0.001
    val canConfirm = amount > 0.0 && !tooMuch

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(
                subtitle,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // The running amount, large enough to double-check across a counter.
            Text(
                CurrencyUtils.formatLkr(amount),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (tooMuch) StatusRed else accent,
                modifier = Modifier.testTag("money_amount")
            )

            if (tooMuch && maxAmount != null) {
                Text(
                    "That is more than the ${CurrencyUtils.formatLkr(maxAmount)} owing",
                    fontSize = 12.sp,
                    color = StatusRed,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shortcuts: the exact amount owed, plus sensible round numbers.
            val shortcuts = buildList {
                if (suggestedAmount != null && suggestedAmount > 0) {
                    add(suggestedLabel to suggestedAmount)
                }
                listOf(100.0, 500.0, 1000.0, 5000.0).forEach { step ->
                    if (maxAmount == null || step <= maxAmount) add(CurrencyUtils.formatLkr(step) to step)
                }
            }.take(4)

            if (shortcuts.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    shortcuts.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = BrandMintSurface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { digits = value.roundToLong().toString() }
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Number pad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("00", "0", "del")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            MoneyKey(
                                key = key,
                                onClick = {
                                    digits = when (key) {
                                        "del" -> digits.dropLast(1)
                                        else -> (digits + key).trimStart('0').take(9)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (footer != null) {
                Spacer(modifier = Modifier.height(14.dp))
                footer()
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onConfirm(amount) },
                enabled = canConfirm,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("money_confirm")
            ) {
                Text(confirmLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowScope.MoneyKey(key: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = LightSurfaceVariant,
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clickable(onClick = onClick)
            .testTag("money_key_$key")
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (key == "del") {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = TextSecondary
                )
            } else {
                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}

/**
 * A person or company with money attached — a customer who owes, or a supplier
 * who is owed. Both lists looked different before; they are the same shape of
 * problem, so they now look the same.
 */
@Composable
fun MoneyPersonRow(
    name: String,
    detail: String,
    amount: Double,
    amountLabel: String,
    isSettled: Boolean,
    initialsColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = LightSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSettled) LightSurfaceVariant else initialsColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.trim().take(1).uppercase().ifBlank { "?" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSettled) TextMuted else initialsColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(detail, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isSettled) "Settled" else CurrencyUtils.formatLkr(amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSettled) StatusGreen else initialsColor
                )
                Text(
                    if (isSettled) "nothing owing" else amountLabel,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            if (trailingAction != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingAction()
            }
        }
    }
}

/**
 * The headline number at the top of a money screen. One figure, said plainly,
 * with the count underneath.
 */
@Composable
fun MoneyHeadline(
    label: String,
    amount: Double,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    CurrencyUtils.formatLkr(amount),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(caption, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            }
            if (action != null) action()
        }
    }
}
