package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private const val ADD_NEW = "＋ Add new…"

/**
 * A tap-to-open dropdown that replaces free-text fields where the value almost
 * always comes from a known list (category, sub-category, unit) but must still
 * allow the shop to invent something new.
 *
 * When [allowCustom] is true the menu carries a "＋ Add new…" row that swaps the
 * whole control for a plain text field with a Done button, so a brand-new value
 * is only ever a couple of taps away.
 */
@Composable
fun DropdownField(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowCustom: Boolean = true,
    placeholder: String = "Choose…"
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var customMode by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
        )

        if (customMode) {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                placeholder = { Text("Type a name…", color = TextMuted) },
                trailingIcon = {
                    IconButton(onClick = {
                        onValueChange(customText.trim())
                        customMode = false
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Done", tint = BrandPrimary)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = LightBorder,
                    cursorColor = BrandPrimary
                ),
                singleLine = true
            )
        } else {
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = LightSurface,
                    border = BorderStroke(1.dp, LightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = value.ifBlank { placeholder },
                            fontSize = 13.sp,
                            fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (value.isBlank()) TextMuted else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Open $label options",
                            tint = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option,
                                        fontSize = 13.sp,
                                        fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                                        color = TextPrimary
                                    )
                                },
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }

                    if (allowCustom) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = BrandPrimary, modifier = Modifier.width(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(ADD_NEW, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BrandPrimary)
                                }
                            },
                            onClick = {
                                expanded = false
                                customText = ""
                                customMode = true
                            }
                        )
                    }
                }
            }
        }
    }
}
