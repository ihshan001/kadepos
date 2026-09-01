package com.example

import com.example.data.model.ProductOptionDraft
import com.example.data.model.ProductOptions
import com.example.data.model.ProductOptionsDraft
import com.example.data.model.ProductSubOptionDraft
import com.example.data.model.VariantCatalog
import com.example.ui.screens.onboarding.validateOwnerName
import com.example.ui.util.CountryCodes
import com.example.ui.util.PhoneValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two shapes of product options a real shop asked for, and the phone
 * rules that stop a wrong number reaching a printed bill.
 *
 * The whole feature lives in one TEXT column, so if encode, decode and the
 * picker ever drift apart the owner silently loses prices or stock counts.
 */
class ProductOptionsTest {

    private fun biryani(): ProductOptionsDraft = ProductOptionsDraft(
        hasOptions = true,
        groupName = "Rice type",
        subGroupName = "Portion",
        options = listOf(
            ProductOptionDraft(
                name = "Keeri",
                price = 650.0,
                subOptions = listOf(
                    ProductSubOptionDraft("Regular", 650.0, 10.0),
                    ProductSubOptionDraft("Full", 850.0, 4.0)
                )
            ),
            ProductOptionDraft(
                name = "Basmati",
                price = 750.0,
                subOptions = listOf(
                    ProductSubOptionDraft("Regular", 750.0, 6.0),
                    ProductSubOptionDraft("Full", 950.0, 2.0)
                )
            )
        )
    )

    private fun trouser(): ProductOptionsDraft = ProductOptionsDraft(
        hasOptions = true,
        groupName = "Colour",
        subGroupName = "Size",
        options = listOf(
            ProductOptionDraft(
                name = "Green",
                subOptions = listOf(
                    ProductSubOptionDraft("32", 2400.0, 12.0),
                    ProductSubOptionDraft("34", 2400.0, 2.0),
                    ProductSubOptionDraft("36", 2400.0, 0.0),
                    ProductSubOptionDraft("40", 2500.0, 2.0)
                )
            ),
            ProductOptionDraft(
                name = "Black",
                subOptions = listOf(
                    ProductSubOptionDraft("L", 2400.0, 2.0),
                    ProductSubOptionDraft("XL", 2600.0, 5.0)
                )
            )
        )
    )

    @Test
    fun `a biryani priced by rice and portion keeps every price`() {
        val encoded = ProductOptions.encode(biryani())
        val combos = VariantCatalog.buildCombinations(encoded, basePrice = 650.0)

        assertEquals(4, combos.size)

        fun priceOf(display: String) = combos.first { it.displayName == display }.price
        assertEquals(650.0, priceOf("Keeri/Regular"), 0.001)
        assertEquals(850.0, priceOf("Keeri/Full"), 0.001)
        assertEquals(750.0, priceOf("Basmati/Regular"), 0.001)
        assertEquals(950.0, priceOf("Basmati/Full"), 0.001)
    }

    @Test
    fun `a trouser can give each colour its own sizes`() {
        val encoded = ProductOptions.encode(trouser())
        val combos = VariantCatalog.buildCombinations(encoded, basePrice = 2400.0)

        assertEquals(6, combos.size)
        val names = combos.map { it.displayName }
        assertTrue(names.contains("Green/32"))
        assertTrue(names.contains("Green/40"))
        assertTrue(names.contains("Black/L"))
        assertTrue(names.contains("Black/XL"))
        // A plain cross-product would invent these; the shop does not sell them.
        assertTrue(!names.contains("Green/L"))
        assertTrue(!names.contains("Black/32"))
    }

    @Test
    fun `the editor reads a saved product back with its stock counts`() {
        val encoded = ProductOptions.encode(trouser())
        val stock = ProductOptions.combinationStock(trouser())

        val decoded = ProductOptions.decode(encoded, basePrice = 2400.0) { labels ->
            stock[ProductOptions.comboKey(labels)] ?: 0.0
        }

        assertEquals(2, decoded.options.size)
        assertEquals("Colour", decoded.groupName)
        assertEquals("Size", decoded.subGroupName)
        assertEquals(4, decoded.options[0].subOptions.size)
        assertEquals(2, decoded.options[1].subOptions.size)
        assertEquals(12.0, decoded.options[0].subOptions[0].stock, 0.001)
        assertEquals(5.0, decoded.options[1].subOptions[1].stock, 0.001)

        // Re-encoding must not drift from what was saved.
        assertEquals(encoded, ProductOptions.encode(decoded))
    }

