package com.example.ui.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyUtils {
    private val formatter = DecimalFormat("#,##0.00")
    private val compactFormatter = DecimalFormat("#,##0")

    fun formatLkr(amount: Double, showDecimals: Boolean = true): String {
        return if (showDecimals) {
            "Rs. ${formatter.format(amount)}"
        } else {
            "Rs. ${compactFormatter.format(amount)}"
        }
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTimeOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun generateInvoiceNumber(): String {
        val randomSuffix = (1000..9999).random()
        return "INV-${String.format(Locale.US, "%06d", (1000..999999).random())}"
    }

    /**
     * Renders a bill the way a thermal receipt actually looks: fixed-width
     * columns, a QTY x RATE line under every item, and a totals block that
     * lines up on the right edge. Works out to 32 characters on 58mm paper
     * and 48 on 80mm, which is exactly what the printer expects.
     */
    fun buildReceiptText(
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        invoiceNumber: String,
        timestamp: Long,
        cashierName: String,
        customerName: String,
        items: List<ReceiptItemData>,
        subtotal: Double,
        discount: Double,
        total: Double,
        paymentMethod: String,
        cashReceived: Double,
        change: Double,
        footerMessage: String,
        paperWidth: String = "58mm"
    ): String {
        val width = if (paperWidth == "58mm") 32 else 48
        val thin = "-".repeat(width)
        val thick = "=".repeat(width)

        val sb = StringBuilder()

        // --- Shop header ---
        sb.appendLine(centerText(businessName.uppercase(), width))
        if (businessAddress.isNotBlank()) {
            wrapText(businessAddress, width).forEach { sb.appendLine(centerText(it, width)) }
        }
        if (businessPhone.isNotBlank()) {
            sb.appendLine(centerText("Tel: $businessPhone", width))
        }
        sb.appendLine(thick)

        // --- Bill meta, label column kept narrow so values align ---
        val labelWidth = 9
        fun meta(label: String, value: String) {
            sb.appendLine(label.padEnd(labelWidth) + ": " + value.take(width - labelWidth - 2))
        }
        meta("Bill No", invoiceNumber)
        meta("Date", formatDateOnly(timestamp))
        meta("Time", formatTimeOnly(timestamp))
        if (cashierName.isNotBlank()) meta("Served by", cashierName)
        if (customerName.isNotBlank() && !customerName.equals("Walk-in", true)) {
            meta("Customer", customerName)
        }

        // --- Item table ---
        sb.appendLine(thin)
        sb.appendLine(padBetween("QTY x RATE", "AMOUNT", width))
        sb.appendLine(thin)

        var totalUnits = 0.0
        for (item in items) {
            totalUnits += item.quantity
            // Full product name on its own line(s) - never truncated to
            // something the customer cannot recognise.
            wrapText(item.name, width).forEach { sb.appendLine(it) }
            val qtyRate = "  " + trimNumber(item.quantity) + " x " + money(item.unitPrice)
            sb.appendLine(padBetween(qtyRate, money(item.lineTotal), width))
        }

        // --- Totals ---
        sb.appendLine(thin)
        sb.appendLine(padBetween("Items: ${items.size}", "Qty: ${trimNumber(totalUnits)}", width))
        sb.appendLine(thin)
        sb.appendLine(padBetween("SUBTOTAL", money(subtotal), width))
        if (discount > 0) {
            sb.appendLine(padBetween("DISCOUNT", "-" + money(discount), width))
        }
        sb.appendLine(thick)
        sb.appendLine(padBetween("TOTAL (LKR)", money(total), width))
        sb.appendLine(thick)

        sb.appendLine(padBetween("Paid by", paymentMethod, width))
        if (paymentMethod.equals("CASH", true) && cashReceived > 0) {
            sb.appendLine(padBetween("Cash given", money(cashReceived), width))
            sb.appendLine(padBetween("Change", money(change), width))
        }

        // --- Footer ---
        sb.appendLine(thin)
        if (footerMessage.isNotBlank()) {
            wrapText(footerMessage, width).forEach { sb.appendLine(centerText(it, width)) }
        }
        return sb.toString()
    }

    /** Bare amount for receipt columns: "1,250.00". The Rs. sign lives in the header. */
    private fun money(amount: Double): String = formatter.format(amount)

    /** 3.0 -> "3", 1.5 -> "1.5". Whole quantities should not read "3.00". */
    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else DecimalFormat("0.##").format(value)

    /** Breaks text on word boundaries so nothing runs off the paper edge. */
    private fun wrapText(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            when {
                candidate.length <= width -> {
                    current = StringBuilder(candidate)
                }
                // A single word longer than the paper: hard-split it.
                word.length > width -> {
                    if (current.isNotEmpty()) { lines.add(current.toString()); current = StringBuilder() }
                    word.chunked(width).forEach { lines.add(it) }
                }
                else -> {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val padding = (width - text.length) / 2
        return " ".repeat(padding) + text
    }

    private fun padBetween(left: String, right: String, width: Int): String {
        val spaces = (width - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(spaces) + right
    }
}

data class ReceiptItemData(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double
)
