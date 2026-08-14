// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package config

/**
 * 壁纸相关的运行时状态。
 *
 * 通过 [MainApp] 在启动时从持久化配置载入并写入，供
 * [utils.SystemInfoProvider] 读取以决定概览卡片壁纸来源。
 *
 * 注意：Linux 上"不获取桌面壁纸"为强制开启（与配置文件解耦），
 * 也就是说即使配置文件中 useCustomBg=false，Linux 也始终不使用系统桌面壁纸。
 */
object WallpaperState {
    /** 用户选择"不获取桌面壁纸"（在 Linux 上此值被忽略，始终视为开启） */
    @Volatile
    var useCustomBg: Boolean = false

    /** 用户自选的本地背景文件名，存放在程序目录 /cardbg/ 下；null 表示未选择 */
    @Volatile
    var customBgFileName: String? = null

    /**
     * 最终决定是否"不获取桌面壁纸"。
     * Linux 强制返回 true；其他平台遵循用户配置。
     */
    fun useDefaultWallpaper(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return os.contains("linux") || useCustomBg
    }
}