    @Test
    fun `stock keys line up with the generated child rows`() {
        val draft = trouser()
        val stock = ProductOptions.combinationStock(draft)

        ProductOptions.combinations(draft).forEach { combo ->
            val expected = VariantCatalog.childName("Trouser", combo)
            assertTrue(
                "$expected should carry its own count",
                stock.containsKey(ProductOptions.comboKey(combo.labels))
            )
        }
        assertEquals(12.0, stock[ProductOptions.comboKey(listOf("Green", "32"))]!!, 0.001)
        assertEquals(0.0, stock[ProductOptions.comboKey(listOf("Green", "36"))]!!, 0.001)
    }

    @Test
    fun `products saved before exact combinations still work`() {
        // The old format: group lines plus a delta for one option.
        val legacy = "Rice: Keeri|Basmati\nPortion: Regular|Full\nFull+200"
        val combos = VariantCatalog.buildCombinations(legacy, basePrice = 650.0)

        assertEquals(4, combos.size)

        fun priceOf(display: String) = combos.first { it.displayName == display }.price
        assertEquals(650.0, priceOf("Keeri/Regular"), 0.001)
        assertEquals(850.0, priceOf("Keeri/Full"), 0.001)

        // And the editor still opens it as a two level product.
        val decoded = ProductOptions.decode(legacy, basePrice = 650.0)
        assertTrue(decoded.isSplit)
        assertEquals(2, decoded.options.size)
    }

    // --- Phone numbers -----------------------------------------------------

    @Test
    fun `every country is offered, Sri Lanka leads with +94`() {
        assertTrue("the picker should list every country", CountryCodes.all.size > 200)
        val lanka = CountryCodes.findByCode("LK")
        assertNotNull(lanka)
        assertEquals("+94", lanka!!.dialCode)
        assertEquals(9, lanka.exactLocalLength)
        assertEquals("+94", CountryCodes.default.dialCode)
    }

    @Test
    fun `a Sri Lanka number must be nine digits without the leading zero`() {
        val lanka = CountryCodes.default
        assertNull(PhoneValidator.errorFor(lanka, "777777700"))
        assertNotNull("a leading 0 is the classic mistake", PhoneValidator.errorFor(lanka, "0777777700"))
        assertNotNull("stopping halfway must not pass", PhoneValidator.errorFor(lanka, "7777777"))
        assertNotNull("an empty number must not pass", PhoneValidator.errorFor(lanka, ""))
    }

    @Test
    fun `other countries only need a sensible length`() {
        val uk = CountryCodes.findByCode("GB")!!
        assertNull(PhoneValidator.errorFor(uk, "7700900123"))
        assertNotNull(PhoneValidator.errorFor(uk, "07700900123"))
        assertNotNull(PhoneValidator.errorFor(uk, "7"))
    }

    @Test
    fun `a stored number is split back into its country and local part`() {
        val (country, local) = CountryCodes.split("+94 777777700")
        assertEquals("LK", country.code)
        assertEquals("777777700", local)

        // Numbers saved before the picker existed were typed whole.
        val (legacyCountry, legacyLocal) = CountryCodes.split("0777777700")
        assertEquals("LK", legacyCountry.code)
        assertEquals("777777700", legacyLocal)
    }

    // --- Names -------------------------------------------------------------

    @Test
    fun `an owner name is letters and spaces`() {
        assertNull(validateOwnerName("Morgan Blake"))
        assertNotNull("blank is not a name", validateOwnerName(""))
        assertNotNull("digits belong to the phone box", validateOwnerName("Morgan 2"))
        assertNotNull(validateOwnerName("Morgan_Blake"))
    }
}
