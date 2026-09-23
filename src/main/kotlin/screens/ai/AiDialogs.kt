// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 写操作确认框

package screens.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import components.MaterialSymbols
import utils.ai.ToolCall
import utils.ai.ToolDanger
import utils.ai.ToolDispatcher

/**
 * 会修改系统的工具调用在真正执行前征求用户同意。
 * 用户拒绝后模型会收到「用户拒绝执行」，并被要求停止重试。
 */
@Composable
internal fun AiConfirmDialog(
    call: ToolCall,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val spec = ToolDispatcher.specOf(call.tool)
    val isShell = spec?.danger == ToolDanger.SHELL
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                imageVector = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = { Text(if (isShell) "允许执行终端命令？" else "允许修改系统？") },
        text = {
            Column {
                Text(
                    text = if (isShell) {
                        "AI 请求在终端执行以下命令，命令将以你的用户权限运行："
                    } else {
                        "AI 请求通过包管理器执行以下操作，会修改本机的软件状态："
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = call.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                if (isShell) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "仅在你完全信任模型输出时才允许执行。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAllow) { Text("允许执行") }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("拒绝") }
        }
    )
}
