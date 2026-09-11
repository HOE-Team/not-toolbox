// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题缺少的「警告色」（黄）。
 * Material3 的 [androidx.compose.material3.ColorScheme] 只有 error 槽位（红），
 * 因此这里用 CompositionLocal 单独提供一组黄底配色，供警告类 Snackbar 等使用。
 */
@Immutable
data class WarningColors(
    val container: Color,
    val onContainer: Color,
    val action: Color
)

private val warningColorsLight = WarningColors(
    container = Color(0xFFFFF3CD),
    onContainer = Color(0xFF664D03),
    action = Color(0xFF8A6D00)
)

private val warningColorsDark = WarningColors(
    container = Color(0xFF4D3F00),
    onContainer = Color(0xFFFFE08A),
    action = Color(0xFFFFD75E)
)

/** 由 [AppTheme] 根据深浅色提供 */
val LocalWarningColors = staticCompositionLocalOf { warningColorsLight }

internal fun warningColorsFor(darkTheme: Boolean): WarningColors =
    if (darkTheme) warningColorsDark else warningColorsLight

/** 用法：`MaterialTheme.warningColors.container` */
val MaterialTheme.warningColors: WarningColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWarningColors.current
