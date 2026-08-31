package com.example

import com.example.data.model.Permission
import com.example.data.model.PermissionSet
import com.example.data.model.PermissionOverrides
import com.example.ui.util.CurrencyUtils
import com.example.ui.util.ReceiptDesign
import com.example.ui.util.ReceiptItemData
import com.example.data.model.ProductCatalogPresets
import com.example.data.model.StaffRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rules the whole app leans on: catalogue isolation and who is
 * allowed to do what.
 */
class SetupValidationTest {

  @Test
  fun `every shop type ships exactly fifty products`() {
    ProductCatalogPresets.shopTypes.forEach { preset ->
      assertEquals(
        "${preset.key} should have 50 starter products",
        50,
        preset.products.size
      )
    }
  }

  @Test
  fun `no product name is shared between shop types`() {
    val seen = mutableMapOf<String, String>()
    ProductCatalogPresets.shopTypes.forEach { preset ->
      preset.products.forEach { product ->
        val key = product.name.lowercase().trim()
        val owner = seen[key]
        assertTrue(
          "\"${product.name}\" appears in both $owner and ${preset.key}",
          owner == null
        )
        seen[key] = preset.key
      }
    }
  }

  @Test
  fun `no barcode or sku is reused anywhere`() {
    val barcodes = mutableSetOf<String>()
    val skus = mutableSetOf<String>()
    ProductCatalogPresets.shopTypes.forEach { preset ->
      preset.products.forEach { product ->
        // Services (a haircut, a screen repair) have no barcode by design.
        if (product.barcode.isNotBlank()) {
          assertTrue("duplicate barcode ${product.barcode}", barcodes.add(product.barcode))
        }
        assertTrue("duplicate sku ${product.sku}", skus.add(product.sku))
      }
    }
  }

  @Test
  fun `each shop type stamps its own key on every product`() {
    ProductCatalogPresets.shopTypes.forEach { preset ->
      preset.products.forEach { product ->
        assertEquals(
          "${product.name} should belong to ${preset.key}",
          preset.key,
          product.shopType
        )
      }
    }
  }

  @Test
  fun `no category name is shared between shop types`() {
    val owner = mutableMapOf<String, String>()
    ProductCatalogPresets.shopTypes.forEach { preset ->
      preset.categories.forEach { category ->
        val existing = owner[category.lowercase()]
        assertTrue(
          "category \"$category\" appears in both $existing and ${preset.key}",
          existing == null
        )
        owner[category.lowercase()] = preset.key
      }
    }
  }

  @Test
  fun `a cashier cannot refund or change settings`() {
    val cashier = PermissionSet.of(StaffRole.CASHIER, staffId = 7L, staffName = "Cashier")
    assertTrue(cashier.can(Permission.CREATE_SALE))
    assertFalse(cashier.can(Permission.REFUND_SALE))
    assertFalse(cashier.can(Permission.MANAGE_SETTINGS))
    assertFalse(cashier.can(Permission.MANAGE_STAFF))
  }

  @Test
  fun `a manager runs the shop but cannot touch staff or settings`() {
    val manager = PermissionSet.of(StaffRole.MANAGER, staffId = 3L, staffName = "Manager")
    assertTrue(manager.can(Permission.REFUND_SALE))
    assertTrue(manager.can(Permission.VIEW_REPORTS))
    assertFalse(manager.can(Permission.MANAGE_STAFF))
    assertFalse(manager.can(Permission.MANAGE_SETTINGS))
  }

  @Test
  fun `the owner can do everything`() {
    val owner = PermissionSet.of(StaffRole.OWNER, staffId = 1L, staffName = "Owner")
    Permission.entries.forEach { permission ->
      assertTrue("owner should have $permission", owner.can(permission))
    }
  }

  @Test
  fun `denial messages name the role and stay readable`() {
    val cashier = PermissionSet.of(StaffRole.CASHIER, staffId = 7L, staffName = "Nimal")
    val message = cashier.denialMessage(Permission.REFUND_SALE)
    assertTrue(message.isNotBlank())
    assertFalse(message.contains("_"))
  }

  // --- Per-person permission tweaks ---------------------------------------

  @Test
  fun `an owner can allow one extra thing without changing the role`() {
    val (extra, revoked) = PermissionOverrides.encode(setOf(Permission.CHANGE_PRICE), emptySet())
    val nimal = PermissionSet.resolve(
      role = StaffRole.CASHIER, staffId = 7L, staffName = "Nimal",
      extraCsv = extra, revokedCsv = revoked
    )
    assertTrue("the extra permission should be granted", nimal.can(Permission.CHANGE_PRICE))
    assertTrue("role defaults must survive", nimal.can(Permission.CREATE_SALE))
    assertFalse("unrelated things stay blocked", nimal.can(Permission.MANAGE_SETTINGS))
    assertTrue(nimal.isCustomised())
  }

  @Test
  fun `taking something away beats granting it`() {
    val (extra, revoked) = PermissionOverrides.encode(
      setOf(Permission.REFUND_SALE),
      setOf(Permission.REFUND_SALE)
    )
    val person = PermissionSet.resolve(
      role = StaffRole.MANAGER, staffId = 2L, staffName = "Kamal",
      extraCsv = extra, revokedCsv = revoked
    )
    assertFalse("revoke must win so 'block this' is dependable", person.can(Permission.REFUND_SALE))
  }

