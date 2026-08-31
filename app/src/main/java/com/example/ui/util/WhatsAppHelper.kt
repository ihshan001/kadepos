package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ProductEntity
import java.net.URLEncoder

object WhatsAppHelper {

    /**
     * Formats phone number into international format (defaulting to Sri Lanka +94 if starting with 0 or 7).
     */
    fun cleanPhoneNumber(rawPhone: String): String {
        var digits = rawPhone.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        if (digits.startsWith("0") && digits.length == 10) {
            digits = "94" + digits.substring(1)
        } else if (digits.length == 9 && (digits.startsWith("7") || digits.startsWith("1") || digits.startsWith("2") || digits.startsWith("3") || digits.startsWith("4") || digits.startsWith("5") || digits.startsWith("6") || digits.startsWith("8") || digits.startsWith("9"))) {
            digits = "94$digits"
        }
        return digits
    }

    /**
     * Opens WhatsApp with prefilled message to a phone number (or general share if phone is empty).
     */
    fun sendWhatsAppMessage(context: Context, rawPhone: String, message: String) {
        val cleanPhone = cleanPhoneNumber(rawPhone)
        try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val uriString = if (cleanPhone.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMessage"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general share intent if WhatsApp is not installed
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(sendIntent, "Send Order via").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open WhatsApp: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Builds a structured, professional Purchase Order message for a single product.
     */
    fun buildStockReorderMessage(
        storeName: String,
        storePhone: String,
        product: ProductEntity,
        requestedQty: Double,
        unitCost: Double,
        supplierName: String = "",
        note: String = ""
    ): String {
        val today = CurrencyUtils.formatDateOnly(System.currentTimeMillis())
        val totalCost = requestedQty * unitCost
        val qtyStr = if (requestedQty % 1.0 == 0.0) requestedQty.toInt().toString() else requestedQty.toString()
        val currStockStr = if (product.currentStock % 1.0 == 0.0) product.currentStock.toInt().toString() else product.currentStock.toString()
        val thresholdStr = if (product.lowStockThreshold % 1.0 == 0.0) product.lowStockThreshold.toInt().toString() else product.lowStockThreshold.toString()

        return buildString {
            appendLine("🛒 *PURCHASE ORDER / STOCK INQUIRY*")
            appendLine("🏪 Store: *$storeName*")
            if (storePhone.isNotBlank()) appendLine("📞 Contact: $storePhone")
            if (supplierName.isNotBlank()) appendLine("🏢 To Supplier: *$supplierName*")
            appendLine("📅 Date: $today")
            appendLine("--------------------------------")
            appendLine("📦 *Item:* ${product.name}")
            if (product.barcode.isNotBlank()) appendLine("🏷 *Barcode/SKU:* ${product.barcode}")
            if (product.category.isNotBlank() && product.category != "General") appendLine("📁 *Category:* ${product.category}")
            appendLine("📊 *Current Stock:* $currStockStr ${product.unit} (Low Threshold: $thresholdStr)")
            appendLine("⚡ *Requested Order Qty:* *$qtyStr ${product.unit}*")
            if (unitCost > 0) {
                appendLine("💰 *Expected Unit Cost:* ${CurrencyUtils.formatLkr(unitCost)}")
                appendLine("💵 *Estimated Total:* *${CurrencyUtils.formatLkr(totalCost)}*")
            }
            if (note.isNotBlank()) {
                appendLine("📝 *Note:* $note")
            }
            appendLine("--------------------------------")
            appendLine("Please confirm stock availability, current wholesale pricing, and estimated delivery schedule. Thank you!")
        }
    }

    /**
     * Builds a structured batch Purchase Order message for multiple low stock products.
     */
    fun buildBatchReorderMessage(
        storeName: String,
        storePhone: String,
        supplierName: String,
        items: List<Triple<ProductEntity, Double, Double>>, // Product, Qty, UnitCost
        notes: String = ""
    ): String {
        val today = CurrencyUtils.formatDateOnly(System.currentTimeMillis())
        val grandTotal = items.sumOf { it.second * it.third }

        return buildString {
            appendLine("🛒 *BATCH PURCHASE ORDER*")
            appendLine("🏪 Store: *$storeName*")
            if (storePhone.isNotBlank()) appendLine("📞 Contact: $storePhone")
            if (supplierName.isNotBlank()) appendLine("🏢 To: *$supplierName*")
            appendLine("📅 Date: $today")
            appendLine("--------------------------------")
            appendLine("📋 *Requested Items (${items.size}):*")
            items.forEachIndexed { index, (product, qty, cost) ->
                val qtyStr = if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
                val lineTotal = qty * cost
                appendLine("${index + 1}. *${product.name}*")
                appendLine("   • Qty: *$qtyStr ${product.unit}* @ ${CurrencyUtils.formatLkr(cost)} = ${CurrencyUtils.formatLkr(lineTotal)}")
                if (product.barcode.isNotBlank()) {
                    appendLine("   • Barcode: ${product.barcode}")
                }
            }
            appendLine("--------------------------------")
            appendLine("💰 *ESTIMATED TOTAL:* *${CurrencyUtils.formatLkr(grandTotal)}*")
            if (notes.isNotBlank()) {
                appendLine("📝 *Notes:* $notes")
            }
            appendLine("--------------------------------")
            appendLine("Please reply with confirmation and delivery timeline. Thank you!")
        }
    }
}
