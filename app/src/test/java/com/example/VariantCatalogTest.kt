package com.example

import com.example.data.model.VariantCatalog
import com.example.data.model.VariantGroupDraft
import com.example.data.model.VariantOptionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the structured variant editor's storage round-trip. The whole
 * "Biryani -> rice type -> portion -> price" feature collapses into one TEXT
 * column, so if encode/parse drift apart the owner silently loses prices.
 */
class VariantCatalogTest {

    private fun biryaniGroups() = listOf(
        VariantGroupDraft(
            "Rice type",
            listOf(
                VariantOptionDraft("Keeri", 0.0),
                VariantOptionDraft("Basmati", 400.0)
            )
        ),
        VariantGroupDraft(
            "Portion",
            listOf(
                VariantOptionDraft("Regular", 0.0),
                VariantOptionDraft("Full", 300.0)
            )
        )
    )

    @Test
    fun `encode then build yields every rice x portion combination with correct price`() {
        val base = 1100.0
        val encoded = VariantCatalog.encodeDrafts(biryaniGroups())
        val combos = VariantCatalog.buildCombinations(encoded, base)

        assertEquals(4, combos.size)

        fun price(display: String) = combos.first { it.displayName == display }.price
        assertEquals(1100.0, price("Keeri/Regular"), 0.001)
        assertEquals(1400.0, price("Keeri/Full"), 0.001)
        assertEquals(1500.0, price("Basmati/Regular"), 0.001)
        assertEquals(1800.0, price("Basmati/Full"), 0.001)
    }

    @Test
    fun `encode then parse round-trips groups and price adjustments`() {
        val encoded = VariantCatalog.encodeDrafts(biryaniGroups())
        val parsed = VariantCatalog.parseDrafts(encoded, basePrice = 1100.0)

        assertEquals(2, parsed.size)
        assertEquals("Rice type", parsed[0].name)
        assertEquals(listOf("Keeri", "Basmati"), parsed[0].options.map { it.name })
        assertEquals(0.0, parsed[0].options[0].priceAdjustment, 0.001)
        assertEquals(400.0, parsed[0].options[1].priceAdjustment, 0.001)
        assertEquals("Portion", parsed[1].name)
        assertEquals(300.0, parsed[1].options[1].priceAdjustment, 0.001)
    }

    @Test
    fun `legacy simple lines become one implicit group`() {
        val parsed = VariantCatalog.parseDrafts("Regular|650\nFull|750", basePrice = 650.0)
        assertEquals(1, parsed.size)
        assertEquals("Option", parsed[0].name)
        assertEquals(listOf("Regular", "Full"), parsed[0].options.map { it.name })
        assertEquals(0.0, parsed[0].options[0].priceAdjustment, 0.001)
        assertEquals(100.0, parsed[0].options[1].priceAdjustment, 0.001)
    }

    @Test
    fun `separator characters are stripped from names`() {
        val encoded = VariantCatalog.encodeDrafts(
            listOf(
                VariantGroupDraft(
                    "Rice:type",
                    listOf(VariantOptionDraft("Keeri|Basmati", 0.0), VariantOptionDraft("Sona/Masoori", 100.0))
                )
            )
        )
        // Re-parsing must still produce exactly one group and two distinct options.
        val parsed = VariantCatalog.parseDrafts(encoded, 0.0)
        assertEquals(1, parsed.size)
        assertEquals(2, parsed[0].options.size)
        assertTrue(parsed[0].options.none { it.name.contains("|") || it.name.contains("/") || it.name.contains(":") })
    }

    @Test
    fun `blank groups and options are dropped`() {
        val encoded = VariantCatalog.encodeDrafts(
            listOf(
                VariantGroupDraft("", listOf(VariantOptionDraft("Regular", 0.0))),
                VariantGroupDraft("Portion", listOf(VariantOptionDraft("  ", 0.0), VariantOptionDraft("Full", 50.0)))
            )
        )
        val parsed = VariantCatalog.parseDrafts(encoded, 0.0)
        assertEquals(1, parsed.size)
        assertEquals("Portion", parsed[0].name)
        assertEquals(listOf("Full"), parsed[0].options.map { it.name })
    }
}
