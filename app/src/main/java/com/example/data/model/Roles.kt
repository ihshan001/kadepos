package com.example.data.model

/**
 * Where a permission shows up in the "what can this person do" editor. Grouping
 * matters: a shop owner ticking boxes thinks in terms of "the till", "the
 * stockroom", "the money", not an alphabetical list of 19 switches.
 */
enum class PermissionGroup(val title: String, val blurb: String) {
    TILL("At the counter", "Selling, prices and discounts"),
    CATALOGUE("Products & stock", "The item list and what is on the shelf"),
    PEOPLE("Customers & suppliers", "Credit book and deliveries"),
    MONEY("Money & reports", "Cash drawer, expenses, profit and reports"),
    SHOP("Shop setup", "Team, bill design and app settings")
}

/**
 * Every sensitive capability in the app. Screens and the ViewModel ask
 * `can(Permission.X)` rather than checking role strings, so adding a new role
 * never means hunting through the UI.
 *
 * [risk] drives the warning shown when granting it to a staff member.
 */
enum class Permission(
    val label: String,
    val description: String,
    val group: PermissionGroup,
    val sensitive: Boolean = false
) {
    // --- At the counter ---
    CREATE_SALE(
        "Make sales", "Create bills and take payments",
        PermissionGroup.TILL
    ),
    CHANGE_PRICE(
        "Change the price", "Sell an item for more or less than the listed price",
        PermissionGroup.TILL, sensitive = true
    ),
    GIVE_DISCOUNT(
        "Give a discount", "Take money off an item or a whole bill",
        PermissionGroup.TILL, sensitive = true
    ),
    VIEW_SALES_HISTORY(
        "See past bills", "Open the bill history and reprint receipts",
        PermissionGroup.TILL
    ),
    REFUND_SALE(
        "Give refunds", "Return items and hand money back",
        PermissionGroup.TILL, sensitive = true
    ),
    VOID_SALE(
        "Cancel a bill", "Cancel a bill that was already completed",
        PermissionGroup.TILL, sensitive = true
    ),

    // --- Products & stock ---
    MANAGE_PRODUCTS(
        "Add & edit products", "Change the item list, names and prices",
        PermissionGroup.CATALOGUE, sensitive = true
    ),
    MANAGE_INVENTORY(
        "Manage stock", "Receive stock and make stock corrections",
        PermissionGroup.CATALOGUE, sensitive = true
    ),

    // --- Customers & suppliers ---
    MANAGE_CUSTOMERS(
        "Manage customers", "Add customers and run the credit book",
        PermissionGroup.PEOPLE
    ),
    MANAGE_SUPPLIERS(
        "Manage suppliers", "Add suppliers and record deliveries and bills",
        PermissionGroup.PEOPLE, sensitive = true
    ),

    // --- Money & reports ---
    VIEW_PROFIT(
        "See profit", "See what items cost you and how much you make",
        PermissionGroup.MONEY, sensitive = true
    ),
    VIEW_REPORTS(
        "See reports", "Open sales and business reports",
        PermissionGroup.MONEY, sensitive = true
    ),
    MANAGE_CASH(
        "Handle the cash drawer", "Open and close the day, cash in and cash out",
        PermissionGroup.MONEY, sensitive = true
    ),
    MANAGE_EXPENSES(
        "Record expenses", "Add and remove shop expenses like rent and electricity",
        PermissionGroup.MONEY
    ),

    // --- Shop setup ---
    DELETE_RECORDS(
        "Delete records", "Permanently remove products, suppliers, bills and expenses",
        PermissionGroup.SHOP, sensitive = true
    ),
    EDIT_RECEIPT(
        "Design the bill", "Change what is printed on the receipt",
        PermissionGroup.SHOP
    ),
    MANAGE_STAFF(
        "Manage the team", "Add people and change what they are allowed to do",
        PermissionGroup.SHOP, sensitive = true
    ),
    MANAGE_SETTINGS(
        "Change shop settings", "Business details, printer and app setup",
        PermissionGroup.SHOP, sensitive = true
    ),
    VIEW_AUDIT(
        "See the activity log", "View the record of who did what",
        PermissionGroup.SHOP
    );

    companion object {
        fun fromKey(key: String): Permission? =
            entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) }

        /** Permissions in display order, bucketed by group. */
        fun grouped(): Map<PermissionGroup, List<Permission>> =
            entries.groupBy { it.group }
    }
}

