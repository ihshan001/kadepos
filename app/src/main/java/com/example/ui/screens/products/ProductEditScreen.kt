package com.example.ui.screens.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ProductEntity
import com.example.data.model.ProductOptionDraft
import com.example.data.model.ProductOptions
import com.example.data.model.ProductOptionsDraft
import com.example.data.model.ProductSubOptionDraft
import com.example.data.model.VariantCatalog
import com.example.data.model.VariantCombination
import com.example.ui.components.AppTextField
import com.example.ui.components.DropdownField
import com.example.ui.components.NumberTextField
import com.example.ui.theme.BrandOnPrimary
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryDark
import com.example.ui.theme.BrandSurface
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.CurrencyUtils

/**
 * The Add/Edit product screen.
 *
 * It has two ways of filling in exactly the same item:
 *
 *  * **Easy mode** — a guided wizard, one plain question at a time, for a shop
 *    owner who has never used a stock system.
 *  * **Normal mode** — one dense form, everything visible at once, for someone
 *    who already knows what they are doing.
 *
 * Both write into a single [ProductForm], so flipping between them never loses
 * anything that was typed, and both save the identical product.
 */

/** Units offered as one-tap chips. Anything else can still be typed. */
private val UNIT_CHIPS = listOf(
    "Piece", "Kg", "g", "Litre", "ml", "Bottle", "Pack", "Portion", "Meter"
)

private const val DEFAULT_CATEGORY = "General"
private const val DEFAULT_LOW_STOCK = "3"
private const val TOTAL_EASY_STEPS = 4

enum class ProductEditMode(val label: String) {
    EASY("Easy mode"),
    NORMAL("Normal mode")
}

/** Everything the screen collected, handed back in one piece when saving. */
data class ProductSaveRequest(
    val id: Long,
    val name: String,
    val sellingPrice: Double,
    val costPrice: Double,
    val barcode: String,
    val sku: String,
    val category: String,
    val subCategory: String,
    val unit: String,
    val openingStock: Double,
    val lowStock: Double,
    val isTracked: Boolean,
    val isFavourite: Boolean,
    val variants: String,
    val comboStock: Map<String, Double>
)

/**
 * One editable product. Kept as a single immutable value so every step of the
 * wizard and every card of the normal form read and write the same state.
 */
private data class ProductForm(
    val mode: ProductEditMode = ProductEditMode.EASY,
    val easyStep: Int = 1,
    val name: String = "",
    val unit: String = "Piece",
    val extraUnits: List<String> = emptyList(),
    val price: String = "",
    val cost: String = "",
    val barcode: String = "",
    val sku: String = "",
    val category: String = DEFAULT_CATEGORY,
    val subCategory: String = "",
    val tracked: Boolean = true,
    val openingStock: String = "",
    val lowStock: String = DEFAULT_LOW_STOCK,
    val favourite: Boolean = false,
    val options: ProductOptionsDraft = ProductOptionsDraft()
) {
    val priceValue: Double get() = price.toDoubleOrNull() ?: 0.0
    val costValue: Double get() = cost.toDoubleOrNull() ?: 0.0
    val stockValue: Double get() = openingStock.toDoubleOrNull() ?: 0.0
    val lowStockValue: Double get() = lowStock.toDoubleOrNull() ?: 0.0
    val unitChoices: List<String> get() = (UNIT_CHIPS + extraUnits).distinct()

    /** The combinations the table and the picker will show. */
    fun combinations(): List<VariantCombination> = ProductOptions.combinations(options)
}

