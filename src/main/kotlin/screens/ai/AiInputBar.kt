// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 底部输入区：启用思考开关 + 输入框 + 发送 / 停止

package screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import components.MaterialSymbols

@Composable
internal fun AiInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    thinkingEnabled: Boolean,
    thinkingSupported: Boolean,
    onThinkingEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
    ) {
        // 思考开关只对支持 thinking 参数的提供商显示——否则是个「点了没用」的假开关
        if (thinkingSupported) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiToggleChip(
                    label = "启用思考",
                    checked = thinkingEnabled,
                    onCheckedChange = onThinkingEnabledChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter && event.type == KeyEventType.KeyDown
                    if (enter && !event.isShiftPressed) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
                placeholder = { Text("键入以发送（Enter 发送，Shift+Enter 换行）") },
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (busy) {
                FilledTonalButton(onClick = onStop, modifier = Modifier.height(56.dp)) {
                    Icon(MaterialSymbols.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("停止")
                }
            } else {
                Button(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.height(56.dp)) {
                    Icon(MaterialSymbols.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("发送")
                }
            }
        }
    }
}

/** 输入区上方开关 */
@Composable
private fun AiToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (checked) {
            {
                Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        } else {
            null
        }
    )
}