/**
 * The roles a small shop actually understands. A role is only a *starting
 * point*: the owner can tick extra boxes or take some away per person, which is
 * stored on the staff record. See [PermissionSet.resolve].
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
        summary = "Full control. Can do everything, including team and money settings.",
        permissions = Permission.entries.toSet()
    ),
    MANAGER(
        roleName = "Manager",
        friendlyName = "Manager",
        summary = "Runs the shop day to day. Cannot change the team or shop setup.",
        permissions = setOf(
            Permission.CREATE_SALE,
            Permission.CHANGE_PRICE,
            Permission.GIVE_DISCOUNT,
            Permission.VIEW_SALES_HISTORY,
            Permission.REFUND_SALE,
            Permission.VOID_SALE,
            Permission.MANAGE_PRODUCTS,
            Permission.MANAGE_INVENTORY,
            Permission.MANAGE_CUSTOMERS,
            Permission.MANAGE_SUPPLIERS,
            Permission.VIEW_PROFIT,
            Permission.VIEW_REPORTS,
            Permission.MANAGE_CASH,
            Permission.MANAGE_EXPENSES,
            Permission.EDIT_RECEIPT,
            Permission.VIEW_AUDIT
        )
    ),
    SUPERVISOR(
        roleName = "Supervisor",
        friendlyName = "Senior staff",
        summary = "Sells, and may discount, refund and receive stock. No profit or reports.",
        permissions = setOf(
            Permission.CREATE_SALE,
            Permission.GIVE_DISCOUNT,
            Permission.VIEW_SALES_HISTORY,
            Permission.REFUND_SALE,
            Permission.MANAGE_INVENTORY,
            Permission.MANAGE_CUSTOMERS,
            Permission.MANAGE_EXPENSES
        )
    ),
    CASHIER(
        roleName = "Cashier",
        friendlyName = "Cashier",
        summary = "Sells and prints bills only. Cannot change prices, see profit or reports.",
        permissions = setOf(
            Permission.CREATE_SALE,
            Permission.VIEW_SALES_HISTORY,
            Permission.MANAGE_CUSTOMERS
        )
    );

    /** Permissions this role does NOT get by default, for the "extra access" UI. */
    val notGranted: Set<Permission>
        get() = Permission.entries.toSet() - permissions

    companion object {
        fun fromName(name: String?): StaffRole =
            entries.firstOrNull { it.roleName.equals(name ?: "", ignoreCase = true) } ?: CASHIER

        /** Roles an owner can assign. Owner itself is assignable for a co-owner. */
        val selectableRoles: List<StaffRole> = listOf(OWNER, MANAGER, SUPERVISOR, CASHIER)
    }
}

/**
 * Per-person tweaks on top of a role, stored on the staff record as two small
 * comma separated lists. Keeping them as text avoids a schema migration every
 * time a permission is added, and unknown names are simply ignored — an old
 * record can never crash a newer build.
 */
object PermissionOverrides {

    fun encode(extra: Set<Permission>, revoked: Set<Permission>): Pair<String, String> =
        extra.joinToString(",") { it.name } to revoked.joinToString(",") { it.name }

    fun decode(csv: String): Set<Permission> =
        csv.split(",")
            .mapNotNull { Permission.fromKey(it) }
            .toSet()
}

/**
 * Resolved permissions for whoever is currently using the app. Screens read
 * this; the ViewModel enforces it. Both matter — hiding a button is a courtesy,
 * blocking the action is the actual rule.
 */
data class PermissionSet(
    val role: StaffRole,
    val staffId: Long,
    val staffName: String,
    val granted: Set<Permission>,
    /** True for a one-person shop, where nothing is ever locked. */
    val isSoloOwner: Boolean = false
) {
    fun can(permission: Permission): Boolean = granted.contains(permission)

    fun cannot(permission: Permission): Boolean = !can(permission)

    /** True if this person differs from the plain role template. */
    fun isCustomised(): Boolean = granted != role.permissions

    /**
     * Message shown when someone taps something they are not allowed to use.
     * Names the actual role so it does not sound like a bug.
     */
    fun denialMessage(permission: Permission): String = when {
        isSoloOwner -> "This is switched off for your shop."
        role == StaffRole.OWNER -> "\"${permission.label}\" is switched off for this shop."
        else -> "${role.friendlyName}s cannot ${permission.label.lowercase()}. " +
            "Ask the owner to allow it under More \u2192 Team."
    }

    companion object {
        fun of(role: StaffRole, staffId: Long = 0L, staffName: String = ""): PermissionSet =
            PermissionSet(role, staffId, staffName, role.permissions)

        /**
         * Role defaults, plus anything the owner specifically allowed this
         * person, minus anything the owner specifically took away. Revoking
         * wins over granting so "take this away" is always dependable.
         */
        fun resolve(
            role: StaffRole,
            staffId: Long,
            staffName: String,
            extraCsv: String,
            revokedCsv: String
        ): PermissionSet {
            val extra = PermissionOverrides.decode(extraCsv)
            val revoked = PermissionOverrides.decode(revokedCsv)
            return PermissionSet(
                role = role,
                staffId = staffId,
                staffName = staffName,
                granted = (role.permissions + extra) - revoked
            )
        }

        /** A one-person shop: no staff, no PIN, nothing locked. */
        fun soloOwner(shopName: String): PermissionSet = PermissionSet(
            role = StaffRole.OWNER,
            staffId = 0L,
            staffName = shopName.ifBlank { "Owner" },
            granted = Permission.entries.toSet(),
            isSoloOwner = true
        )

        /** Used before anyone has signed in. */
        val ownerFallback: PermissionSet = of(StaffRole.OWNER, 0L, "Owner")

        /** Signed out of a team shop: can see nothing until a PIN is entered. */
        val lockedOut: PermissionSet =
            PermissionSet(StaffRole.CASHIER, 0L, "", emptySet())
    }
}
