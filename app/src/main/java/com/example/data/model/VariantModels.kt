package com.example.data.model

/**
 * Flexible product variants.
 *
 * A product can have simple options ("Regular|1200", "Full|1800") or deep,
 * nested choices:
 *
 * ```
 * Rice: Basmati|Keeri
 * Portion: Regular|Full
 * Basmati/Regular|1200
 * Basmati/Full|1800
 * Keeri/Regular|1100
 * Keeri/Full|1700
 * ```
 *
 * The storage stays in the existing `products.variants` TEXT column, so this
 * feature needs no migration: it is a richer way of writing the same field.
 *
 * The Add/Edit product screen additionally writes one exact line per
 * sellable combination, each starting with `=`:
 *
 * ```
 * Colour: Green|Black
 * Size: 32|34|36|40|L|XL
 * =Green/32|850
 * =Green/34|850
 * =Black/L|850
 * =Black/XL|900
 * ```
 *
 * Those lines are the truth whenever they exist, because a plain cross-product
 * cannot say "Green comes in sizes but Black only comes in L and XL". Products
 * saved before them keep working: without `=` lines the groups are crossed as
 * before.
 */
data class VariantGroup(
    val name: String,
    val options: List<String>
)

data class VariantCombination(
    val labels: List<String>,
    val displayName: String,
    val price: Double
)

/**
 * A single choice inside an option group while the owner is editing it on the
 * Add/Edit product screen. [priceAdjustment] is how much this choice adds to
 * (or subtracts from) the base selling price, so "Basmati +400" prices every
 * Basmati combination 400 higher without typing each combination by hand.
 */
data class VariantOptionDraft(
    val name: String,
    val priceAdjustment: Double = 0.0
)

/** A named group of mutually-exclusive choices, e.g. "Rice type: Keeri/Basmati". */
data class VariantGroupDraft(
    val name: String,
    val options: List<VariantOptionDraft>
)

object VariantCatalog {

    /**
     * Marks a line that names one exact sellable combination, e.g.
     * `=Green/32|850`. The Add/Edit product screen writes one per row of its
     * combination table, which is how "Green comes in 32/34/36/40 but Black
     * only comes in L/XL" survives being saved.
     */
    const val COMBO_PREFIX = '='

    /** Stable, case-insensitive key for one combination's labels. */
    fun comboKey(labels: List<String>): String =
        labels.joinToString("/").trim().lowercase()

    /**
     * The exact combinations the editor saved, in the order it saved them.
     * The price is null for a line that only names the combination.
     */
    fun parseDefinedCombos(raw: String): List<Pair<List<String>, Double?>> =
        lines(raw).mapNotNull { line ->
            if (!line.startsWith(COMBO_PREFIX)) return@mapNotNull null
            val body = line.removePrefix(COMBO_PREFIX.toString()).trim()
            val bar = body.lastIndexOf('|')
            val head = if (bar > 0) body.substring(0, bar) else body
            val labels = splitOptionPath(head)
            if (labels.isEmpty()) return@mapNotNull null
            val price = if (bar > 0) body.substring(bar + 1).trim().toDoubleOrNull() else null
            labels to price
        }

    /** True when the field carries exact combination lines. */
    fun hasDefinedCombos(raw: String): Boolean = parseDefinedCombos(raw).isNotEmpty()

