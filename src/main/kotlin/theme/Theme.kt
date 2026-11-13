// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive 的圆角体系。
 *
 * 相比基础 M3，Expressive 更强调「大而柔和」的角落：
 * 卡片、对话框、面板统一使用 28dp（extraLarge）级别的圆角，
 * 小型控件（chip/输入框）使用 12–16dp，按钮则保持全圆角。
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Expressive 字体：保留系统字体族，只把标题/展示层级加粗、收紧字距。
 * 正文保持常规字重，避免整页变得厚重。
 */
private val ExpressiveTypography: Typography = run {
    val base = Typography()
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium)
    )
}

/**
 * AppTheme 封装 Material 3 Expressive 主题，并根据十六进制种子色构建配色方案。
 * 当 seedHex 为 null 或非法时，回退到默认 Material 配色。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean,
    seedHex: String?,
    dynamicSeed: Color? = null,
    content: @Composable () -> Unit
) {
    val colorFromHex: Color? = seedHex?.let { parseHexToColor(it) }
    val seedColor: Color? = dynamicSeed ?: colorFromHex
    val base = seedColor ?: Color(0xFF6750A4) // fallback seed
    val scheme = generateColorScheme(base, darkTheme)

    CompositionLocalProvider(LocalWarningColors provides warningColorsFor(darkTheme)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content
        )
    }
}
