// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private data class NavEntry(
    val label: String,
    val icon: ImageVector
)

private val navEntries = listOf(
    NavEntry("概览", MaterialSymbols.Home),
    NavEntry("包", MaterialSymbols.Package),
    NavEntry("终端", MaterialSymbols.Terminal),
    NavEntry("AI", MaterialSymbols.Robot2),
    NavEntry("设置", MaterialSymbols.Settings),
    NavEntry("关于", MaterialSymbols.Info)
)

/**
 * Expressive 侧边导航：
 * - 独立的圆角容器（surfaceContainerLow），而不是贴着窗口边缘的默认 Rail；
 * - 导航项在容器内垂直居中；
 * - 选中态使用 primary 实心胶囊，和页面主色联动。
 */
@Composable
fun NavRail(
    selectedIndex: Int = 0,
    onSelection: (Int) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(100.dp),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                navEntries.forEachIndexed { index, entry ->
                    NavItem(
                        label = entry.label,
                        icon = entry.icon,
                        selected = selectedIndex == index,
                        onClick = { onSelection(index) }
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "nav-container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "nav-content"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = content
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
