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
        val is58 = paperWidth == "58mm"
        val lineLength = if (is58) 32 else 48
        val separator = "-".repeat(lineLength)
        val doubleSeparator = "=".repeat(lineLength)

        val sb = StringBuilder()
        sb.append(centerText(businessName.uppercase(), lineLength)).append("\n")
        if (businessAddress.isNotBlank()) {
            sb.append(centerText(businessAddress, lineLength)).append("\n")
        }
        if (businessPhone.isNotBlank()) {
            sb.append(centerText("Tel: $businessPhone", lineLength)).append("\n")
        }
        sb.append(doubleSeparator).append("\n")
        sb.append("Invoice: $invoiceNumber\n")
        sb.append("Date: ${formatDateTime(timestamp)}\n")
        sb.append("Cashier: $cashierName | Cust: $customerName\n")
        sb.append(separator).append("\n")

        for (item in items) {
            val name = if (item.name.length > 18) item.name.take(16) + ".." else item.name
            val qtyStr = "${item.quantity.toInt()} x ${formatLkr(item.unitPrice)}"
            val lineTotalStr = formatLkr(item.lineTotal)

            sb.append(String.format(Locale.US, "%-18s %12s\n", name, lineTotalStr))
            sb.append(String.format(Locale.US, "  %-28s\n", qtyStr))
        }

        sb.append(separator).append("\n")
        sb.append(padBetween("SUBTOTAL", formatLkr(subtotal), lineLength)).append("\n")
        if (discount > 0) {
            sb.append(padBetween("DISCOUNT", "-${formatLkr(discount)}", lineLength)).append("\n")
        }
        sb.append(doubleSeparator).append("\n")
        sb.append(padBetween("TOTAL", formatLkr(total), lineLength)).append("\n")
        sb.append(doubleSeparator).append("\n")
        sb.append(padBetween("PAYMENT", paymentMethod, lineLength)).append("\n")
        if (paymentMethod == "CASH" && cashReceived > 0) {
            sb.append(padBetween("RECEIVED", formatLkr(cashReceived), lineLength)).append("\n")
            sb.append(padBetween("CHANGE", formatLkr(change), lineLength)).append("\n")
        }

        sb.append(separator).append("\n")
        sb.append(centerText(footerMessage, lineLength)).append("\n")
        sb.append(centerText("--- DIGITAL COPY / VERIFIED ---", lineLength)).append("\n")

        return sb.toString()
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
