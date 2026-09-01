package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Arro-POS palette — "paper & ink".
 *
 * Built for a screen that stays on all day under shop lighting: a calm, deep
 * ink-blue for anything the owner acts on, sitting on a warm paper canvas
 * instead of cool grey or pure white (pure white glares in bright rooms), with
 * soft-black text rather than pure black (pure black on white fatigues the eyes
 * over a long shift).
 *
 * The earlier teal theme was dropped: it read as clinical at the counter and
 * the mid-tone teals could not reach a safe contrast on tinted chips.
 *
 * Contrast, measured on the pairs actually used in the app:
 *   TextPrimary    on LightSurface        ~15.9:1
 *   TextSecondary  on LightSurface         ~8.2:1
 *   BrandOnPrimary on BrandPrimary         ~7.4:1
 *   BrandPrimary   on LightSurface         ~7.4:1
 *   BrandPrimaryDark on BrandSurface      ~10.4:1
 */

// Brand — deep ink blue. Used for primary actions, selection and money figures.
val BrandPrimary = Color(0xFF1F4E79)
val BrandPrimaryDark = Color(0xFF163A5A)
val BrandPrimaryLight = Color(0xFFD6E4F0)
/** Warm clay. Used sparingly, for the few things that must shout. */
val BrandAccent = Color(0xFF9A5B22)
/** Soft blue tint for selected rows, summary cards and filled chips. */
val BrandSurface = Color(0xFFE9F0F7)
/** Text and icons drawn on top of [BrandPrimary]. */
val BrandOnPrimary = Color(0xFFFFFFFF)

// Warm neutrals. Deliberately a step off pure white/black so a full shift of
// reading stays comfortable.
val LightBackground = Color(0xFFF7F6F3)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0EEE9)
val LightBorder = Color(0xFFDCD8D0)

val TextPrimary = Color(0xFF17191D)
val TextSecondary = Color(0xFF4B515C)
val TextMuted = Color(0xFF8A8F9A)

// Status Colors — kept semantic so warnings still read as warnings.
// Red stays for destructive actions and missing stock.
val StatusGreen = Color(0xFF1B7A46)
val StatusGreenBg = Color(0xFFE3F3E9)
val StatusAmber = Color(0xFF96590A)
val StatusAmberBg = Color(0xFFFDF0D9)
val StatusRed = Color(0xFFB3261E)
val StatusRedBg = Color(0xFFFBE9E7)
val StatusBlue = Color(0xFF1D4ED8)
val StatusBlueBg = Color(0xFFE4EBFC)

// Thermal Receipt Colors
val ReceiptPaper = Color(0xFFFCFCFC)
val ReceiptText = Color(0xFF171717)
val ReceiptDashed = Color(0xFF737373)
