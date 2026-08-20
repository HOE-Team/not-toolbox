// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import utils.SystemOverview
import java.io.File
import androidx.compose.ui.graphics.decodeToImageBitmap

@Composable
fun SystemOverviewCard(
    overview: SystemOverview,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()) {
            // 背景壁纸。
            // - Linux：不获取真实壁纸，直接使用内置默认壁纸。
            // - Windows：尝试加载注册表检测到的壁纸路径；仅当明确无法获取
            //   （未检测到 / 文件不存在 / 加载失败）时才回退到默认壁纸。
            // 默认壁纸：亮色主题使用 default-light.png，暗色主题使用 default.png。
            // 根据当前 MaterialTheme 的 surface 底色亮度推断处于亮色还是暗色主题。
            val surfaceColor = MaterialTheme.colorScheme.surface
            val surfaceLum = surfaceColor.red * 0.2126f + surfaceColor.green * 0.7152f + surfaceColor.blue * 0.0722f
            val isDark = surfaceLum < 0.5f
            val wallpaperBitmap = remember(overview.wallpaperPath, isDark) {
                var bmp: ImageBitmap? = null
                val p = overview.wallpaperPath
                if (!p.isNullOrBlank()) {
                    try {
                        val f = File(p)
                        if (f.exists()) {
                            bmp = f.readBytes().decodeToImageBitmap()
                        }
                    } catch (_: Exception) {
                        bmp = null
                    }
                }
                if (bmp == null) {
                    bmp = try {
                        // 回退：内置默认壁纸（打包在 resources/img/ 下）
                        val fallbackName = if (isDark) "img/default.png" else "img/default-light.png"
                        val res = Thread.currentThread().contextClassLoader?.getResourceAsStream(fallbackName)
                            ?: ClassLoader.getSystemClassLoader().getResourceAsStream(fallbackName)
                        if (res != null) {
                            val loaded = res.readBytes().decodeToImageBitmap()
                            res.close()
                            loaded
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                bmp
            }

            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Left (opaque) -> Right (transparent) overlay.
            // Text sits on the left, so the left side is more opaque for readability,
            // while the right side is more transparent to keep the wallpaper visible.
            // matchParentSize: measured last, matches the parent Box's final size
            // (fillMaxSize in a wrapContentHeight Box can resolve incorrectly).
            Box(modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 1.0f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                        )
                    )
                )
            )

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                Text(
                    text = "系统概览",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = overview.computerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "操作系统版本: ${overview.osVersion}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "系统架构: ${overview.architecture}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "平台: ${overview.platform}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
