// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private fun clamp01(v: Float) = when {
    v < 0f -> 0f
    v > 1f -> 1f
    else -> v
}

fun isValidHex(hex: String): Boolean {
    return Regex("^#?[0-9a-fA-F]{6}$").matches(hex)
}

fun parseHexToColor(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    if (!isValidHex(cleaned)) return null
    val intVal = cleaned.toLong(16).toInt()
    val r = (intVal shr 16) and 0xFF
    val g = (intVal shr 8) and 0xFF
    val b = intVal and 0xFF
    return Color(r, g, b)
}

fun adjustLuminance(color: Color, factor: Float): Color {
    val r = clamp01(color.red * factor)
    val g = clamp01(color.green * factor)
    val b = clamp01(color.blue * factor)
    return Color(r, g, b, color.alpha)
}

// 用于 Material 3「色阶（tone）」映射的 HSL 转换辅助函数。
// 同一颜色角色保持色相与彩度不变，但按主题置于不同的色阶（明度）：
// 深色主题提亮强调色（色阶 80）并压暗容器色（色阶 30），
// 对应浅色主题的色阶 40 / 90。
private data class HSL(val hue: Float, val sat: Float, val light: Float)

private fun Color.toHsl(): HSL {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    val s = if (max == min) 0f else if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when {
        max == min -> 0f
        max == r -> (g - b) / d + (if (g < b) 6f else 0f)
        max == g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * (1f / 6f)
    return HSL(h.coerceIn(0f, 1f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hslToColor(hue: Float, sat: Float, light: Float, alpha: Float): Color {
    val c = (1f - kotlin.math.abs(2f * light - 1f)) * sat
    val hp = hue * 6f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = light - c / 2f
    return Color(r1 + m, g1 + m, b1 + m, alpha)
}

/** Re-color the given color onto the requested Material 3 tone (0-100), keeping its hue & chroma. */
fun toneOf(color: Color, tone: Float): Color {
    val hsl = color.toHsl()
    return hslToColor(hsl.hue, hsl.sat, (tone / 100f).coerceIn(0f, 1f), color.alpha)
}

/**
 * 生成接近中性灰、同时保留主色色相的低饱和容器色。
 * M3 的 surfaceVariant/平级容器（如 #E7E0EC）饱和度很低，几乎只是"带色的浅灰"。
 * 这里固定低饱和度，只把色调提到目标亮度，避免容器显得过浓/过深。
 */
fun containerColor(color: Color, tone: Float): Color {
    val hsl = color.toHsl()
    // 取原饱和度的约一半（上限 0.30），既保留明显的色相、又不至于像高饱和那样浓深
    val softSat = (hsl.sat * 0.8f).coerceAtMost(0.30f)
    return hslToColor(hsl.hue, softSat, (tone / 100f).coerceIn(0f, 1f), color.alpha)
}

fun contrastColor(color: Color): Color {
    val lum = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (lum < 0.5f) Color(0xFFFFFFFF) else Color(0xFF000000)
}

/* -------------------------------------------------------------------------
 * Material 3 Expressive 配色
 *
 * 与旧实现只做「同一色相 + 色阶」不同，这里按 M3 Expressive 的做法：
 *  - primary 维持用户种子色相；
 *  - secondary 偏移约 18°，作为柔和的中性强调；
 *  - tertiary 偏移约 60°，作为高对比的「表达色」；
 *  - 同时补齐 surfaceContainerLowest…Highest、surfaceDim/Bright 等容器色阶，
 *    让页面可以用「同色系分层」而不是阴影来表达层级。
 *
 * 所有角色都由种子色推导，用户设置的 HEX 主题色仍然生效。
 * ---------------------------------------------------------------------- */

private fun toneColor(hueDeg: Float, sat: Float, tone: Float, alpha: Float = 1f): Color {
    val h = ((hueDeg % 360f) + 360f) % 360f / 360f
    return hslToColor(h, sat.coerceIn(0f, 1f), (tone / 100f).coerceIn(0f, 1f), alpha)
}

private fun containerTone(hueDeg: Float, sat: Float, tone: Float, alpha: Float = 1f): Color {
    // 容器色保持低彩度：既看得出色相，又不会比内容更抢眼。
    val soft = (sat * 0.55f).coerceIn(0f, 0.32f)
    return toneColor(hueDeg, soft, tone, alpha)
}

fun generateColorScheme(seed: Color, dark: Boolean) = if (dark) {
    val hsl = seed.toHsl()
    val hue = hsl.hue * 360f
    val chroma = if (hsl.sat < 0.05f) hsl.sat else hsl.sat.coerceAtLeast(0.42f)
    val secondaryHue = hue + 18f
    val tertiaryHue = hue + 60f
    val neutralSat = if (hsl.sat < 0.05f) 0f else 0.055f

    val primary = toneColor(hue, chroma, 80f)
    val secondary = toneColor(secondaryHue, chroma * 0.60f, 80f)
    val tertiary = toneColor(tertiaryHue, chroma * 0.85f, 80f)

    darkColorScheme().copy(
        primary = primary,
        // 色阶 80 的强调色偏亮，因此其前景色采用同色相的色阶 20
        // （如浅紫上的深紫），而非生硬的纯黑。
        onPrimary = toneColor(hue, chroma * 0.55f, 20f),
        primaryContainer = containerTone(hue, chroma, 30f),
        onPrimaryContainer = toneColor(hue, chroma * 0.55f, 90f),
        inversePrimary = toneColor(hue, chroma, 40f),
        secondary = secondary,
        onSecondary = toneColor(secondaryHue, chroma * 0.40f, 20f),
        secondaryContainer = containerTone(secondaryHue, chroma, 30f),
        onSecondaryContainer = toneColor(secondaryHue, chroma * 0.45f, 90f),
        tertiary = tertiary,
        onTertiary = toneColor(tertiaryHue, chroma * 0.45f, 20f),
        tertiaryContainer = containerTone(tertiaryHue, chroma, 30f),
        onTertiaryContainer = toneColor(tertiaryHue, chroma * 0.50f, 90f),
        background = toneColor(hue, neutralSat, 7f),
        onBackground = toneColor(hue, neutralSat * 0.5f, 92f),
        surface = toneColor(hue, neutralSat, 10f),
        onSurface = toneColor(hue, neutralSat * 0.5f, 92f),
        surfaceVariant = toneColor(hue, neutralSat * 1.8f, 30f),
        onSurfaceVariant = toneColor(hue, neutralSat * 1.2f, 80f),
        surfaceTint = primary,
        surfaceDim = toneColor(hue, neutralSat, 6f),
        surfaceBright = toneColor(hue, neutralSat, 24f),
        surfaceContainerLowest = toneColor(hue, neutralSat, 4f),
        surfaceContainerLow = toneColor(hue, neutralSat, 12f),
        surfaceContainer = toneColor(hue, neutralSat, 16f),
        surfaceContainerHigh = toneColor(hue, neutralSat, 20f),
        surfaceContainerHighest = toneColor(hue, neutralSat, 24f),
        outline = toneColor(hue, neutralSat * 1.4f, 60f),
        outlineVariant = toneColor(hue, neutralSat * 2.0f, 30f),
        inverseSurface = toneColor(hue, neutralSat * 0.5f, 90f),
        inverseOnSurface = toneColor(hue, neutralSat * 0.5f, 20f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
} else {
    val hsl = seed.toHsl()
    val hue = hsl.hue * 360f
    val chroma = if (hsl.sat < 0.05f) hsl.sat else hsl.sat.coerceAtLeast(0.42f)
    val secondaryHue = hue + 18f
    val tertiaryHue = hue + 60f
    val neutralSat = if (hsl.sat < 0.05f) 0f else 0.05f

    val primary = toneColor(hue, chroma, 40f)
    val secondary = toneColor(secondaryHue, chroma * 0.60f, 40f)
    val tertiary = toneColor(tertiaryHue, chroma * 0.85f, 40f)

    lightColorScheme(
        primary = primary,
        onPrimary = contrastColor(primary),
        primaryContainer = containerTone(hue, chroma, 90f),
        onPrimaryContainer = toneColor(hue, chroma * 0.55f, 10f),
        inversePrimary = toneColor(hue, chroma, 80f),
        secondary = secondary,
        onSecondary = contrastColor(secondary),
        secondaryContainer = containerTone(secondaryHue, chroma, 90f),
        onSecondaryContainer = toneColor(secondaryHue, chroma * 0.45f, 10f),
        tertiary = tertiary,
        onTertiary = contrastColor(tertiary),
        tertiaryContainer = containerTone(tertiaryHue, chroma, 90f),
        onTertiaryContainer = toneColor(tertiaryHue, chroma * 0.50f, 10f),
        background = toneColor(hue, neutralSat, 99f),
        onBackground = toneColor(hue, neutralSat * 0.5f, 10f),
        surface = toneColor(hue, neutralSat, 99f),
        onSurface = toneColor(hue, neutralSat * 0.5f, 10f),
        surfaceVariant = toneColor(hue, neutralSat * 1.8f, 92f),
        onSurfaceVariant = toneColor(hue, neutralSat * 1.2f, 30f),
        surfaceTint = primary,
        surfaceDim = toneColor(hue, neutralSat, 87f),
        surfaceBright = toneColor(hue, neutralSat, 100f),
        surfaceContainerLowest = toneColor(hue, neutralSat, 100f),
        surfaceContainerLow = toneColor(hue, neutralSat, 97f),
        surfaceContainer = toneColor(hue, neutralSat, 95f),
        surfaceContainerHigh = toneColor(hue, neutralSat, 93f),
        surfaceContainerHighest = toneColor(hue, neutralSat, 91f),
        outline = toneColor(hue, neutralSat * 1.4f, 50f),
        outlineVariant = toneColor(hue, neutralSat * 2.0f, 82f),
        inverseSurface = toneColor(hue, neutralSat * 0.5f, 20f),
        inverseOnSurface = toneColor(hue, neutralSat * 0.5f, 95f),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}
