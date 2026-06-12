// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

data class SystemOverview(
    val osVersion: String,
    val architecture: String,
    val windowsUpdateStatus: String,
    val platform: String,
    val computerName: String,
    val wallpaperPath: String?
)
