package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

// Primitives for Natural Tones Theme (Integrating User's Custom Palette)
val NaturalGreen = Color(0xFF1F7A6D)       // #1f7a6d - Rich Deep Forest Emerald
val NaturalCharcoal = Color(0xFF191C1B)    // Charcoal Primary Text
val NaturalSage = Color(0xFFA8E0D2)        // #a8e0d2 - Light Leafy Sage
val NaturalMutedOlive = Color(0xFF3BB8A0)  // #3bb8a0 - Vibrant Medical Teal
val NaturalSoftGreen = Color(0xFFE2F3EF)   // Soft light background tint of #a8e0d2
val NaturalClay = Color(0xFFEEF5F2)        // Pale clay off-white background card
val NaturalLinen = Color(0xFFF5FAF7)       // Linen Background with minty accent
val NaturalDarkGreen = Color(0xFF11423B)   // Deep forest dark green

// User's custom palette references
val CustomPaletteMint = Color(0xFFA8E0D2)      // #a8e0d2
val CustomPaletteSeafoam = Color(0xFF6FD3C2)   // #6fd3c2
val CustomPaletteTeal = Color(0xFF3BB8A0)      // #3bb8a0
val CustomPaletteEmerald = Color(0xFF1F7A6D)   // #1f7a6d

// Status & Alert Primitives
val NaturalAlertRed = Color(0xFFBA1A1A)    // Soft M3 Red
val NaturalAlertBg = Color(0xFFFFDAD6)     // Warm Light Coral Red Badge
val NaturalAlertText = Color(0xFF410002)   // Emergency Deep Red Text

// Dynamic PillPal design tokens
val PillPalPrimary: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalSage else NaturalGreen

val PillPalPrimaryDark: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalSage else NaturalDarkGreen

val PillPalSecondary: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalGreen else NaturalSage

val PillPalAccent: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalMutedOlive else NaturalMutedOlive

// Status & Alert Colors
val PillPalSuccess: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalSage else NaturalGreen

val PillPalSuccessBg: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalDarkGreen else NaturalSoftGreen

val PillPalWarningBg: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF2C312E) else NaturalClay

val PillPalWarningText: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalSage else NaturalMutedOlive

val PillPalAlert: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else NaturalAlertRed

val PillPalAlertBg: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF93000A) else NaturalAlertBg

val PillPalAlertText: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFFFFDAD6) else NaturalAlertText

// Pure Semantic Hierarchy
val PillPalBackground: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF191C1B) else NaturalLinen

val PillPalSurface: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF222524) else Color(0xFFFFFFFF)

val PillPalTextPrimary: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFFE1E3E0) else NaturalCharcoal

val PillPalTextSecondary: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xC0E1E3E0) else Color(0xFF404944)

val PillPalDisabled: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF404944) else Color(0xFFE1E3E0)

val PillPalDivider: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF404944) else Color(0xFFE1E3E0)

val PillPalDotMatrix: Color
    @Composable
    get() = if (isSystemInDarkTheme()) NaturalSage else NaturalDarkGreen

