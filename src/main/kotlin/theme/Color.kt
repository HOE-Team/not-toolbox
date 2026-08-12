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

// HSL conversion helpers for Material 3 "tone" mapping.
// A color role keeps its hue & chroma but is placed on a different tone
// (lightness) per theme: dark themes lighten accents (Tone 80) and darken
// containers (Tone 30), mirroring light themes' Tone 40 / Tone 90.
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

fun generateColorScheme(seed: Color, dark: Boolean) = if (dark) {
    // Material 3 dark scheme via tone mapping: the same color roles keep their
    // hue & chroma but move to different tones than light mode. Accents go to
    // Tone 80 (lighter, for contrast on dark surfaces), containers drop to
    // Tone 30 (darker), and text on containers goes to Tone 90.
    val primaryTone80 = toneOf(seed, 80f)
    val primaryContainerTone30 = toneOf(seed, 30f)
    val secondaryTone80 = toneOf(seed, 80f)
    val secondaryContainerTone30 = toneOf(seed, 30f)
    val tertiaryTone80 = toneOf(seed, 80f)
    val tertiaryContainerTone30 = toneOf(seed, 30f)

    darkColorScheme().copy(
        primary = primaryTone80,
        // Tone-80 accents are light; on-colors use tone-20 in the same hue
        // (e.g. deep purple on light purple) instead of a stark pure black,
        // matching M3 on-color contrast (see #D0BCFF / #371E73).
        onPrimary = toneOf(seed, 20f),
        primaryContainer = primaryContainerTone30,
        onPrimaryContainer = toneOf(seed, 90f),
        secondary = secondaryTone80,
        onSecondary = toneOf(seed, 20f),
        secondaryContainer = secondaryContainerTone30,
        onSecondaryContainer = toneOf(seed, 90f),
        tertiary = tertiaryTone80,
        onTertiary = toneOf(seed, 20f),
        tertiaryContainer = tertiaryContainerTone30,
        onTertiaryContainer = toneOf(seed, 90f),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF1D1B20),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        inverseSurface = Color(0xFFE6E1E5),
        inverseOnSurface = Color(0xFF322F35),
        inversePrimary = seed,
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC)
    )
} else {
    // 浅色主题：保持原有配色；仅新增 NavRail 选中项背景(secondaryContainer)
    // 和输入框/卡片背景(surfaceVariant)跟随主色，其余图标/字体着色角色不变。
    lightColorScheme(
        primary = seed,
        onPrimary = contrastColor(seed),
        secondary = adjustLuminance(seed, 0.9f),
        onSecondary = contrastColor(adjustLuminance(seed, 0.9f)),
        secondaryContainer = containerColor(seed, 90f),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = containerColor(seed, 90f)
    )
}