  @Test
  fun `a revoked role default is actually removed`() {
    val (extra, revoked) = PermissionOverrides.encode(emptySet(), setOf(Permission.VIEW_PROFIT))
    val manager = PermissionSet.resolve(
      role = StaffRole.MANAGER, staffId = 3L, staffName = "Sunil",
      extraCsv = extra, revokedCsv = revoked
    )
    assertFalse(manager.can(Permission.VIEW_PROFIT))
    assertTrue(manager.can(Permission.VIEW_REPORTS))
  }

  @Test
  fun `unknown permission names in old records are ignored, not fatal`() {
    val person = PermissionSet.resolve(
      role = StaffRole.CASHIER, staffId = 9L, staffName = "Old record",
      extraCsv = "SOMETHING_REMOVED,CHANGE_PRICE", revokedCsv = "ALSO_GONE"
    )
    assertTrue(person.can(Permission.CHANGE_PRICE))
    assertTrue(person.can(Permission.CREATE_SALE))
  }

  @Test
  fun `no overrides means the plain role`() {
    val plain = PermissionSet.resolve(StaffRole.CASHIER, 1L, "A", "", "")
    assertEquals(StaffRole.CASHIER.permissions, plain.granted)
    assertFalse(plain.isCustomised())
  }

  @Test
  fun `a solo owner has everything and is marked solo`() {
    val solo = PermissionSet.soloOwner("Sunrise Stores")
    Permission.entries.forEach { assertTrue(solo.can(it)) }
    assertTrue(solo.isSoloOwner)
  }

  @Test
  fun `signed out of a team shop means no access at all`() {
    Permission.entries.forEach {
      assertFalse("locked out must grant nothing", PermissionSet.lockedOut.can(it))
    }
  }

  @Test
  fun `every permission belongs to a group so the editor can show it`() {
    val shown = Permission.grouped().values.flatten().toSet()
    assertEquals(Permission.entries.toSet(), shown)
  }

  @Test
  fun `a cashier cannot change prices or delete records`() {
    val cashier = PermissionSet.of(StaffRole.CASHIER)
    assertFalse(cashier.can(Permission.CHANGE_PRICE))
    assertFalse(cashier.can(Permission.GIVE_DISCOUNT))
    assertFalse(cashier.can(Permission.DELETE_RECORDS))
    assertFalse(cashier.can(Permission.MANAGE_INVENTORY))
    assertFalse(cashier.can(Permission.MANAGE_SUPPLIERS))
    assertFalse(cashier.can(Permission.VIEW_PROFIT))
  }

  // --- The printed bill ----------------------------------------------------

  private fun sampleReceipt(design: ReceiptDesign = ReceiptDesign(), paper: String = "58mm") =
    CurrencyUtils.buildReceiptText(
      businessName = "Sunrise Stores",
      businessPhone = "0771234567",
      businessAddress = "42 Main Street, Negombo",
      invoiceNumber = "INV-000123",
      timestamp = 1_756_600_000_000L,
      cashierName = "Nimal",
      customerName = "Walk-in",
      items = listOf(
        ReceiptItemData("Bread", 3.0, 120.0, 360.0),
        ReceiptItemData("Milk Powder 400g", 1.0, 1250.0, 1250.0)
      ),
      subtotal = 1610.0,
      discount = 10.0,
      total = 1600.0,
      paymentMethod = "CASH",
      cashReceived = 2000.0,
      change = 400.0,
      footerMessage = "Thank you!",
      paperWidth = paper,
      design = design
    )

  @Test
  fun `the bill shows quantity and rate for every line`() {
    val text = sampleReceipt()
    assertTrue("needs a column header", text.contains("QTY x RATE"))
    assertTrue("quantity must be explicit", text.contains("3 x 120.00"))
    assertTrue(text.contains("1 x 1,250.00"))
  }

  @Test
  fun `no printed line is wider than the paper`() {
    listOf("58mm" to 32, "80mm" to 48).forEach { (paper, width) ->
      sampleReceipt(paper = paper).lines().forEach { line ->
        assertTrue("\"$line\" is ${line.length} chars, over $width on $paper",
          line.length <= width)
      }
    }
  }

  @Test
  fun `date and time appear and can be turned off`() {
    assertTrue(sampleReceipt().contains("Date"))
    assertTrue(sampleReceipt().contains("Time"))
    val hidden = sampleReceipt(ReceiptDesign(showDateTime = false))
    assertFalse(hidden.contains("Date "))
    assertFalse(hidden.contains("Time "))
  }

  @Test
  fun `the shop can rename the bill header and add its own notes`() {
    val text = sampleReceipt(
      ReceiptDesign(
        headerName = "Sunrise Wholesale",
        headerNote = "VAT 12345",
        returnNote = "Keep this bill for exchanges"
      )
    )
    assertTrue(text.contains("SUNRISE WHOLESALE"))
    assertFalse("the override should replace the shop name", text.contains("SUNRISE STORES"))
    assertTrue(text.contains("VAT 12345"))
    assertTrue(text.contains("Keep this bill for exchanges"))
  }

  @Test
  fun `hiding the address and phone actually removes them`() {
    val text = sampleReceipt(ReceiptDesign(showAddress = false, showPhone = false))
    assertFalse(text.contains("Main Street"))
    assertFalse(text.contains("0771234567"))
  }

  @Test
  fun `totals and change are on the bill`() {
    val text = sampleReceipt()
    assertTrue(text.contains("TOTAL (LKR)"))
    assertTrue(text.contains("1,600.00"))
    assertTrue(text.contains("Change"))
    assertTrue(text.contains("400.00"))
  }
}
