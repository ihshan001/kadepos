package com.example.ui.viewmodel

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores a parked ("kept aside") bill as JSON so resuming brings back every
 * line exactly as it was — quantities, edited prices, per-line discounts and
 * notes included.
 */
object CartSerializer {

    data class RestoredCart(
        val items: List<CartItem> = emptyList(),
        val billDiscount: Double = 0.0,
        val billNote: String = ""
    )

    fun encode(items: List<CartItem>, billDiscount: Double, billNote: String): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("productId", item.productId ?: JSONObject.NULL)
                    put("name", item.name)
                    put("unitPrice", item.unitPrice)
                    put("listPrice", item.listPrice)
                    put("costPrice", item.costPrice)
                    put("quantity", item.quantity)
                    put("discount", item.discount)
                    put("unit", item.unit)
                    put("note", item.note)
                }
            )
        }
        return JSONObject().apply {
            put("items", array)
            put("billDiscount", billDiscount)
            put("billNote", billNote)
        }.toString()
    }

    fun decode(json: String): RestoredCart {
        if (json.isBlank()) return RestoredCart()
        return try {
            val root = JSONObject(json)
            val array = root.optJSONArray("items") ?: JSONArray()
            val items = (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                CartItem(
                    productId = if (obj.isNull("productId")) null else obj.optLong("productId"),
                    name = obj.optString("name", "Item"),
                    unitPrice = obj.optDouble("unitPrice", 0.0),
                    // Older parked bills have no listPrice: fall back to the
                    // sold price so they simply look "unchanged".
                    listPrice = obj.optDouble("listPrice", obj.optDouble("unitPrice", 0.0)),
                    costPrice = obj.optDouble("costPrice", 0.0),
                    quantity = obj.optDouble("quantity", 1.0),
                    discount = obj.optDouble("discount", 0.0),
                    unit = obj.optString("unit", "Piece"),
                    note = obj.optString("note", "")
                )
            }
            RestoredCart(
                items = items,
                billDiscount = root.optDouble("billDiscount", 0.0),
                billNote = root.optString("billNote", "")
            )
        } catch (e: Exception) {
            RestoredCart()
        }
    }
}
