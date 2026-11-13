// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import androidx.compose.ui.graphics.Color
import config.WallpaperState
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 从当前桌面壁纸中提取一个用于 Material 3 动态配色的种子色。
 *
 * 结果按“壁纸路径 + 修改时间 + 文件大小”缓存；调用方可以按需轮询，
 * 只有壁纸真正变化时才会重新解码和取色。
 */
object DynamicColorProvider {
    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var cachedStamp: Long = 0L

    @Volatile
    private var cachedColor: Color? = null

    @Synchronized
    fun currentSeed(force: Boolean = false): Color? {
        val path = resolveWallpaperPath() ?: run {
            cachedPath = null
            cachedStamp = 0L
            cachedColor = null
            return null
        }
        val file = File(path)
        if (!file.isFile) return null
        val stamp = file.lastModified() * 31L + file.length()
        if (!force && path == cachedPath && stamp == cachedStamp) {
            return cachedColor
        }

        val color = extractSeed(file)
        cachedPath = path
        cachedStamp = stamp
        cachedColor = color
        return color
    }

    private fun resolveWallpaperPath(): String? {
        // 优先真实桌面壁纸；Linux 未识别到桌面环境时，再回退到应用自选背景。
        SystemInfoProvider.getDesktopWallpaperPath()
            ?.takeIf { File(it).isFile }
            ?.let { return it }

        val custom = WallpaperState.customBgFileName
        if (!custom.isNullOrBlank()) {
            val file = File("cardbg", custom)
            if (file.isFile) return file.absolutePath
        }
        return null
    }

    private fun extractSeed(file: File): Color? {
        return try {
            val source = ImageIO.read(file) ?: return null
            val width = 64
            val height = 64
            val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.drawImage(source, 0, 0, width, height, null)
            graphics.dispose()

            data class Bucket(var weight: Float = 0f, var r: Float = 0f, var g: Float = 0f, var b: Float = 0f)

            val buckets = HashMap<Int, Bucket>()
            var fallbackR = 0f
            var fallbackG = 0f
            var fallbackB = 0f
            var fallbackCount = 0

            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    val argb = scaled.getRGB(x, y)
                    val alpha = (argb ushr 24) and 0xFF
                    if (alpha < 128) continue

                    val r = ((argb shr 16) and 0xFF) / 255f
                    val g = ((argb shr 8) and 0xFF) / 255f
                    val b = (argb and 0xFF) / 255f
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val lightness = (max + min) / 2f
                    val delta = max - min
                    val saturation = if (delta == 0f) 0f else if (lightness > 0.5f) {
                        delta / (2f - max - min)
                    } else {
                        delta / (max + min)
                    }

                    if (saturation < 0.08f || lightness < 0.08f || lightness > 0.92f) continue

                    fallbackR += r
                    fallbackG += g
                    fallbackB += b
                    fallbackCount++

                    val hue = when {
                        delta == 0f -> 0f
                        max == r -> ((g - b) / delta + if (g < b) 6f else 0f) / 6f
                        max == g -> ((b - r) / delta + 2f) / 6f
                        else -> ((r - g) / delta + 4f) / 6f
                    }
                    val bucketIndex = (hue * 24f).toInt().coerceIn(0, 23)
                    val weight = 0.35f + saturation * 2.2f - kotlin.math.abs(lightness - 0.5f) * 0.5f
                    val bucket = buckets.getOrPut(bucketIndex) { Bucket() }
                    bucket.weight += weight
                    bucket.r += r * weight
                    bucket.g += g * weight
                    bucket.b += b * weight
                }
            }

            val best = buckets.values.maxByOrNull { it.weight }
            if (best != null && best.weight > 0f) {
                Color(
                    red = (best.r / best.weight).coerceIn(0f, 1f),
                    green = (best.g / best.weight).coerceIn(0f, 1f),
                    blue = (best.b / best.weight).coerceIn(0f, 1f)
                )
            } else if (fallbackCount > 0) {
                Color(
                    red = (fallbackR / fallbackCount).coerceIn(0f, 1f),
                    green = (fallbackG / fallbackCount).coerceIn(0f, 1f),
                    blue = (fallbackB / fallbackCount).coerceIn(0f, 1f)
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
