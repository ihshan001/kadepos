package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_profile")
data class BusinessProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val businessType: String = "Retail", // Retail, Service, Both, Food, Repair
    val shopTypeKey: String = "", // GROCERY, FOOD_CAFE, PHARMACY, ... chosen during setup
    val phone: String = "",
    val address: String = "",
    val currencySymbol: String = "Rs.",
    val managementLevel: String = "BILL_STOCK", // JUST_BILL, BILL_STOCK, MANAGE_BUSINESS
    val trackStock: Boolean = true,
    val creditEnabled: Boolean = true,
    val staffEnabled: Boolean = false,
    val receiptStyle: String = "Modern", // Minimal, Classic, Detailed, Modern
    val receiptFooter: String = "Thank you! Please come again.",
    val receiptShowQr: Boolean = false,
    // Printer hardware configuration
    val printerName: String = "",
    val printerAddress: String = "", // MAC address for Bluetooth, IP address for Wi-Fi
    val printerConnectionType: String = "NONE", // NONE, BLUETOOTH, WIFI
    val printerPort: Int = 9100, // network printers, 9100 is the ESC/POS standard
    val printerPaperWidth: String = "58mm", // 58mm, 80mm
    val printerConnected: Boolean = false,
    val autoPrint: Boolean = false,
    val isConfigured: Boolean = false,
    val activeStaffId: Long = 0L,
    val activeStaffName: String = "",
    val activeStaffRole: String = "Owner",
    val requirePinOnOpen: Boolean = false,
    val language: String = "English"
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sellingPrice: Double,
    val costPrice: Double = 0.0,
    val barcode: String = "",
    val sku: String = "",
    val category: String = "General",
    /**
     * The shop type this product belongs to (GROCERY, PHARMACY, ...).
     * Products are always filtered by the active shop type so one business
     * never sees another business type's catalogue.
     */
    val shopType: String = "",
    val unit: String = "Piece", // Piece, Bottle, Box, Packet, Kg, g, L, ml, Meter, Service
    val currentStock: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    val isTracked: Boolean = true,
    val isArchived: Boolean = false,
    val isFavourite: Boolean = false,
    val imageUrl: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val customerId: Long? = null,
    val customerName: String = "Walk-in",
    val customerPhone: String = "",
    val cashierId: Long = 1L,
    val cashierName: String = "Staff",
    val timestamp: Long = System.currentTimeMillis(),
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double,
    val paymentMethod: String, // CASH, CARD, CREDIT, SPLIT, OTHER
    val cashReceived: Double = 0.0,
    val changeGiven: Double = 0.0,
    val cardAmount: Double = 0.0,
    val creditAmount: Double = 0.0,
    val status: String = "COMPLETED", // COMPLETED, REFUNDED, PARTIALLY_REFUNDED, VOID
    val notes: String = "",
    val isOfflineSynced: Boolean = true
)

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long? = null,
    val productName: String,
    val unitPrice: Double,
    val costPrice: Double = 0.0,
    val quantity: Double,
    val discount: Double = 0.0,
    val lineTotal: Double,
    val unit: String = "Piece",
    val returnedQuantity: Double = 0.0
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val creditBalance: Double = 0.0,
    val creditLimit: Double = 50000.0,
    val notes: String = "",
    val isFavourite: Boolean = false,
    val totalPurchased: Double = 0.0,
    val purchaseCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "credit_transactions")
data class CreditTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val saleId: Long? = null,
    val type: String, // SALE_CREDIT, PAYMENT, ADJUSTMENT
    val amount: Double,
    val balanceAfter: Double,
    val paymentMethod: String = "CASH", // CASH, BANK, CARD
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val outstandingBalance: Double = 0.0,
    val totalPurchased: Double = 0.0,
    val purchaseCount: Int = 0,
    val notes: String = ""
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long? = null,
    val supplierName: String = "Local Supplier",
    val invoiceNumber: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val paidAmount: Double,
    val dueAmount: Double = 0.0,
    val paymentStatus: String = "PAID", // PAID, DUE, PARTIAL
    val itemsCount: Int = 0,
    val notes: String = ""
)

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val costPrice: Double,
    val lineTotal: Double
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val changeQty: Double,
    val stockAfter: Double,
    val type: String, // SALE, RETURN, PURCHASE, ADJUST_DAMAGE, ADJUST_EXPIRED, ADJUST_COUNT, ADJUST_OTHER, INITIAL
    val referenceId: String = "",
    val reason: String = "",
    val staffName: String = "Staff",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Rent, Electricity, Staff, Fuel, Transport, Packaging, Repairs, Internet, Other
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val paymentMethod: String = "CASH", // CASH, BANK, CARD
    val reference: String = "",
    val note: String = "",
    val isCashDeducted: Boolean = true
)

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val role: String = "Cashier", // Owner, Manager, Cashier
    val pin: String = "",
    val isActive: Boolean = true,
    val totalSalesCount: Int = 0,
    val totalSalesAmount: Double = 0.0
)

/**
 * Audit trail for sensitive actions (price changes, refunds, voids, cash movements,
 * staff logins). Financial history must never silently disappear.
 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffId: Long = 0,
    val staffName: String = "",
    val action: String, // SALE, REFUND, VOID, PRICE_CHANGE, DISCOUNT, CASH_IN, CASH_OUT, LOGIN, ...
    val description: String,
    val amount: Double = 0.0,
    val reference: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cash_register_shifts")
data class CashRegisterShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterName: String = "Counter 01",
    val staffId: Long = 1L,
    val staffName: String = "",
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val openingCash: Double = 10000.0,
    val expectedCash: Double = 10000.0,
    val actualCash: Double = 10000.0,
    val difference: Double = 0.0,
    val differenceReason: String = "",
    val status: String = "OPEN" // OPEN, CLOSED
)

@Entity(tableName = "cash_movements")
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val type: String, // CASH_IN, CASH_OUT
    val amount: Double,
    val reason: String, // Owner added cash, Owner withdrawal, Shop expense, Change fund, etc.
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "held_sales")
data class HeldSaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val customerId: Long? = null,
    val customerName: String = "Walk-in",
    val cartJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val itemsCount: Int
)
