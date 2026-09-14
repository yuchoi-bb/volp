package com.volp.travelbudget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

private val LightColors = lightColorScheme(
    primary = Teal40,
    secondary = TealGrey40,
    tertiary = Sand40,
)

private val DarkColors = darkColorScheme(
    primary = Teal80,
    secondary = TealGrey80,
    tertiary = Sand80,
)

@Composable
fun VolpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VolpTypography,
        content = content,
    )
}

/** 예산 초과 여부를 나타내는 색. 다크 모드에서도 읽히도록 두 벌을 둔다. */
object BudgetColors {
    val over: androidx.compose.ui.graphics.Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) OverBudgetDark else OverBudget

    val under: androidx.compose.ui.graphics.Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) UnderBudgetDark else UnderBudget
}
