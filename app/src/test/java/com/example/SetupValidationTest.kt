package com.example

import com.example.data.model.Permission
import com.example.data.model.PermissionSet
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
        assertTrue("duplicate barcode ${product.barcode}", barcodes.add(product.barcode))
        assertTrue("duplicate sku ${product.sku}", skus.add(product.sku))
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
}
