package com.example.data.model

/**
 * Ready-made option presets for the Add/Edit product screen, keyed by shop type.
 *
 * The Add/Edit screen has two levels of options:
 *  1. **Choices** — the first set (e.g. "Colour: Black/Green", "Portion:
 *     Regular/Full"). A [ChoiceSet] is a short list of names the owner taps to
 *     add as ready-made options.
 *  2. **Runs** — the second set (e.g. "Size: S/M/L/XL"). A [RunSet] is a whole
 *     size run the owner applies to every choice at once, then trims where one
 *     choice comes in fewer sizes ("Black only comes in L and XL").
 *
 * Every preset only *suggests*; nothing is locked in. Names are added without
 * touching prices or stock already entered, the group name is auto-filled only
 * when it is still blank, and everything remains editable through the existing
 * option / size editors. No database migration is needed — this feeds the same
 * `products.variants` text the editor already writes.
 *
 * [presetsFor] is keyed by the shop type chosen at setup (stable, survives
 * category renames), and it can re-order the sets so the ones relevant to the
 * product's current category come first ([ChoiceSet.categoryHints]).
 */
object ProductOptionPresets {

    /** A ready-made set of first-level choices, e.g. colours or portions. */
    data class ChoiceSet(
        val key: String,
        /** The heading shown above the chips, e.g. "Quick colours". */
        val label: String,
        /** The group name applied when it is still blank, e.g. "Colour". */
        val groupName: String,
        val choices: List<String>,
        /** Category keywords that bump this set to the front. */
        val categoryHints: List<String> = emptyList()
    )

    /** A ready-made run of second-level values, e.g. sizes. */
    data class RunSet(
        val key: String,
        /** The heading shown above the chips, e.g. "S–XXL". */
        val label: String,
        /** The sub-group name applied when it is still blank, e.g. "Size". */
        val subGroupName: String,
        val values: List<String>,
        val categoryHints: List<String> = emptyList()
    )

    data class ShopPresets(
        val choices: List<ChoiceSet>,
        val runs: List<RunSet>
    ) {
        val isEmpty: Boolean get() = choices.isEmpty() && runs.isEmpty()
    }

    /**
     * The presets for a shop type, with sets relevant to [category] moved to
     * the front. Ordering is otherwise stable (the declared order is kept).
     */
    fun presetsFor(shopTypeKey: String, category: String = ""): ShopPresets {
        val base = byShopType[shopTypeKey.uppercase()] ?: return ShopPresets(emptyList(), emptyList())
        val cat = category.lowercase()
        if (cat.isBlank()) return base
        fun match(hints: List<String>) = hints.any { cat.contains(it.lowercase()) }
        return ShopPresets(
            choices = base.choices.sortedBy { if (match(it.categoryHints)) 0 else 1 },
            runs = base.runs.sortedBy { if (match(it.categoryHints)) 0 else 1 }
        )
    }

    // ------------------------------------------------------------------
    // The catalogue. Choice sets come first, then runs, per shop type.
    // ------------------------------------------------------------------

