package com.example.robottirabasura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4F4B5),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF4F6353),
    secondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFF3A646F),
    tertiaryContainer = Color(0xFFBEEAF6),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF89D89B),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA4F4B5),
    secondary = Color(0xFFB6CCB8),
    secondaryContainer = Color(0xFF384B3C),
    tertiary = Color(0xFFA2CEDA),
    tertiaryContainer = Color(0xFF214C57),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

/** Status colors with fixed meaning (normal / warning / error), independent of the brand palette. */
@Immutable
data class StatusColors(
    val ok: Color,
    val onOk: Color,
    val warning: Color,
    val onWarning: Color,
    val error: Color,
    val onError: Color,
    val neutral: Color,
    val onNeutral: Color,
)

private val LightStatus = StatusColors(
    ok = Color(0xFFC8F0CF), onOk = Color(0xFF0B3D1C),
    warning = Color(0xFFFFE08A), onWarning = Color(0xFF3D2E00),
    error = Color(0xFFFFDAD6), onError = Color(0xFF690005),
    neutral = Color(0xFFE2E3DE), onNeutral = Color(0xFF2F312D),
)

private val DarkStatus = StatusColors(
    ok = Color(0xFF1E5130), onOk = Color(0xFFC8F0CF),
    warning = Color(0xFF5A4500), onWarning = Color(0xFFFFE08A),
    error = Color(0xFF93000A), onError = Color(0xFFFFDAD6),
    neutral = Color(0xFF444842), onNeutral = Color(0xFFE2E3DE),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * Dynamic color is off on purpose: an operator console needs the same colors on every phone so that green, amber and
 * red always mean the same thing.
 */
@Composable
fun RobotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

object RobotThemeExtras {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
