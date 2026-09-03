package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSurface
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.Country
import com.example.ui.util.CountryCodes
import com.example.ui.util.PhoneValidator

/**
 * A phone number box that knows which country it is in.
 *
 * The country is picked from a searchable list of every country, so the owner
 * never types a dial code, and the code is then shown in front of the number so
 * what they type is only ever the local part.
 */
@Composable
fun PhoneField(
    country: Country,
    localNumber: String,
    onCountryChange: (Country) -> Unit,
    onNumberChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    label: String = "Phone number",
    placeholder: String = "777777700",
    scrollState: ScrollState? = null,
    testTag: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }

    // The number is checked against the country rules here as the user types,
    // not just when the form is submitted, so the box can turn green the
    // moment the length is right and show how many digits are still missing
    // before then. This matters because the Continue button stays disabled
    // until the number is complete — without live feedback there is nothing
    // to explain why.
    val digits = localNumber.filter { it.isDigit() }
    val exact = country.exactLocalLength
    val liveError = PhoneValidator.errorFor(country, localNumber)
    val valid = liveError == null && localNumber.isNotBlank()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The country button: pick from a list, never type a code.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandSurface,
                border = BorderStroke(1.dp, LightBorder),
                modifier = Modifier.clickable { showPicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        country.dialCode,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimary
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Choose country",
                        tint = BrandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AppTextField(
                value = localNumber,
                // Keep the box clean: digits only, no spaces or dashes to
                // trip the length rules.
                onValueChange = { onNumberChange(it.filter { char -> char.isDigit() }) },
                label = label,
                placeholder = placeholder,
                error = error,
                helper = when {
                    error != null -> null
                    valid -> null
                    exact != null && digits.isNotEmpty() && digits.length < exact ->
                        "${country.name} numbers have $exact digits — you've typed ${digits.length}"
                    exact != null -> "${country.name} numbers have $exact digits"
                    else -> "Type the number without the first 0"
                },
                trailingIcon = if (valid) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = "Valid number", tint = StatusGreen) }
                } else {
                    null
                },
                keyboardType = KeyboardType.Phone,
                scrollState = scrollState,
                singleLine = true,
                modifier = Modifier.weight(1f),
                testTag = testTag
            )
        }

        if (valid) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    "Correct — saved as ${CountryCodes.join(country, localNumber)}",
                    fontSize = 12.sp,
                    color = StatusGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showPicker) {
        CountryPickerDialog(
            selected = country,
            onSelect = {
                onCountryChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun CountryPickerDialog(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) {
        val wanted = query.trim()
        if (wanted.isBlank()) {
            CountryCodes.all
        } else {
            CountryCodes.all.filter {
                it.name.contains(wanted, ignoreCase = true) ||
                    it.dialCode.contains(wanted) ||
                    it.code.equals(wanted, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "Choose your country",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Search by name or dial code.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search countries",
                    placeholder = "e.g. Sri Lanka or 94",
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(matches, key = { it.code }) { country ->
                        val isSelected = country.code == selected.code
                        Surface(
                            color = if (isSelected) BrandSurface else LightSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        country.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = TextPrimary
                                    )
                                    Text(
                                        country.dialCode,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Chosen",
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = LightBorder)
                    }
                    if (matches.isEmpty()) {
                        item {
                            Text(
                                "No country matches that.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
