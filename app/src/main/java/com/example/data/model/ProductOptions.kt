package com.example.data.model

/**
 * The option tree the Add/Edit product screen edits.
 *
 * A shop sells the same thing in a few shapes — a biryani by rice and portion,
 * a trouser by colour and then by size. Two levels cover every shop we have
 * met, and the second level is deliberately *per option*: Green comes in
 * 32/34/36/40 while Black only comes in L and XL. A plain cross-product of two
 * flat lists cannot say that, which is why the stored text keeps one exact line
 * per sellable combination (see [VariantCatalog.COMBO_PREFIX]).
 *
 * Everything still lives in the existing `products.variants` TEXT column, so
 * none of this needs a database migration.
 */

/** One size / sub-choice under an option, e.g. "32" or "XL". */
data class ProductSubOptionDraft(
    val name: String = "",
    /** Final selling price of this combination, in rupees. */
    val price: Double = 0.0,
    /** How many are on the shelf right now. */
    val stock: Double = 0.0
)

/** One choice in the first set, e.g. "Green", plus the sizes it splits into. */
data class ProductOptionDraft(
    val name: String = "",
    /** Selling price used while this choice is not split any further. */
    val price: Double = 0.0,
    /** Opening count used while this choice is not split any further. */
    val stock: Double = 0.0,
    /** Empty means "sell it exactly as it is". */
    val subOptions: List<ProductSubOptionDraft> = emptyList()
)

/** What one named set of choices is called, plus the choices themselves. */
data class ProductOptionsDraft(
    val hasOptions: Boolean = false,
    /** Name of the first set, e.g. "Portion", "Colour", "Type". */
    val groupName: String = "",
    /** Name of the second set, e.g. "Size". Only used when something is split. */
    val subGroupName: String = "",
    val options: List<ProductOptionDraft> = emptyList()
) {
    /** True when at least one option has been split into sizes. */
    val isSplit: Boolean get() = options.any { it.subOptions.isNotEmpty() }
}

object ProductOptions {

    /** Stable key for one combination, shared with the saved child rows. */
    fun comboKey(labels: List<String>): String = VariantCatalog.comboKey(labels)

    /**
     * Every final, sellable combination in the order the owner typed them.
     * Each carries its own price, and the unit is added by the screens.
     */
    fun combinations(draft: ProductOptionsDraft): List<VariantCombination> {
        if (!draft.hasOptions) return emptyList()
        val out = mutableListOf<VariantCombination>()
        draft.options.forEach { option ->
            val name = option.name.trim()
            if (name.isBlank()) return@forEach
            if (option.subOptions.isEmpty()) {
                out.add(VariantCombination(listOf(name), name, option.price))
            } else {
                option.subOptions.forEach { sub ->
                    val subName = sub.name.trim()
                    if (subName.isBlank()) return@forEach
                    out.add(
                        VariantCombination(
                            labels = listOf(name, subName),
                            displayName = "$name/$subName",
                            price = sub.price
                        )
                    )
                }
            }
        }
        return out
    }

    /** Opening stock per combination, keyed by [comboKey]. */
    fun combinationStock(draft: ProductOptionsDraft): Map<String, Double> {
        val out = linkedMapOf<String, Double>()
        draft.options.forEach { option ->
            val name = option.name.trim()
            if (name.isBlank()) return@forEach
            if (option.subOptions.isEmpty()) {
                out[comboKey(listOf(name))] = option.stock
            } else {
                option.subOptions.forEach { sub ->
                    val subName = sub.name.trim()
                    if (subName.isBlank()) return@forEach
                    out[comboKey(listOf(name, subName))] = sub.stock
                }
            }
        }
        return out
    }

