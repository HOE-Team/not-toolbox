// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.BackgroundTask
import utils.BackgroundTaskStatus

/** 查看/取消所有后台包管理任务。 */
@Composable
fun BackgroundTaskDialog(
    tasks: List<BackgroundTask>,
    onClose: () -> Unit,
    onCancel: (Long) -> Unit,
    onRemove: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MaterialSymbols.Hourglass_Bottom,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("后台任务（" + tasks.size + "）")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    BackgroundTaskCard(
                        task = task,
                        onCancel = { onCancel(task.id) },
                        onRemove = { onRemove(task.id) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("关闭") }
        }
    )
}

@Composable
private fun BackgroundTaskCard(
    task: BackgroundTask,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val statusText = when (task.status) {
        BackgroundTaskStatus.RUNNING -> "运行中"
        BackgroundTaskStatus.SUCCESS -> "已完成"
        BackgroundTaskStatus.FAILED -> "失败"
        BackgroundTaskStatus.CANCELLED -> "已取消"
    }
    val outputPreview = task.output.lineSequence().toList().takeLast(6).joinToString("\n")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = statusText,
                    containerColor = when (task.status) {
                        BackgroundTaskStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
                        BackgroundTaskStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = when (task.status) {
                        BackgroundTaskStatus.RUNNING -> MaterialTheme.colorScheme.onTertiaryContainer
                        BackgroundTaskStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.actionLabel + " · " + task.command,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.status == BackgroundTaskStatus.RUNNING) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (outputPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = outputPreview,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 90.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (task.status == BackgroundTaskStatus.RUNNING) {
                    TextButton(onClick = onCancel) { Text("取消") }
                } else {
                    TextButton(onClick = onRemove) { Text("移除") }
                }
            }
        }
    }
}
