package com.example.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Shared text fields.
 *
 * Two problems these solve once, for every screen that asks the shopkeeper to
 * type something:
 *
 *  1. **The keyboard must never cover the field.** The activity resizes
 *     (`adjustResize`), and [FocusRiser] scrolls the focused field to the
 *     middle of what is left of the screen once the keyboard has settled.
 *  2. **Secrets must be checkable.** Every password, PIN or access code gets an
 *     eye button so a four digit code typed in a hurry can be read back.
 */

/** Remembers where a field sits, without triggering recomposition. */
private class FieldPosition {
    var top: Float = 0f
    var height: Int = 0
}

/**
 * Wraps a field so it rises to the middle of the screen when it takes focus.
 *
 * [scrollState] is the state of the scrolling container around the field; pass
 * null for a field that lives on a non-scrolling screen.
 */
@Composable
fun FocusRiser(
    scrollState: ScrollState?,
    content: @Composable (Modifier) -> Unit
) {
    if (scrollState == null) {
        content(Modifier)
        return
    }
    val scope = rememberCoroutineScope()
    val position = remember { FieldPosition() }
    val running = remember { mutableStateOf<Job?>(null) }

    content(
        Modifier
            .onGloballyPositioned { coordinates ->
                position.top = coordinates.positionInRoot().y
                position.height = coordinates.size.height
            }
            .onFocusEvent { state ->
                running.value?.cancel()
                if (!state.isFocused) return@onFocusEvent
                running.value = scope.launch {
                    // Wait for the keyboard to finish resizing the window,
                    // otherwise the scroll target is computed from the old height.
                    delay(220)
                    val viewport = scrollState.viewportSize
                    if (viewport <= 0) return@launch
                    val middle = position.top + position.height / 2f - viewport / 2f
                    val target = middle.roundToInt().coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(target)
                }
            }
    )
}

/**
 * The standard field used across the app.
 *
 * Set [isSecret] for anything the shopkeeper should be able to reveal — the eye
 * button is added automatically.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    placeholder: String = "",
    error: String? = null,
    helper: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isSecret: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    var revealed by remember { mutableStateOf(false) }
    FocusRiser(scrollState = scrollState) { focusModifier ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .then(focusModifier)
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
            label = { Text(label, fontSize = 13.sp) },
            placeholder = if (placeholder.isBlank()) {
                null
            } else {
                { Text(placeholder, color = TextMuted, fontSize = 14.sp) }
            },
            leadingIcon = if (leadingIcon == null) {
                null
            } else {
                { Icon(leadingIcon, contentDescription = null, tint = BrandPrimary) }
            },
            trailingIcon = when {
                isSecret -> {
                    {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (revealed) "Hide" else "Show",
                                tint = TextSecondary
                            )
                        }
                    }
                }
                trailingIcon != null -> trailingIcon
                else -> null
            },
            visualTransformation = if (isSecret && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            isError = error != null,
            supportingText = when {
                error != null -> ({ Text(error, color = StatusRed, fontSize = 11.sp) })
                helper != null -> ({ Text(helper, color = TextSecondary, fontSize = 11.sp) })
                else -> null
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
            shape = RoundedCornerShape(12.dp),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = BrandPrimary,
                unfocusedBorderColor = LightBorder,
                focusedContainerColor = LightSurface,
                unfocusedContainerColor = LightSurface,
                errorBorderColor = StatusRed,
                cursorColor = BrandPrimary
            )
        )
    }
}

/**
 * A field that only accepts a number, used for prices and counts. Anything that
 * is not a digit, a dot or a minus is dropped, so a stray letter can never turn
 * into an invalid price.
 */
@Composable
fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    placeholder: String = "0",
    allowDecimal: Boolean = true,
    error: String? = null,
    helper: String? = null,
    testTag: String? = null
) {
    AppTextField(
        value = value,
        onValueChange = { input ->
            val allowed = input.filter { it.isDigit() || (allowDecimal && it == '.') }
            // One decimal point only: "12..5" is not a price.
            val cleaned = if (allowDecimal) {
                val first = allowed.indexOf('.')
                if (first < 0) {
                    allowed
                } else {
                    allowed.substring(0, first + 1) + allowed.substring(first + 1).replace(".", "")
                }
            } else {
                allowed
            }
            onValueChange(cleaned)
        },
        label = label,
        modifier = modifier,
        scrollState = scrollState,
        placeholder = placeholder,
        error = error,
        helper = helper,
        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        singleLine = true,
        testTag = testTag
    )
}

/**
 * Padding that keeps the bottom of a form clear of the keyboard. Use it on the
 * scrolling container of any dialog or screen that asks for a password.
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.keyboardPadding(): Modifier = this.imePadding()