private fun ProductForm.updateOptions(draft: ProductOptionsDraft): ProductForm =
    copy(options = draft)

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@Composable
fun ProductEditScreen(
    product: ProductEntity?,
    /** Existing stock lines of [product], so counts are never reset to zero. */
    children: List<ProductEntity> = emptyList(),
    categoryOptions: List<String> = emptyList(),
    /** Sub-categories per category, so the second list follows the first. */
    subCategoryOptions: Map<String, List<String>> = emptyList(),
    onSave: (ProductSaveRequest) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var form by remember(product?.id) { mutableStateOf(loadForm(product, children)) }
    val scroll = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenHeader(
                    isEdit = product != null,
                    mode = form.mode,
                    onModeChange = { form = form.copy(mode = it) },
                    onDismiss = onDismiss
                )

                HorizontalDivider(color = LightBorder)

                Box(modifier = Modifier.weight(1f)) {
                    if (form.mode == ProductEditMode.EASY) {
                        EasyMode(
                            form = form,
                            onChange = { form = it },
                            onSave = {
                                buildRequest(form, product).let(onSave)
                            },
                            onDismiss = onDismiss
                        )
                    } else {
                        NormalMode(
                            form = form,
                            onChange = { form = it },
                            categoryOptions = categoryOptions,
                            subCategoryOptions = subCategoryOptions,
                            scrollState = scroll,
                            onSave = { buildRequest(form, product).let(onSave) },
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading / saving the form
// ---------------------------------------------------------------------------

private fun loadForm(product: ProductEntity?, children: List<ProductEntity>): ProductForm {
    if (product == null) return ProductForm()
    val stockOf: (List<String>) -> Double = { labels ->
        VariantCatalog.findChild(
            children = children,
            parentId = product.id,
            parentName = product.name,
            combo = VariantCombination(labels, labels.joinToString("/"), 0.0)
        )?.currentStock ?: 0.0
    }
    return ProductForm(
        name = product.name,
        unit = product.unit.ifBlank { "Piece" },
        extraUnits = listOf(product.unit).filter { it.isNotBlank() && it !in UNIT_CHIPS },
        price = if (product.sellingPrice > 0) product.sellingPrice.money() else "",
        cost = if (product.costPrice > 0) product.costPrice.money() else "",
        barcode = product.barcode,
        sku = product.sku,
        category = product.category.ifBlank { DEFAULT_CATEGORY },
        subCategory = product.subCategory,
        tracked = product.isTracked,
        openingStock = product.currentStock.money(),
        lowStock = product.lowStockThreshold.money(),
        favourite = product.isFavourite,
        options = ProductOptions.decode(product.variants, product.sellingPrice, stockOf)
    )
}

private fun buildRequest(form: ProductForm, product: ProductEntity?): ProductSaveRequest =
    ProductSaveRequest(
        id = product?.id ?: 0L,
        name = form.name.trim(),
        sellingPrice = form.priceValue,
        costPrice = form.costValue,
        barcode = form.barcode.trim(),
        sku = form.sku.trim(),
        category = form.category.trim().ifBlank { DEFAULT_CATEGORY },
        subCategory = form.subCategory.trim(),
        unit = form.unit.trim().ifBlank { "Piece" },
        openingStock = form.stockValue,
        lowStock = form.lowStockValue,
        isTracked = form.tracked,
        isFavourite = form.favourite,
        variants = ProductOptions.encode(form.options),
        comboStock = ProductOptions.combinationStock(form.options)
    )

private fun Double.money(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

// ---------------------------------------------------------------------------
// Shared chrome
// ---------------------------------------------------------------------------

@Composable
private fun ScreenHeader(
    isEdit: Boolean,
    mode: ProductEditMode,
    onModeChange: (ProductEditMode) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isEdit) "Edit item" else "Add a new item",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Text(
                    "Both modes save the same item — pick whichever you prefer.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Two-way switch: Easy mode | Normal mode.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LightSurfaceVariant)
                .padding(4.dp)
        ) {
            ProductEditMode.entries.forEach { entry ->
                val selected = entry == mode
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onModeChange(entry) },
                    color = if (selected) BrandPrimary else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = BrandOnPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            entry.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selected) BrandOnPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int, title: String, subtitle: String) {
    Text(
        "Step $step of $TOTAL_EASY_STEPS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = BrandPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        title,
        fontSize = 19.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextPrimary,
        lineHeight = 24.sp
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(subtitle, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun WizardButtons(
    canGoBack: Boolean,
    onBack: () -> Unit,
    nextLabel: String,
    nextEnabled: Boolean,
    blockedReason: String?,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (!nextEnabled && blockedReason != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(blockedReason, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(nextLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Easy mode — one question at a time
// ---------------------------------------------------------------------------

@Composable
private fun EasyMode(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    when (form.easyStep) {
        1 -> NameAndUnitStep(
            form = form,
            onChange = onChange,
            onNext = { onChange(form.copy(easyStep = 2)) },
            onDismiss = onDismiss
        )

        2 -> FirstOptionsStep(
            form = form,
            onChange = onChange,
            onBack = { onChange(form.copy(easyStep = 1)) },
            onNext = { step -> onChange(form.copy(easyStep = step)) }
        )

        3 -> SplitAgainStep(
            form = form,
            onChange = onChange,
            onBack = { onChange(form.copy(easyStep = 2)) },
            onNext = { onChange(form.copy(easyStep = 4)) }
        )

        else -> ReviewStep(
            form = form,
            onChange = onChange,
            onBack = { onChange(form.copy(easyStep = 3)) },
            onSave = onSave
        )
    }
}

/**
 * A number field that mirrors the value it is given.
 *
 * The combination table and the option cards can edit the same price from two
 * places, so the text has to follow the model when it changes elsewhere while
 * still letting the owner clear the box and retype without fighting it.
 */
@Composable
private fun SyncedNumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    key: Any?,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true
) {
    var text by remember(key) { mutableStateOf(if (value > 0.0) value.money() else "") }
    var mirrored by remember(key) { mutableStateOf(value) }
    if (value != mirrored) {
        text = if (value > 0.0) value.money() else ""
        mirrored = value
    }
    NumberTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it.toDoubleOrNull() ?: 0.0)
        },
        label = label,
        allowDecimal = allowDecimal,
        scrollState = scrollState,
        modifier = modifier
    )
}

@Composable
private fun NameAndUnitStep(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val scroll = rememberScrollState()
    var addingUnit by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            StepHeader(
                step = 1,
                title = "What is the item called?",
                subtitle = "Write it the way your customers ask for it."
            )

            AppTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                label = "Item name",
                placeholder = "e.g. Biryani, Trouser",
                scrollState = scroll,
                singleLine = true,
                testTag = "product_name_input"
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text("How do you sell it?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Tap one. It shows next to every price and stock figure.",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            UnitChipRow(
                units = form.unitChoices,
                selected = form.unit,
                onSelect = { onChange(form.copy(unit = it)) },
                onAddUnit = { onChange(form.copy(unit = it, extraUnits = (form.extraUnits + it).distinct())) },
                addingUnit = addingUnit,
                onAddingUnitChange = { addingUnit = it },
                scrollState = scroll
            )

            Spacer(modifier = Modifier.height(14.dp))

            HintCardText(
                text = "Selling something by weight, by the bottle or by the plate? Pick that now — " +
                    "it follows the item everywhere, including the printed bill."
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        WizardButtons(
            canGoBack = false,
            onBack = onDismiss,
            nextLabel = "Next",
            nextEnabled = form.name.trim().isNotBlank(),
            blockedReason = "Type the item name to continue",
            onNext = onNext
        )
    }
}

@Composable
private fun FirstOptionsStep(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    onBack: () -> Unit,
    onNext: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    var addingOption by remember { mutableStateOf(false) }
    val draft = form.options
    val wantsOptions = draft.hasOptions

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            StepHeader(
                step = 2,
                title = "Do you want to split this item into options?",
                subtitle = "Options are things like Regular/Full, Keeri/Basmati or Green/Black."
            )

            YesNoRow(
                value = wantsOptions,
                onYes = {
                    onChange(
                        form.updateOptions(
                            draft.copy(
                                hasOptions = true,
                                groupName = draft.groupName.ifBlank { "" }
                            )
                        )
                    )
                },
                onNo = {
                    onChange(form.updateOptions(ProductOptionsDraft(hasOptions = false)))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            NumberTextField(
                value = form.price,
                onValueChange = { onChange(form.copy(price = it)) },
                label = if (wantsOptions) "Starting price (Rs.)" else "Selling price (Rs.)",
                placeholder = "0",
                scrollState = scroll,
                helper = if (wantsOptions) {
                    "Every option starts at this price. You can raise or lower each one below."
                } else {
                    null
                },
                testTag = "product_price_input"
            )

            if (!wantsOptions) {
                Spacer(modifier = Modifier.height(12.dp))
                NumberTextField(
                    value = form.openingStock,
                    onValueChange = { onChange(form.copy(openingStock = it)) },
                    label = "How many do you have now? (${form.unit})",
                    placeholder = "0",
                    allowDecimal = false,
                    scrollState = scroll,
                    helper = "Leave empty if you do not want to count this item.",
                    testTag = "product_stock_input"
                )
            }

            if (wantsOptions) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "What do you call this set of options?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProductOptions.GROUP_NAME_SUGGESTIONS.forEach { suggestion ->
                        SuggestionChip(
                            label = suggestion,
                            selected = draft.groupName.equals(suggestion, ignoreCase = true),
                            onClick = {
                                onChange(form.updateOptions(draft.copy(groupName = suggestion)))
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = draft.groupName,
                    onValueChange = { onChange(form.updateOptions(draft.copy(groupName = it))) },
                    label = "Name of this set",
                    placeholder = "e.g. Portion",
                    scrollState = scroll,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text("Add the options", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Tap + Add option for each one, then set its price.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // The options themselves are chips, so removing one is a tap on
                // the little cross instead of hunting for a delete button.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    draft.options.forEachIndexed { index, option ->
                        RemovableChip(
                            label = option.name.ifBlank { "Option ${index + 1}" },
                            onRemove = {
                                onChange(
                                    form.updateOptions(
                                        draft.copy(options = draft.options.removed(index))
                                    )
                                )
                            }
                        )
                    }
                    AddChip(
                        label = "Add option",
                        active = addingOption,
                        onClick = { addingOption = !addingOption }
                    )
                }

                if (addingOption) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AddNameRow(
                        label = "New option",
                        placeholder = "e.g. Regular",
                        buttonText = "Add option",
                        scrollState = scroll,
                        onAdd = { name ->
                            onChange(
                                form.updateOptions(
                                    draft.copy(
                                        options = draft.options + ProductOptionDraft(
                                            name = name,
                                            // Pre-filled so the owner only
                                            // changes the difference, not the
                                            // whole price.
                                            price = form.priceValue
                                        )
                                    )
                                )
                            )
                            addingOption = false
                        }
                    )
                }

                if (draft.options.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LightSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No options yet. Add at least two so your customers can choose.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                draft.options.forEachIndexed { index, option ->
                    Spacer(modifier = Modifier.height(10.dp))
                    OptionPriceCard(
                        title = option.name.ifBlank { "Option ${index + 1}" },
                        groupName = draft.groupName.ifBlank { "Option" },
                        price = option.price,
                        unit = form.unit,
                        onPriceChange = { price ->
                            onChange(form.updateOptions(draft.replaceOption(index, option.copy(price = price))))
                        },
                        onRemove = {
                            onChange(form.updateOptions(draft.copy(options = draft.options.removed(index))))
                        },
                        onRename = { name ->
                            onChange(form.updateOptions(draft.replaceOption(index, option.copy(name = name))))
                        },
                        scrollState = scroll
                    )
                }

            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        val ready = form.priceValue > 0.0 && if (wantsOptions) {
            draft.groupName.isNotBlank() && draft.options.count { it.name.isNotBlank() } >= 1
        } else {
            true
        }

        WizardButtons(
            canGoBack = true,
            onBack = onBack,
            nextLabel = if (wantsOptions) "Next" else "Review",
            nextEnabled = ready,
            blockedReason = when {
                form.priceValue <= 0.0 -> "Enter a selling price to continue"
                wantsOptions && draft.groupName.isBlank() -> "Name the set of options"
                wantsOptions && draft.options.isEmpty() -> "Add at least one option"
                else -> null
            },
            // No options means there is nothing to split again: go straight to
            // the review screen.
            onNext = { onNext(if (wantsOptions) 3 else 4) }
        )
    }
}

@Composable
private fun SplitAgainStep(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val scroll = rememberScrollState()
    val draft = form.options
    val split = draft.isSplit

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            StepHeader(
                step = 3,
                title = "Do you want to split these again?",
                subtitle = "For example by size: Green comes in 32/34/36/40, Black only in L and XL."
            )

            YesNoRow(
                value = split,
                onYes = {
                    onChange(form.updateOptions(draft.ensureSplit(form.priceValue)))
                },
                onNo = {
                    onChange(form.updateOptions(draft.copy(options = draft.options.map { it.copy(subOptions = emptyList()) })))
                }
            )

            if (split) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "What do you call this second set?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProductOptions.SUB_GROUP_NAME_SUGGESTIONS.forEach { suggestion ->
                        SuggestionChip(
                            label = suggestion,
                            selected = draft.subGroupName.equals(suggestion, ignoreCase = true),
                            onClick = {
                                onChange(form.updateOptions(draft.copy(subGroupName = suggestion)))
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = draft.subGroupName,
                    onValueChange = { onChange(form.updateOptions(draft.copy(subGroupName = it))) },
                    label = "Name of the second set",
                    placeholder = "e.g. Size",
                    scrollState = scroll,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                draft.options.forEachIndexed { index, option ->
                    SubOptionEditor(
                        option = option,
                        unit = form.unit,
                        onRename = { name ->
                            onChange(form.updateOptions(draft.replaceOption(index, option.copy(name = name))))
                        },
                        onAddSubOption = { name ->
                            val updated = option.copy(
                                subOptions = option.subOptions + ProductSubOptionDraft(
                                    name = name,
                                    price = if (option.price > 0.0) option.price else form.priceValue
                                )
                            )
                            onChange(form.updateOptions(draft.replaceOption(index, updated)))
                        },
                        onRemoveSubOption = { subIndex ->
                            val updated = option.copy(subOptions = option.subOptions.removed(subIndex))
                            onChange(form.updateOptions(draft.replaceOption(index, updated)))
                        },
                        scrollState = scroll
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                "Set the price and the count you have",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Every line below becomes its own stock line, so selling one never touches another.",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            CombinationTable(
                form = form,
                onChange = onChange,
                scrollState = scroll
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        WizardButtons(
            canGoBack = true,
            onBack = onBack,
            nextLabel = "Review",
            nextEnabled = true,
            blockedReason = null,
            onNext = onNext
        )
    }
}

@Composable
private fun ReviewStep(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val scroll = rememberScrollState()
    val combos = form.combinations()
    val totalStock = combos.sumOf { combo -> form.stockOf(combo.labels) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            StepHeader(
                step = 4,
                title = "Check it, then save",
                subtitle = "This is exactly how the item will look when you sell it."
            )

            // Live preview, drawn like the selling screen's product card.
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                form.name.trim().ifBlank { "New item" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                listOf(form.category, form.subCategory)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" > ")
                                    .ifBlank { DEFAULT_CATEGORY },
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        if (form.favourite) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Pinned",
                                tint = StatusAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (combos.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                CurrencyUtils.formatLkr(form.priceValue) + " / " + form.unit,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = BrandPrimary
                            )
                            if (form.tracked) {
                                Text(
                                    "${form.stockValue.toInt()} ${form.unit} on hand",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (form.stockValue <= 0.0) StatusRed else StatusGreen
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "Tap to choose: " + combos.size + " options",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        combos.forEach { combo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        combo.displayName.replace("/", " · "),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (form.tracked) {
                                            "${form.stockOf(combo.labels).toInt()} ${form.unit} on hand"
                                        } else {
                                            "Not counted"
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    CurrencyUtils.formatLkr(combo.price),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = BrandPrimary
                                )
                            }
                            HorizontalDivider(color = LightBorder)
                        }

                        if (form.tracked) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Total on hand: ${totalStock.toInt()} ${form.unit}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    if (form.barcode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Barcode ${form.barcode}", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HintCardText(
                text = "Everything here can still be changed later from the Items tab — " +
                    "nothing is locked in."
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        WizardButtons(
            canGoBack = true,
            onBack = onBack,
            nextLabel = "Save item",
            nextEnabled = form.name.trim().isNotBlank(),
            blockedReason = "Type the item name before saving",
            onNext = onSave
        )
    }
}

// ---------------------------------------------------------------------------
// Normal mode — one dense form
// ---------------------------------------------------------------------------

@Composable
private fun NormalMode(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    categoryOptions: List<String>,
    subCategoryOptions: Map<String, List<String>>,
    scrollState: ScrollState,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val split = form.options.isSplit
    val profit = (form.priceValue - form.costValue).coerceAtLeast(0.0)
    val margin = if (form.priceValue > 0) ((profit / form.priceValue) * 100).toInt() else 0

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormCard(title = "Basics", icon = Icons.Default.ListAlt) {
                AppTextField(
                    value = form.name,
                    onValueChange = { onChange(form.copy(name = it)) },
                    label = "Item name",
                    placeholder = "e.g. Biryani, Trouser",
                    scrollState = scrollState,
                    singleLine = true,
                    testTag = "product_name_input"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(
                        value = form.category,
                        options = categoryOptions,
                        onValueChange = { onChange(form.copy(category = it)) },
                        label = "Category",
                        modifier = Modifier.weight(1.2f),
                        placeholder = "General"
                    )
                    DropdownField(
                        value = form.subCategory,
                        // Only the sub-categories that belong to the category
                        // above, plus "add new" for anything else.
                        options = subCategoryOptions[form.category].orEmpty(),
                        onValueChange = { onChange(form.copy(subCategory = it)) },
                        label = "Sub-category",
                        modifier = Modifier.weight(1f),
                        placeholder = "Optional"
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Unit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                UnitChipRow(
                    units = form.unitChoices,
                    selected = form.unit,
                    onSelect = { onChange(form.copy(unit = it)) },
                    onAddUnit = { onChange(form.copy(unit = it, extraUnits = (form.extraUnits + it).distinct())) },
                    addingUnit = false,
                    onAddingUnitChange = {},
                    scrollState = scrollState
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = form.barcode,
                    onValueChange = { onChange(form.copy(barcode = it)) },
                    label = "Barcode",
                    placeholder = "Scan or leave blank",
                    scrollState = scrollState,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            onChange(form.copy(barcode = "890" + (10000000..99999999).random().toString()))
                        }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Generate a barcode", tint = BrandPrimary)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = form.sku,
                    onValueChange = { onChange(form.copy(sku = it)) },
                    label = "SKU / shop code",
                    placeholder = "Optional",
                    scrollState = scrollState,
                    singleLine = true
                )
            }

            FormCard(title = "Pricing", icon = Icons.Default.Sell) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberTextField(
                        value = form.price,
                        onValueChange = { onChange(form.copy(price = it)) },
                        label = "Selling price (Rs.)",
                        scrollState = scrollState,
                        modifier = Modifier.weight(1f)
                    )
                    NumberTextField(
                        value = form.cost,
                        onValueChange = { onChange(form.copy(cost = it)) },
                        label = "Cost price (Rs.)",
                        scrollState = scrollState,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (form.priceValue > 0 && form.costValue > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Profit ${CurrencyUtils.formatLkr(profit)} / ${form.unit}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                            Text(
                                "Margin $margin%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimaryDark
                            )
                        }
                    }
                }
            }

            FormCard(title = "Options & sizes", icon = Icons.Default.Tune) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("This item has options", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "e.g. Regular/Full, Keeri/Basmati, Green/Black",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = form.options.hasOptions,
                        onCheckedChange = { on ->
                            onChange(
                                form.updateOptions(
                                    if (on) {
                                        form.options.copy(hasOptions = true)
                                    } else {
                                        ProductOptionsDraft(hasOptions = false)
                                    }
                                )
                            )
                        }
                    )
                }

                if (form.options.hasOptions) {
                    val draft = form.options
                    Spacer(modifier = Modifier.height(12.dp))
                    AppTextField(
                        value = draft.groupName,
                        onValueChange = { onChange(form.updateOptions(draft.copy(groupName = it))) },
                        label = "Name of the first set",
                        placeholder = "e.g. Portion",
                        scrollState = scrollState,
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProductOptions.GROUP_NAME_SUGGESTIONS.forEach { suggestion ->
                            SuggestionChip(
                                label = suggestion,
                                selected = draft.groupName.equals(suggestion, ignoreCase = true),
                                onClick = { onChange(form.updateOptions(draft.copy(groupName = suggestion))) }
                            )
                        }
                    }

                    draft.options.forEachIndexed { index, option ->
                        Spacer(modifier = Modifier.height(12.dp))
                        NormalOptionRow(
                            option = option,
                            index = index,
                            unit = form.unit,
                            groupName = draft.groupName.ifBlank { "Option" },
                            scrollState = scrollState,
                            onRename = { onChange(form.updateOptions(draft.replaceOption(index, option.copy(name = it)))) },
                            onPriceChange = { onChange(form.updateOptions(draft.replaceOption(index, option.copy(price = it)))) },
                            onStockChange = { onChange(form.updateOptions(draft.replaceOption(index, option.copy(stock = it)))) },
                            onRemove = { onChange(form.updateOptions(draft.copy(options = draft.options.removed(index)))) },
                            onAddSubOption = { name ->
                                onChange(
                                    form.updateOptions(
                                        draft.replaceOption(
                                            index,
                                            option.copy(
                                                subOptions = option.subOptions + ProductSubOptionDraft(
                                                    name = name,
                                                    price = if (option.price > 0.0) option.price else form.priceValue
                                                )
                                            )
                                        )
                                    )
                                )
                            },
                            onRemoveSubOption = { subIndex ->
                                onChange(
                                    form.updateOptions(
                                        draft.replaceOption(
                                            index,
                                            option.copy(subOptions = option.subOptions.removed(subIndex))
                                        )
                                    )
                                )
                            },
                            onChangeSubPrice = { subIndex, price ->
                                onChange(
                                    form.updateOptions(
                                        draft.replaceOption(
                                            index,
                                            option.copy(
                                                subOptions = option.subOptions.replaced(subIndex) {
                                                    it.copy(price = price)
                                                }
                                            )
                                        )
                                    )
                                )
                            },
                            onChangeSubStock = { subIndex, stock ->
                                onChange(
                                    form.updateOptions(
                                        draft.replaceOption(
                                            index,
                                            option.copy(
                                                subOptions = option.subOptions.replaced(subIndex) {
                                                    it.copy(stock = stock)
                                                }
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    AddNameRow(
                        label = "New option",
                        placeholder = "e.g. Regular",
                        buttonText = "Add option",
                        scrollState = scrollState,
                        onAdd = { name ->
                            onChange(
                                form.updateOptions(
                                    draft.copy(
                                        options = draft.options + ProductOptionDraft(
                                            name = name,
                                            price = form.priceValue
                                        )
                                    )
                                )
                            )
                        }
                    )

                    if (split) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppTextField(
                            value = draft.subGroupName,
                            onValueChange = { onChange(form.updateOptions(draft.copy(subGroupName = it))) },
                            label = "Name of the second set",
                            placeholder = "e.g. Size",
                            scrollState = scrollState,
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Every combination",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CombinationTable(form = form, onChange = onChange, scrollState = scrollState)
                }
            }

            FormCard(title = "Stock", icon = Icons.Default.Inventory2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Count this item", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Stock drops automatically on every bill", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = form.tracked,
                        onCheckedChange = { onChange(form.copy(tracked = it)) }
                    )
                }
                if (form.tracked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberTextField(
                            value = form.openingStock,
                            onValueChange = { onChange(form.copy(openingStock = it)) },
                            label = if (form.options.hasOptions) "Opening stock (parent)" else "Opening stock",
                            allowDecimal = false,
                            scrollState = scrollState,
                            modifier = Modifier.weight(1f),
                            helper = if (form.options.hasOptions) {
                                "Each combination has its own count above."
                            } else {
                                null
                            }
                        )
                        NumberTextField(
                            value = form.lowStock,
                            onValueChange = { onChange(form.copy(lowStock = it)) },
                            label = "Warn me below",
                            allowDecimal = false,
                            scrollState = scrollState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            FormCard(title = "Extras", icon = Icons.Default.Star) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pin to favourites", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Shows first on the selling screen", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = form.favourite,
                        onCheckedChange = { onChange(form.copy(favourite = it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Sticky save bar.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = form.name.trim().isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save item", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove this item", color = StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
private fun FormCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun HintCardText(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, fontSize = 11.sp, color = BrandPrimaryDark, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun YesNoRow(
    value: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BigChoiceButton(
            label = "Yes",
            detail = "Split it into options",
            selected = value,
            onClick = onYes,
            modifier = Modifier.weight(1f)
        )
        BigChoiceButton(
            label = "No",
            detail = "Sell it as one item",
            selected = !value,
            onClick = onNo,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BigChoiceButton(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) BrandPrimary else TextSecondary
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) BrandSurface else LightSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) BrandPrimary else LightBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = tint)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                detail,
                fontSize = 10.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) BrandPrimary else LightSurface,
        border = BorderStroke(1.dp, if (selected) BrandPrimary else LightBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) BrandOnPrimary else TextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun RemovableChip(
    label: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BrandSurface,
        border = BorderStroke(1.dp, BrandPrimary)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandPrimaryDark
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $label",
                tint = BrandPrimary,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}

@Composable
private fun AddChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) BrandSurface else LightSurface,
        border = BorderStroke(1.dp, if (active) BrandPrimary else LightBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (active) Icons.Default.ExpandLess else Icons.Default.Add,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandPrimary
            )
        }
    }
}

@Composable
private fun UnitChipRow(
    units: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAddUnit: (String) -> Unit,
    addingUnit: Boolean,
    onAddingUnitChange: (Boolean) -> Unit,
    scrollState: ScrollState
) {
    Column {
        // Chips wrap onto as many rows as they need.
        val rows = units.chunked(4)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { unit ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (unit.equals(selected, ignoreCase = true)) BrandPrimary else LightSurfaceVariant,
                        border = if (unit.equals(selected, ignoreCase = true)) {
                            null
                        } else {
                            BorderStroke(1.dp, LightBorder)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(unit) }
                    ) {
                        Text(
                            unit,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (unit.equals(selected, ignoreCase = true)) BrandOnPrimary else TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp)
                        )
                    }
                }
                if (row.size < 4) {
                    repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (addingUnit) BrandSurface else LightSurfaceVariant,
                border = BorderStroke(1.dp, if (addingUnit) BrandPrimary else LightBorder),
                modifier = Modifier.clickable { onAddingUnitChange(!addingUnit) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (addingUnit) Icons.Default.ExpandLess else Icons.Default.Add,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Add",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandPrimary
                    )
                }
            }
        }

        if (addingUnit) {
            var text by remember { mutableStateOf("") }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Your own unit",
                    placeholder = "e.g. Plate",
                    scrollState = scrollState,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            onAddUnit(text.trim())
                            text = ""
                            onAddingUnitChange(false)
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun AddNameRow(
    label: String,
    placeholder: String,
    buttonText: String,
    scrollState: ScrollState,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = label,
            placeholder = placeholder,
            scrollState = scrollState,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.height(52.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(buttonText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OptionPriceCard(
    title: String,
    groupName: String,
    price: Double,
    unit: String,
    onPriceChange: (Double) -> Unit,
    onRemove: () -> Unit,
    onRename: (String) -> Unit,
    scrollState: ScrollState
) {
    var renaming by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$groupName: $title",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("Price per $unit", fontSize = 10.sp, color = TextSecondary)
                }
                IconButton(onClick = { renaming = !renaming }) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Rename",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove option",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (renaming) {
                AppTextField(
                    value = title,
                    onValueChange = onRename,
                    label = "Option name",
                    scrollState = scrollState,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            SyncedNumberField(
                value = price,
                onValueChange = onPriceChange,
                label = "Selling price (Rs.)",
                key = title,
                scrollState = scrollState
            )
        }
    }
}

@Composable
private fun SubOptionEditor(
    option: ProductOptionDraft,
    unit: String,
    onRename: (String) -> Unit,
    onAddSubOption: (String) -> Unit,
    onRemoveSubOption: (Int) -> Unit,
    scrollState: ScrollState
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ListAlt,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    option.name.ifBlank { "Option" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${option.subOptions.size} size(s)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            AppTextField(
                value = option.name,
                onValueChange = onRename,
                label = "Option name",
                scrollState = scrollState,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (option.subOptions.isEmpty()) {
                Text(
                    "No sizes yet. Add the ones this option actually comes in.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                option.subOptions.withIndex().chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { (index, sub) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = LightSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        sub.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onRemoveSubOption(index) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove ${sub.name}",
                                            tint = StatusRed,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            AddNameRow(
                label = "New size for ${option.name.ifBlank { "this option" }}",
                placeholder = "e.g. 32",
                buttonText = "Add",
                scrollState = scrollState,
                onAdd = onAddSubOption
            )
            Text(
                "Prices and counts for every combination are set just below, per $unit.",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun NormalOptionRow(
    option: ProductOptionDraft,
    index: Int,
    unit: String,
    groupName: String,
    scrollState: ScrollState,
    onRename: (String) -> Unit,
    onPriceChange: (Double) -> Unit,
    onStockChange: (Double) -> Unit,
    onRemove: () -> Unit,
    onAddSubOption: (String) -> Unit,
    onRemoveSubOption: (Int) -> Unit,
    onChangeSubPrice: (Int, Double) -> Unit,
    onChangeSubStock: (Int, Double) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$groupName ${index + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.Add,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (expanded) "Hide sizes" else "Sub-options (${option.subOptions.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimary
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove option",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AppTextField(
                value = option.name,
                onValueChange = onRename,
                label = "Option name",
                scrollState = scrollState,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncedNumberField(
                    value = option.price,
                    onValueChange = onPriceChange,
                    label = "Price (Rs.)",
                    key = "opt-$index-price",
                    scrollState = scrollState,
                    modifier = Modifier.weight(1f)
                )
                if (option.subOptions.isEmpty()) {
                    SyncedNumberField(
                        value = option.stock,
                        onValueChange = onStockChange,
                        label = "Stock ($unit)",
                        key = "opt-$index-stock",
                        allowDecimal = false,
                        scrollState = scrollState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = LightBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sizes for ${option.name.ifBlank { "this option" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                option.subOptions.forEachIndexed { subIndex, sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            sub.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(0.8f)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            SyncedNumberField(
                                value = sub.price,
                                onValueChange = { price -> onChangeSubPrice(subIndex, price) },
                                label = "Price",
                                key = "sub-$index-$subIndex-price",
                                scrollState = scrollState
                            )
                        }
                        Box(modifier = Modifier.weight(0.9f)) {
                            SyncedNumberField(
                                value = sub.stock,
                                onValueChange = { stock -> onChangeSubStock(subIndex, stock) },
                                label = "Stock",
                                key = "sub-$index-$subIndex-stock",
                                allowDecimal = false,
                                scrollState = scrollState
                            )
                        }
                        IconButton(
                            onClick = { onRemoveSubOption(subIndex) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove ${sub.name}",
                                tint = StatusRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                AddNameRow(
                    label = "New size",
                    placeholder = "e.g. 32",
                    buttonText = "Add",
                    scrollState = scrollState,
                    onAdd = onAddSubOption
                )
            }
        }
    }
}

/**
 * The live table of final combinations: one row each with its price and the
 * count on the shelf. Used by both modes so they can never disagree.
 */
@Composable
private fun CombinationTable(
    form: ProductForm,
    onChange: (ProductForm) -> Unit,
    scrollState: ScrollState
) {
    val combos = form.combinations()
    if (combos.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = LightSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Add an option first — every combination of your options shows up here.",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(12.dp)
            )
        }
        return
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Combination",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    "Price (Rs.)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.weight(0.9f)
                )
                Text(
                    "Stock (${form.unit})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.weight(0.9f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            combos.forEach { combo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        combo.displayName.replace("/", " · "),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.1f)
                    )
                    Box(modifier = Modifier.weight(0.9f)) {
                        SyncedNumberField(
                            value = combo.price,
                            onValueChange = { price ->
                                onChange(form.updateComboPrice(combo.labels, price))
                            },
                            label = "Price",
                            key = "combo-${combo.displayName}-price",
                            scrollState = scrollState
                        )
                    }
                    Box(modifier = Modifier.weight(0.9f)) {
                        SyncedNumberField(
                            value = form.stockOf(combo.labels),
                            onValueChange = { stock ->
                                onChange(form.updateComboStock(combo.labels, stock))
                            },
                            label = "Stock",
                            key = "combo-${combo.displayName}-stock",
                            allowDecimal = false,
                            scrollState = scrollState
                        )
                    }
                }
            }

            val total = combos.sumOf { form.stockOf(it.labels) }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${combos.size} stock lines · ${total.toInt()} ${form.unit} in total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (total > 0.0) StatusGreen else StatusAmber
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pure helpers on the draft, so the screens stay readable
// ---------------------------------------------------------------------------

private fun <T> List<T>.removed(index: Int): List<T> =
    filterIndexed { i, _ -> i != index }

private fun <T> List<T>.replaced(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, value -> if (i == index) transform(value) else value }

private fun ProductOptionsDraft.replaceOption(index: Int, option: ProductOptionDraft): ProductOptionsDraft =
    copy(options = options.mapIndexed { i, existing -> if (i == index) option else existing })

/** Turns "split again" on, seeding sizes from the base price. */
private fun ProductOptionsDraft.ensureSplit(basePrice: Double): ProductOptionsDraft {
    if (isSplit) return this
    return copy(
        subGroupName = subGroupName.ifBlank { "Size" },
        options = options.map { option ->
            if (option.subOptions.isNotEmpty()) option else {
                option.copy(
                    subOptions = listOf(
                        ProductSubOptionDraft(
                            name = "Regular",
                            price = if (option.price > 0.0) option.price else basePrice
                        )
                    )
                )
            }
        }
    )
}

private fun ProductForm.stockOf(labels: List<String>): Double {
    val first = labels.firstOrNull() ?: return 0.0
    val option = options.options.firstOrNull { it.name.equals(first, ignoreCase = true) } ?: return 0.0
    if (labels.size == 1) return option.stock
    val rest = labels.drop(1).joinToString("/")
    return option.subOptions.firstOrNull { it.name.equals(rest, ignoreCase = true) }?.stock ?: 0.0
}

private fun ProductForm.updateComboPrice(labels: List<String>, price: Double): ProductForm {
    val draft = options
    val updated = draft.copy(
        options = draft.options.map { option ->
            if (!option.name.equals(labels.firstOrNull().orEmpty(), ignoreCase = true)) return@map option
            if (labels.size == 1 || option.subOptions.isEmpty()) {
                option.copy(price = price)
            } else {
                val rest = labels.drop(1).joinToString("/")
                option.copy(
                    subOptions = option.subOptions.map { sub ->
                        if (sub.name.equals(rest, ignoreCase = true)) sub.copy(price = price) else sub
                    }
                )
            }
        }
    )
    return updateOptions(updated)
}

private fun ProductForm.updateComboStock(labels: List<String>, stock: Double): ProductForm {
    val draft = options
    val updated = draft.copy(
        options = draft.options.map { option ->
            if (!option.name.equals(labels.firstOrNull().orEmpty(), ignoreCase = true)) return@map option
            if (labels.size == 1 || option.subOptions.isEmpty()) {
                option.copy(stock = stock)
            } else {
                val rest = labels.drop(1).joinToString("/")
                option.copy(
                    subOptions = option.subOptions.map { sub ->
                        if (sub.name.equals(rest, ignoreCase = true)) sub.copy(stock = stock) else sub
                    }
                )
            }
        }
    )
    return updateOptions(updated)
}
