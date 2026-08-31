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

object VariantCatalog {

    /** Parses the group lines: `Rice: Basmati|Keeri`. */
    fun parseGroups(raw: String): List<VariantGroup> {
        val groups = mutableListOf<VariantGroup>()
        lines(raw).forEach { line ->
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
     */
    fun buildCombinations(raw: String, basePrice: Double): List<VariantCombination> {
        val groups = parseGroups(raw)

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
        return lines(raw).joinToString(" · ") { line -> line.split("|")[0].trim().ifBlank { line } }
    }

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
