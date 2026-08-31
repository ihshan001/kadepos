package com.example.data.model

/**
 * Every sensitive capability in the app. Screens ask
 * `permissions.can(Permission.X)` rather than checking role strings, so adding
 * a new role never means hunting through the UI.
 */
enum class Permission(val label: String, val description: String) {
    CREATE_SALE("Make sales", "Create bills and take payments"),
    CHANGE_PRICE("Change price", "Edit the price of an item while billing"),
    GIVE_DISCOUNT("Give discount", "Apply a discount to an item or a whole bill"),
    REFUND_SALE("Refund", "Return items and give money back"),
    VOID_SALE("Cancel bill", "Cancel a completed bill"),
    VIEW_PROFIT("See profit", "See cost prices and profit figures"),
    MANAGE_PRODUCTS("Manage products", "Add, edit and remove products"),
    MANAGE_INVENTORY("Manage stock", "Receive stock and make stock corrections"),
    MANAGE_CUSTOMERS("Manage customers", "Add customers and manage the credit book"),
    MANAGE_SUPPLIERS("Manage suppliers", "Add suppliers and record purchases"),
    VIEW_REPORTS("See reports", "Open sales and business reports"),
    MANAGE_CASH("Handle cash drawer", "Open/close the day, cash in and cash out"),
    MANAGE_EXPENSES("Record expenses", "Add and edit shop expenses like rent and electricity"),
    MANAGE_STAFF("Manage team", "Add staff and change what they can do"),
    MANAGE_SETTINGS("Change settings", "Business details, receipt and printer setup"),
    VIEW_AUDIT("See activity log", "View the record of who did what")
}

/**
 * The three roles a small shop actually understands. Custom permission sets are
 * still possible via [PermissionSet.of] with an explicit list.
 */
enum class StaffRole(
    val roleName: String,
    val friendlyName: String,
    val summary: String,
    val permissions: Set<Permission>
) {
    OWNER(
        roleName = "Owner",
        friendlyName = "Owner",
        summary = "Can do everything, including money and team settings",
        permissions = Permission.entries.toSet()
    ),
    MANAGER(
        roleName = "Manager",
        friendlyName = "Manager",
        summary = "Runs the shop day to day, but cannot change team or business setup",
        permissions = setOf(
            Permission.CREATE_SALE,
            Permission.CHANGE_PRICE,
            Permission.GIVE_DISCOUNT,
            Permission.REFUND_SALE,
            Permission.VOID_SALE,
            Permission.VIEW_PROFIT,
            Permission.MANAGE_PRODUCTS,
            Permission.MANAGE_INVENTORY,
            Permission.MANAGE_CUSTOMERS,
            Permission.MANAGE_SUPPLIERS,
            Permission.VIEW_REPORTS,
            Permission.MANAGE_CASH,
            Permission.MANAGE_EXPENSES,
            Permission.VIEW_AUDIT
        )
    ),
    CASHIER(
        roleName = "Cashier",
        friendlyName = "Cashier",
        summary = "Sells and prints bills only — no prices, profit or reports",
        permissions = setOf(
            Permission.CREATE_SALE,
            Permission.MANAGE_CUSTOMERS
        )
    );

    companion object {
        fun fromName(name: String?): StaffRole =
            entries.firstOrNull { it.roleName.equals(name ?: "", ignoreCase = true) } ?: CASHIER

        val selectableRoles: List<StaffRole> = listOf(OWNER, MANAGER, CASHIER)
    }
}

/**
 * Resolved permissions for whoever is currently using the app.
 */
data class PermissionSet(
    val role: StaffRole,
    val staffId: Long,
    val staffName: String,
    val granted: Set<Permission>
) {
    fun can(permission: Permission): Boolean = granted.contains(permission)

    fun cannot(permission: Permission): Boolean = !can(permission)

    /** Message shown when a cashier taps something they are not allowed to use. */
    fun denialMessage(permission: Permission): String =
        "Only an owner or manager can ${permission.label.lowercase()}. Ask them to unlock this."

    companion object {
        fun of(role: StaffRole, staffId: Long = 0L, staffName: String = ""): PermissionSet =
            PermissionSet(role, staffId, staffName, role.permissions)

        /** Used before anyone has signed in, and for solo shops with no staff. */
        val ownerFallback: PermissionSet = of(StaffRole.OWNER, 0L, "Owner")
    }
}