    private val byShopType: Map<String, ShopPresets> = mapOf(
        "GROCERY" to ShopPresets(
            choices = listOf(
                ChoiceSet("weight", "Quick weights", "Weight",
                    listOf("250g", "500g", "1kg", "2kg", "5kg", "10kg"),
                    listOf("rice", "grain", "sugar", "flour", "dhal", "gram")),
                ChoiceSet("volume", "Quick volumes", "Volume",
                    listOf("250ml", "500ml", "750ml", "1L", "2L", "5L"),
                    listOf("oil", "milk", "water", "drink", "beverage")),
                ChoiceSet("flavour", "Quick flavours", "Flavour",
                    listOf("Cola", "Lemon", "Orange", "Mango", "Vanilla", "Chocolate", "Strawberry"),
                    listOf("drink", "beverage", "soda", "biscuit", "cream")),
                ChoiceSet("pack", "Quick pack sizes", "Pack",
                    listOf("Single", "2 Pack", "6 Pack", "12 Pack"),
                    listOf("biscuit", "snack", "chips"))
            ),
            runs = emptyList()
        ),
        "FOOD_CAFE" to ShopPresets(
            choices = listOf(
                ChoiceSet("portion", "Quick portions", "Portion",
                    listOf("Small", "Regular", "Full", "Half", "Large", "Jumbo"),
                    listOf("rice", "kottu", "portion", "plate", "hopper")),
                ChoiceSet("meat", "Quick meat / protein", "Meat",
                    listOf("Chicken", "Beef", "Mutton", "Fish", "Prawn", "Egg", "Vegetable"),
                    listOf("kottu", "rice", "curry", "fried", "biriyani", "noodle")),
                ChoiceSet("spice", "Quick spice level", "Spice",
                    listOf("Mild", "Medium", "Hot", "Extra Hot"),
                    listOf("curry", "kottu", "gravy")),
                ChoiceSet("rice", "Quick rice types", "Rice",
                    listOf("Basmati", "Keeri", "Samba", "Nadu"),
                    listOf("biriyani", "rice"))
            ),
            runs = listOf(
                RunSet("drink", "Drink sizes", "Size",
                    listOf("Small", "Medium", "Large"),
                    listOf("tea", "coffee", "juice", "drink", "milkshake")),
                RunSet("addon", "Add-ons", "Add-on",
                    listOf("Extra Egg", "Extra Cheese", "Extra Gravy", "Extra Meat"),
                    listOf("kottu", "rice", "noodle"))
            )
        ),
        "PHARMACY" to ShopPresets(
            choices = listOf(
                ChoiceSet("strength", "Quick strengths", "Strength",
                    listOf("100mg", "250mg", "500mg", "1g"),
                    listOf("tablet", "capsule", "dose")),
                ChoiceSet("pack", "Quick pack sizes", "Pack",
                    listOf("10", "20", "30", "100"),
                    listOf("tablet", "capsule", "strip")),
                ChoiceSet("flavour", "Quick syrup flavours", "Flavour",
                    listOf("Orange", "Banana", "Strawberry", "Plain"),
                    listOf("syrup", "suspension", "drops"))
            ),
            runs = emptyList()
        ),
        "CLOTHING" to ShopPresets(
            choices = listOf(
                ChoiceSet("colour", "Quick colours", "Colour",
                    listOf("Black", "White", "Grey", "Navy", "Blue", "Red", "Green",
                        "Maroon", "Beige", "Brown", "Yellow", "Pink")),
                ChoiceSet("fit", "Quick fits", "Fit",
                    listOf("Slim", "Regular", "Relaxed", "Loose"),
                    listOf("shirt", "trouser", "jean", "pant"))
            ),
            runs = listOf(
                RunSet("sizes", "S–XXL", "Size",
                    listOf("S", "M", "L", "XL", "XXL")),
                RunSet("sizes2", "XS–XL", "Size",
                    listOf("XS", "S", "M", "L", "XL")),
                RunSet("waist", "Waist 32–40", "Waist",
                    listOf("32", "34", "36", "38", "40"),
                    listOf("trouser", "jean", "pant")),
                RunSet("waist2", "Waist 28–42", "Waist",
                    listOf("28", "30", "32", "34", "36", "38", "40", "42"),
                    listOf("trouser", "jean", "pant")),
                RunSet("collar", "Collar 14.5–17", "Collar",
                    listOf("14.5", "15", "15.5", "16", "16.5", "17"),
                    listOf("shirt")),
                RunSet("kids", "Kids 2–12", "Size",
                    listOf("2", "4", "6", "8", "10", "12"),
                    listOf("kids", "baby", "school", "uniform")),
                RunSet("footwear", "Footwear 5–11", "Size",
                    listOf("5", "6", "7", "8", "9", "10", "11"),
                    listOf("shoe", "slipper", "sandal"))
            )
        ),
        "ELECTRONICS" to ShopPresets(
            choices = listOf(
                ChoiceSet("colour", "Quick colours", "Colour",
                    listOf("Black", "White", "Grey", "Blue", "Red", "Green")),
                ChoiceSet("storage", "Quick storage", "Storage",
                    listOf("16GB", "32GB", "64GB", "128GB", "256GB", "512GB", "1TB"),
                    listOf("flash", "memory", "card", "ssd", "storage")),
                ChoiceSet("length", "Quick cable lengths", "Length",
                    listOf("0.5m", "1m", "1.5m", "2m", "3m"),
                    listOf("cable", "cord", "wire")),
                ChoiceSet("power", "Quick power", "Power",
                    listOf("18W", "20W", "33W", "45W", "65W"),
                    listOf("charger", "adapter", "power", "watt"))
            ),
            runs = emptyList()
        ),
        "STATIONERY" to ShopPresets(
            choices = listOf(
                ChoiceSet("ink", "Quick ink colours", "Ink",
                    listOf("Blue", "Black", "Red", "Green"),
                    listOf("pen", "ink", "marker")),
                ChoiceSet("pages", "Quick page counts", "Pages",
                    listOf("80", "120", "160", "200", "400"),
                    listOf("book", "exercise", "copy")),
                ChoiceSet("size", "Quick paper sizes", "Size",
                    listOf("A4", "A5", "A6"),
                    listOf("paper", "drawing", "book")),
                ChoiceSet("grade", "Quick pencil grades", "Grade",
                    listOf("HB", "2B", "4B", "6B"),
                    listOf("pencil"))
            ),
            runs = emptyList()
        ),
        "SALON" to ShopPresets(
            choices = listOf(
                ChoiceSet("length", "Quick hair lengths", "Length",
                    listOf("Short", "Medium", "Long"),
                    listOf("hair", "colour", "treatment", "cut")),
                ChoiceSet("size", "Quick retail sizes", "Size",
                    listOf("100ml", "250ml", "500ml", "1L"),
                    listOf("shampoo", "conditioner", "serum", "retail"))
            ),
            runs = emptyList()
        ),
        "REPAIR" to ShopPresets(
            choices = listOf(
                ChoiceSet("brand", "Quick device brands", "Brand",
                    listOf("iPhone", "Samsung", "Xiaomi", "Oppo", "Vivo", "Realme", "Huawei", "Other"),
                    listOf("screen", "battery", "replacement", "repair", "port")),
                ChoiceSet("storage", "Quick storage", "Storage",
                    listOf("32GB", "64GB", "128GB", "256GB"),
                    listOf("model", "phone", "storage"))
            ),
            runs = emptyList()
        ),
        "FLOWERS" to ShopPresets(
            choices = listOf(
                ChoiceSet("colour", "Quick colours", "Colour",
                    listOf("Red", "White", "Pink", "Yellow", "Orange", "Purple", "Mixed"),
                    listOf("rose", "bouquet", "flower")),
                ChoiceSet("stems", "Quick stem counts", "Stems",
                    listOf("5", "10", "20", "50"),
                    listOf("stem", "bunch", "rose"))
            ),
            runs = listOf(
                RunSet("size", "Bouquet sizes", "Size",
                    listOf("Small", "Medium", "Large"),
                    listOf("bouquet"))
            )
        ),
        "SHOES" to ShopPresets(
            choices = listOf(
                ChoiceSet("colour", "Quick colours", "Colour",
                    listOf("Black", "Brown", "Tan", "White", "Navy", "Grey"))
            ),
            runs = listOf(
                RunSet("uk", "UK 5–11", "Size",
                    listOf("5", "6", "7", "8", "9", "10", "11")),
                RunSet("eu", "EU 36–45", "Size",
                    listOf("36", "37", "38", "39", "40", "41", "42", "43", "44", "45")),
                RunSet("kids", "Kids 8–13", "Size",
                    listOf("8", "9", "10", "11", "12", "13"),
                    listOf("kids"))
            )
        ),
        "HARDWARE" to ShopPresets(
            choices = listOf(
                ChoiceSet("size", "Quick sizes", "Size",
                    listOf("½", "¾", "1", "1½", "2", "3"),
                    listOf("pipe", "wrench", "bolt", "nail", "screw", "spanner", "elbow")),
                ChoiceSet("colour", "Quick paint colours", "Colour",
                    listOf("White", "Cream", "Grey", "Beige", "Black"),
                    listOf("paint", "enamel", "emulsion", "primer")),
                ChoiceSet("gauge", "Quick wire gauges", "Gauge",
                    listOf("1mm", "1.5mm", "2.5mm", "4mm"),
                    listOf("wire", "cable", "electrical", "conduit"))
            ),
            runs = emptyList()
        ),
        "TOYS_GIFTS" to ShopPresets(
            choices = listOf(
                ChoiceSet("size", "Quick sizes", "Size",
                    listOf("Small", "Medium", "Large"),
                    listOf("teddy", "toy", "plush", "soft")),
                ChoiceSet("colour", "Quick colours", "Colour",
                    listOf("Red", "Blue", "Green", "Yellow", "Pink", "Purple", "Mixed")),
                ChoiceSet("age", "Quick age ranges", "Age",
                    listOf("0-2", "3-5", "6-8", "9-12"),
                    listOf("kids", "baby", "educational", "puzzle"))
            ),
            runs = emptyList()
        ),
        "SPORTS" to ShopPresets(
            choices = listOf(
                ChoiceSet("ball", "Quick ball sizes", "Ball",
                    listOf("3", "4", "5"),
                    listOf("football", "ball")),
                ChoiceSet("weight", "Quick weights", "Weight",
                    listOf("2.5kg", "5kg", "7.5kg", "10kg", "15kg", "20kg"),
                    listOf("dumbbell", "weight", "plate", "kettle")),
                ChoiceSet("colour", "Quick colours", "Colour",
                    listOf("Black", "White", "Red", "Blue", "Green", "Yellow"))
            ),
            runs = listOf(
                RunSet("apparel", "S–XXL", "Size",
                    listOf("S", "M", "L", "XL", "XXL"),
                    listOf("shirt", "short", "tracksuit", "wear", "jersey"))
            )
        )
    )
}
