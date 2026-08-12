// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    startBar: @Composable () -> Unit = {},
    topBarTitle: String = "概览",
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    // 检测当前操作系统
    val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    MaterialTheme {
        Scaffold() { paddingValues ->
            // fillMaxSize 确保宽高随窗口尺寸变化强制重排（修复 Linux 下缩放窗口显示面积不变）
            Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Left rail occupies full height
                Box(modifier = Modifier.fillMaxHeight()) { startBar() }

                // Right side: TopBar at top, then content fills remaining space
                Column(modifier = Modifier.fillMaxSize()) {
                    TopBar(title = topBarTitle, actions = topBarActions)

                    // 如果是macOS，显示警告
                    if (isMacOS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(MaterialSymbols.Warning, "警告", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            Text("当前系统为macOS环境，您将无法使用大部分功能", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) { content() }
                }
            }
        }
    }
}