    /** Parses the group lines: `Rice: Basmati|Keeri`. */
    fun parseGroups(raw: String): List<VariantGroup> {
        val groups = mutableListOf<VariantGroup>()
        lines(raw).forEach { line ->
            if (line.startsWith(COMBO_PREFIX)) return@forEach
            val colon = line.indexOf(':')
            if (colon > 0) {
                val name = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                val options = value
                    .split(Regex("""[|/]"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (name.isNotBlank() && options.isNotEmpty()) {
                    groups.add(VariantGroup(name, options))
                }
            }
        }
        return groups
    }

    /** True when the field contains nested group declarations. */
    fun isNested(raw: String): Boolean = parseGroups(raw).size > 1

    /**
     * Builds every purchasable combination and its final price.
     *
     * Price precedence:
     *  1. an explicit combination line, e.g. `Basmati/Regular|1200`
     *  2. the base selling price plus any option deltas (`Full+200`, `Keeri+50`)
     *  3. the product base / selling price
     *
     * When the field carries exact `=Green/32|850` lines, those lines decide
     * both *which* combinations exist and what they cost: every first choice
     * can then carry its own second choices.
     */
    fun buildCombinations(raw: String, basePrice: Double): List<VariantCombination> {
        val groups = parseGroups(raw)

        val defined = parseDefinedCombos(raw)
        if (defined.isNotEmpty()) {
            val overrides = parseOverridePrices(raw)
            val deltas = parseDeltas(raw, groups)
            return defined.map { (labels, explicitPrice) ->
                val explicit = explicitPrice
                    ?: overrides.firstOrNull { matches(labels, it.first) }?.second
                val delta = labels.sumOf { deltas[it.lowercase()] ?: 0.0 }
                VariantCombination(
                    labels = labels,
                    displayName = labels.joinToString("/"),
                    price = explicit ?: (basePrice + delta).coerceAtLeast(0.0)
                )
            }
        }

        if (groups.isNotEmpty()) {
            val optionsPerGroup = groups.map { group ->
                group.options.map { it to group.name }
            }
            val combos = cartesian(optionsPerGroup)
            val overrides = parseOverridePrices(raw)
            val deltas = parseDeltas(raw, groups)

            return combos.mapIndexed { index, selected ->
                val labels = selected.map { it.first }
                val display = labels.joinToString("/")
                val explicit = overrides.firstOrNull { matches(labels, it.first) }?.second
                val delta = labels.sumOf { deltas[it.lowercase()] ?: 0.0 }
                VariantCombination(
                    labels = labels,
                    displayName = display,
                    price = explicit ?: (basePrice + delta).coerceAtLeast(0.0)
                )
            }
        }

        // No group lines: keep the old "Name|price" / "Name" behaviour.
        val simple = lines(raw).mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val name = parts.getOrNull(0).orEmpty()
            if (name.isBlank()) return@mapNotNull null
            name to (parts.getOrNull(1)?.toDoubleOrNull() ?: basePrice)
        }.distinct()

        return simple.map { (name, price) ->
            VariantCombination(
                labels = listOf(name),
                displayName = name,
                price = price.coerceAtLeast(0.0)
            )
        }
    }

    /** The product name normally used for a generated stockable child line. */
    fun childName(parentName: String, combo: VariantCombination): String {
        if (combo.labels.size == 1 && combo.labels.first().equals(parentName, ignoreCase = true)) {
            return parentName
        }
        return "$parentName - ${combo.displayName}"
    }

    /** Finds a saved child row matching this combination. */
    fun findChild(
        children: List<ProductEntity>,
        parentId: Long,
        parentName: String,
        combo: VariantCombination
    ): ProductEntity? {
        val expected = childName(parentName, combo)
        return children.firstOrNull {
            it.parentProductId == parentId &&
                it.isVariant &&
                it.name.equals(expected, ignoreCase = true)
        }
    }

    /** A short one-line summary for product cards and pickers. */
    fun summary(raw: String): String {
        val groups = parseGroups(raw)
        if (groups.isNotEmpty()) {
            return groups.joinToString(" · ") { g -> "${g.name}: ${g.options.joinToString(" / ")}" }
        }
        return lines(raw)
            .filterNot { it.startsWith(COMBO_PREFIX) }
            .joinToString(" · ") { line -> line.split("|")[0].trim().ifBlank { line } }
    }

    // ------------------------------------------------------------------
    // Structured editor round-trip (Add/Edit product screen)
    // ------------------------------------------------------------------

    /**
     * Reads a stored variants string back into the editable groups the
     * Add/Edit product screen shows. Handles both the deep "Group: a|b"
     * format and the legacy one-line "Name|price" format (which becomes a
     * single implicit group named "Option").
     */
    fun parseDrafts(raw: String, basePrice: Double): List<VariantGroupDraft> {
        val groups = parseGroups(raw)
        if (groups.isNotEmpty()) {
            val deltas = parseDeltas(raw, groups)
            val overrides = parseOverridePrices(raw)
            return groups.map { group ->
                VariantGroupDraft(
                    name = group.name,
                    options = group.options.map { option ->
                        val fromDelta = deltas[option.lowercase()]
                        val fromOverride = overrides
                            .firstOrNull { it.first.size == 1 && it.first[0].equals(option, true) }
                            ?.let { it.second - basePrice }
                        VariantOptionDraft(option, fromDelta ?: fromOverride ?: 0.0)
                    }
                )
            }
        }

        // Legacy simple format: "Name" or "Name|price" per line.
        val simple = lines(raw).mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val name = parts.getOrNull(0).orEmpty()
            if (name.isBlank()) null else name to (parts.getOrNull(1)?.toDoubleOrNull() ?: basePrice)
        }.distinctBy { it.first }
        if (simple.isEmpty()) return emptyList()
        return listOf(
            VariantGroupDraft(
                name = "Option",
                options = simple.map { (name, price) ->
                    VariantOptionDraft(name, price - basePrice)
                }
            )
        )
    }

    /**
     * Writes the editor state back into the stored string format the rest of
     * the app (and the stockable child-line generator) already understands:
     * one "Group: a|b" line per group plus one "Name+delta" line for every
     * choice whose price differs from the base. Blank groups are dropped.
     */
    fun encodeDrafts(groups: List<VariantGroupDraft>): String {
        val clean = groups
            .map { group ->
                VariantGroupDraft(
                    name = cleanName(group.name),
                    options = group.options
                        .mapNotNull { opt ->
                            val name = cleanName(opt.name)
                            if (name.isBlank()) null else VariantOptionDraft(name, opt.priceAdjustment)
                        }
                        .distinctBy { it.name.lowercase() }
                )
            }
            .filter { it.name.isNotBlank() && it.options.isNotEmpty() }

        val sb = StringBuilder()
        clean.forEach { group ->
            sb.append(group.name).append(": ")
                .append(group.options.joinToString("|") { it.name })
                .append('\n')
        }
        clean.forEach { group ->
            group.options.forEach { option ->
                if (option.priceAdjustment != 0.0) {
                    sb.append(option.name)
                        .append(if (option.priceAdjustment > 0) "+" else "")
                        .append(formatPrice(option.priceAdjustment))
                        .append('\n')
                }
            }
        }
        return sb.toString().trimEnd('\n')
    }

    /** Strips the characters the variants grammar uses as separators. */
    private fun cleanName(name: String): String =
        name.replace(Regex("""[|/:;=\n\r]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun formatPrice(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun cartesian(groups: List<List<Pair<String, String>>>): List<List<Pair<String, String>>> {
        if (groups.isEmpty()) return emptyList()
        var result: List<List<Pair<String, String>>> = listOf(emptyList())
        groups.forEach { options ->
            result = result.flatMap { prefix -> options.map { prefix + it } }
        }
        return result
    }

    /** Lines like `Basmati/Regular|1200` or a quoted `Basmati Regular|1200`. */
    private fun parseOverridePrices(raw: String): List<Pair<List<String>, Double>> =
        lines(raw).mapNotNull { line ->
            // `=` lines are whole combinations; they are read by
            // [parseDefinedCombos], not as a price override.
            if (line.startsWith(COMBO_PREFIX)) return@mapNotNull null
            val bar = line.lastIndexOf('|')
            if (bar <= 0) return@mapNotNull null
            val path = line.substring(0, bar).trim()
            val price = line.substring(bar + 1).trim().toDoubleOrNull() ?: return@mapNotNull null
            val tokens = tokenizeOptionPath(path)
            if (tokens.isEmpty()) null else tokens to price
        }

    /** Option deltas like `Full+200`, `Keeri+50`, `Basmati-20`. */
    private fun parseDeltas(raw: String, groups: List<VariantGroup>): Map<String, Double> {
        val known = groups.flatMap { it.options }.map { it.lowercase() }.toSet()
        val deltas = mutableMapOf<String, Double>()
        lines(raw).forEach { line ->
            val match = Regex("""^(.*?)([+\-])(\d+(?:\.\d+)?)$""").matchEntire(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val sign = match.groupValues[2]
                val amount = match.groupValues[3].toDoubleOrNull() ?: return@forEach
                if (name.lowercase() in known) {
                    deltas[name.lowercase()] = if (sign == "-") -amount else amount
                }
            }
        }
        return deltas
    }

    /** Converts the left-hand side of a price line into option tokens. */
    private fun tokenizeOptionPath(raw: String): List<String> {
        val slash = raw.split(Regex("""[/|]""")).map { it.trim() }.filter { it.isNotBlank() }
        if (slash.size > 1 || slash.isNotEmpty()) return slash
        return raw.split(Regex("""\s+""")).map { it.trim() }.filter { it.isNotBlank() }
    }

    /** Splits `Green/32` into ["Green", "32"]. */
    private fun splitOptionPath(raw: String): List<String> =
        splitNames(raw, Regex("""[/|]""")).ifEmpty { splitNames(raw, Regex("""\s+""")) }

    private fun splitNames(raw: String, separator: Regex): List<String> =
        raw.split(separator).map { it.trim() }.filter { it.isNotBlank() }

    /** Matches a price-override path to a combination, order-insensitive. */
    private fun matches(labels: List<String>, path: List<String>): Boolean {
        if (labels.size != path.size) return false
        val lower = labels.map { it.lowercase() }.toSet()
        return path.all { it.lowercase() in lower }
    }

    private fun lines(raw: String): List<String> =
        raw.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n")
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
}
