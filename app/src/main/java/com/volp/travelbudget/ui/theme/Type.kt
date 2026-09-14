package com.volp.travelbudget.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

val VolpTypography = defaults.copy(
    // 금액은 자리수를 눈으로 세는 일이 많아 조금 더 굵고 크게 잡는다.
    headlineMedium = defaults.headlineMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    ),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)
