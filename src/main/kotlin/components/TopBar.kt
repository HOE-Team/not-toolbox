// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Expressive 顶部栏：更高的呼吸空间、左侧色条与加粗标题。
 * 标题过长时仍沿用原有的「逐级缩小字号」策略，保证窄窗口下不截断。
 */
@Composable
fun TopBar(
    title: String = "概览",
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题左侧的强调色条：Expressive 风格的页面锚点
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(14.dp))

            BoxWithConstraints(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                val density = LocalDensity.current
                val maxPx = with(density) { maxWidth.toPx() }
                val measurer = rememberTextMeasurer()
                var fontSize = 26.sp
                val minFont = 16.sp
                var measuredWidth = measurer.measure(
                    AnnotatedString(title),
                    style = TextStyle(fontSize = fontSize)
                ).size.width.toFloat()
                while (measuredWidth > maxPx && fontSize > minFont) {
                    fontSize = (fontSize.value - 1).coerceAtLeast(minFont.value).let { it.sp }
                    measuredWidth = measurer.measure(
                        AnnotatedString(title),
                        style = TextStyle(fontSize = fontSize)
                    ).size.width.toFloat()
                }

                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}