    /**
     * Writes the draft into the `products.variants` text: one line naming each
     * set, then one exact line per sellable combination with its price.
     *
     * Blank options and blank sizes are dropped rather than saved as empty
     * rows, because an empty row would become an un-sellable stock line.
     */
    fun encode(draft: ProductOptionsDraft): String {
        if (!draft.hasOptions) return ""
        val options = draft.options
            .map { option ->
                ProductOptionDraft(
                    name = clean(option.name),
                    price = option.price,
                    stock = option.stock,
                    subOptions = option.subOptions
                        .map { it.copy(name = clean(it.name)) }
                        .filter { it.name.isNotBlank() }
                )
            }
            .filter { it.name.isNotBlank() }
        if (options.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append(clean(draft.groupName).ifBlank { OPTION_GROUP_NAME }).append(": ")
            .append(options.joinToString("|") { it.name })
            .append('\n')

        if (options.any { it.subOptions.isNotEmpty() }) {
            val seen = LinkedHashSet<String>()
            options.forEach { option -> option.subOptions.forEach { seen.add(it.name) } }
            if (seen.isNotEmpty()) {
                sb.append(clean(draft.subGroupName).ifBlank { SIZE_GROUP_NAME }).append(": ")
                    .append(seen.joinToString("|"))
                    .append('\n')
            }
        }

        options.forEach { option ->
            if (option.subOptions.isEmpty()) {
                sb.append(VariantCatalog.COMBO_PREFIX)
                    .append(option.name).append('|').append(money(option.price))
                    .append('\n')
            } else {
                option.subOptions.forEach { sub ->
                    sb.append(VariantCatalog.COMBO_PREFIX)
                        .append(option.name).append('/').append(sub.name)
                        .append('|').append(money(sub.price))
                        .append('\n')
                }
            }
        }
        return sb.toString().trimEnd('\n')
    }

    /**
     * Reads stored options text back into the editable tree.
     *
     * [stockOf] supplies the on-hand count of an existing combination, normally
     * read from the child rows the product already has, so opening the editor
     * never silently resets the shelf to zero.
     */
    fun decode(
        raw: String,
        basePrice: Double,
        stockOf: (List<String>) -> Double = { 0.0 }
    ): ProductOptionsDraft {
        if (raw.isBlank()) return ProductOptionsDraft()
        val combos = VariantCatalog.buildCombinations(raw, basePrice)
        if (combos.isEmpty()) return ProductOptionsDraft()

        val groups = VariantCatalog.parseGroups(raw)
        val order = mutableListOf<String>()
        val subs = linkedMapOf<String, MutableList<ProductSubOptionDraft>>()
        val priceOf = linkedMapOf<String, Double>()

        combos.forEach { combo ->
            val first = combo.labels.firstOrNull()?.trim().orEmpty()
            if (first.isBlank()) return@forEach
            if (!subs.containsKey(first)) {
                order.add(first)
                subs[first] = mutableListOf()
            }
            val rest = combo.labels.drop(1)
            if (rest.isEmpty()) {
                priceOf[first] = combo.price
            } else {
                subs[first]!!.add(
                    ProductSubOptionDraft(
                        name = rest.joinToString("/"),
                        price = combo.price,
                        stock = stockOf(combo.labels)
                    )
                )
                val current = priceOf[first]
                // If the owner ever un-splits this option, start it at the
                // cheapest size rather than at a surprising number.
                priceOf[first] = if (current == null) combo.price else minOf(current, combo.price)
            }
        }

        val options = order.map { name ->
            ProductOptionDraft(
                name = name,
                price = priceOf[name] ?: basePrice,
                stock = stockOf(listOf(name)),
                subOptions = subs[name].orEmpty()
            )
        }

        return ProductOptionsDraft(
            hasOptions = true,
            groupName = groups.getOrNull(0)?.name.orEmpty().ifBlank { OPTION_GROUP_NAME },
            subGroupName = groups.getOrNull(1)?.name.orEmpty(),
            options = options
        )
    }

    /** Names offered as one-tap suggestions for the first set. */
    val GROUP_NAME_SUGGESTIONS = listOf("Portion", "Size", "Colour", "Type")

    /** Names offered as one-tap suggestions for the second set. */
    val SUB_GROUP_NAME_SUGGESTIONS = listOf("Size", "Weight", "Length", "Pack")

    /**
     * One-tap colour choices for clothing & fashion shops. Tapping one adds it
     * as a ready-made option so the owner never has to type "Black", "Green"…
     * out by hand.
     */
    val CLOTHING_COLOURS = listOf(
        "Black", "White", "Grey", "Navy", "Blue", "Red", "Green",
        "Maroon", "Beige", "Brown", "Yellow", "Pink"
    )

    /** A ready-made set of sizes the owner can apply with one tap. */
    data class SizePreset(val label: String, val sizes: List<String>)

    /**
     * Common size runs for clothing and footwear. Applying one hands every
     * colour the same sizes to begin with; a colour that comes in fewer (or
     * different) sizes is then trimmed or topped up in the size editor, which
     * is exactly what "Black only comes in L and XL" needs.
     */
    val SIZE_PRESETS = listOf(
        SizePreset("S–XXL", listOf("S", "M", "L", "XL", "XXL")),
        SizePreset("XS–XL", listOf("XS", "S", "M", "L", "XL")),
        SizePreset("Waist 32–40", listOf("32", "34", "36", "38", "40")),
        SizePreset("Waist 28–42", listOf("28", "30", "32", "34", "36", "38", "40", "42")),
        SizePreset("Collar 14.5–17", listOf("14.5", "15", "15.5", "16", "16.5", "17")),
        SizePreset("Footwear 5–11", listOf("5", "6", "7", "8", "9", "10", "11")),
        SizePreset("Kids 2–12", listOf("2", "4", "6", "8", "10", "12"))
    )

    private const val OPTION_GROUP_NAME = "Option"
    private const val SIZE_GROUP_NAME = "Size"

    /** Strips the separators the storage grammar uses. */
    private fun clean(name: String): String =
        name.replace(Regex("""[|/:;=\n\r]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun money(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
