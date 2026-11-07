// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 是否启用 Material 3 Expressive 的波浪形进度指示器。
 *
 * 由 MainApp 根据「设置 > 程序外观 > 波浪形进度指示器」提供；
 * 进度组件读取此值，在波浪形与常规实心指示器之间切换。
 */
val LocalWavyProgress = staticCompositionLocalOf { false }
