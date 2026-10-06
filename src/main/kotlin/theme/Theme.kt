// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * AppTheme 封装 MaterialTheme，并根据十六进制种子色构建配色方案。
 * 当 seedHex 为 null 或非法时，回退到默认 Material 配色。
 */
@Composable
fun AppTheme(darkTheme: Boolean, seedHex: String?, content: @Composable () -> Unit) {
    val seedColor: Color? = seedHex?.let { parseHexToColor(it) }
    val base = seedColor ?: Color(0xFF6750A4) // fallback seed
    val scheme = generateColorScheme(base, darkTheme)

    CompositionLocalProvider(LocalWarningColors provides warningColorsFor(darkTheme)) {
        MaterialTheme(colorScheme = scheme) {
            content()
        }
    }
}
