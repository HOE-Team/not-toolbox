// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 管理"使用本地图像"：将用户选择的图片复制到程序目录下的 cardbg/ 新目录，
 * 返回复制后的文件名，供持久化保存用。
 */
object CardBgManager {

    /** 存储目录：程序目录/cardbg/ */
    private val cardBgDir: Path = Path.of("cardbg")

    /**
     * 将 [sourcePath] 指向的图片复制到 cardbg/ 目录下，返回目标文件名。
     * 若复制失败返回 null。
     */
    fun importImage(sourcePath: String): String? {
        return try {
            val src = Path.of(sourcePath)
            if (!Files.exists(src)) return null
            // 生成目标文件名：保留原文件扩展名，前缀 bg_ 加时间戳避免冲突
            val ext = run {
                val name = src.fileName.toString()
                val idx = name.lastIndexOf('.')
                if (idx > 0) name.substring(idx) else ".png"
            }
            val targetName = "bg_${System.currentTimeMillis()}$ext"
            Files.createDirectories(cardBgDir)
            val target = cardBgDir.resolve(targetName)
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
            targetName
        } catch (_: Exception) {
            null
        }
    }

    /** 已存在自选背景时，解析其绝对路径（供展示/加载）；不存在返回 null。 */
    fun resolveCustomBgPath(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val f = cardBgDir.resolve(fileName).toFile()
        return if (f.exists()) f.absolutePath else null
    }
}