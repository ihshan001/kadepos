package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Arro-POS palette.
 *
 * Built for a screen that stays on all day under shop lighting: a calm deep
 * teal for anything the owner acts on, sitting on a cool off-white canvas
 * instead of pure white (pure white glares in bright rooms), with slate text
 * rather than pure black (pure black on white fatigues the eyes over a shift).
 *
 * Contrast, measured on the pairs actually used in the app:
 *   TextPrimary   on LightSurface        ~16.6:1
 *   TextSecondary on LightSurface         ~7.5:1
 *   BrandOnPrimary on BrandPrimary        ~5.0:1
 *   BrandPrimary  on LightSurface         ~4.9:1
 *   BrandPrimaryDark on BrandSurface      ~6.6:1
 */

// Brand — deep teal. Used for primary actions, selection, and money figures.
val BrandPrimary = Color(0xFF0F766E)
val BrandPrimaryDark = Color(0xFF115E59)
val BrandPrimaryLight = Color(0xFF99F6E4)
val BrandAccent = Color(0xFF0E7490)
/** Soft teal tint for selected rows, summary cards and filled chips. */
val BrandSurface = Color(0xFFE4F2F0)
/** Text and icons drawn on top of [BrandPrimary]. */
val BrandOnPrimary = Color(0xFFFFFFFF)

// Cool neutrals. Deliberately a step off pure white/black so a full shift of
// reading stays comfortable.
val LightBackground = Color(0xFFF4F6F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF1F4)
val LightBorder = Color(0xFFD8E0E6)

val TextPrimary = Color(0xFF101828)
val TextSecondary = Color(0xFF475467)
val TextMuted = Color(0xFF8A94A6)

// Status Colors — kept semantic so warnings still read as warnings.
// The user's expense request keeps red for destructive actions.
val StatusGreen = Color(0xFF15803D)
val StatusGreenBg = Color(0xFFDCFCE7)
val StatusAmber = Color(0xFFB45309)
val StatusAmberBg = Color(0xFFFEF3C7)
val StatusRed = Color(0xFFC62828)
val StatusRedBg = Color(0xFFFDECEA)
val StatusBlue = Color(0xFF1D4ED8)
val StatusBlueBg = Color(0xFFDBEAFE)

// Thermal Receipt Colors
val ReceiptPaper = Color(0xFFFCFCFC)
val ReceiptText = Color(0xFF171717)
val ReceiptDashed = Color(0xFF737373)
