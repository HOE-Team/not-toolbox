// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 模型设置对话框：左侧模型列表（低频操作即时保存），右侧模型表单（草稿 + 保存按钮）

package screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import components.MaterialSymbols
import utils.ai.AiAppConfig
import utils.ai.AiModelConfig
import utils.ai.LlmProvider
import utils.ai.SecretStore

/**
 * 模型设置对话框：左侧模型列表，右侧模型详情表单。
 *
 * 列表操作（新增 / 删除 / 设为当前）即时保存；表单改动先在本地草稿里编辑，点「保存」才落盘。
 * 以前每敲一个字符都会写一次 ai_config.json，还会顺手丢掉整个对话，编辑体验很差。
 */
@Composable
internal fun AiModelDialog(
    config: AiAppConfig,
    onConfigChange: (AiAppConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(config.activeModel()?.id ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(960.dp).height(640.dp)) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AI 模型设置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "密钥保护：" + SecretStore.protectionDescription(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("完成")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                        Button(
                            onClick = {
                                val created = AiModelConfig(
                                    id = "m" + System.currentTimeMillis(),
                                    name = "新模型",
                                    modelName = "",
                                    provider = LlmProvider.OPENAI
                                )
                                onConfigChange(config.addModel(created))
                                selectedId = created.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("新增模型")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        AiModelList(
                            config = config,
                            selectedId = selectedId,
                            onSelect = { selectedId = it },
                            onConfigChange = { updated ->
                                if (selectedId !in updated.models.map { it.id }) {
                                    selectedId = updated.models.firstOrNull()?.id ?: ""
                                }
                                onConfigChange(updated)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier.width(1.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    val selected = config.models.firstOrNull { it.id == selectedId }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (selected == null) {
                            Text(
                                text = "从左侧选择或新增一个模型。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            AiModelForm(
                                model = selected,
                                isActive = selected.id == config.activeModelId,
                                onSave = { updated -> onConfigChange(config.withModel(updated)) },
                                onActivate = { onConfigChange(config.copy(activeModelId = selected.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 左侧：模型列表 + 「允许终端命令工具」总开关。
 * 列表上的操作（切换 / 删除 / 设为当前）都是低频动作，直接落盘。
 */
@Composable
private fun ColumnScope.AiModelList(
    config: AiAppConfig,
    selectedId: String,
    onSelect: (String) -> Unit,
    onConfigChange: (AiAppConfig) -> Unit
) {
    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            config.models.forEach { model ->
                AiModelListItem(
                    model = model,
                    selected = model.id == selectedId,
                    active = model.id == config.activeModelId,
                    onClick = { onSelect(model.id) },
                    onActivate = { onConfigChange(config.copy(activeModelId = model.id)) },
                    onDelete = { onConfigChange(config.withoutModel(model.id)) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (config.models.isEmpty()) {
                Text(
                    text = "还没有模型，点击上方「新增模型」开始配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = config.shellToolEnabled,
                onCheckedChange = { onConfigChange(config.copy(shellToolEnabled = it)) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("允许终端命令工具", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "默认关闭；开启后 AI 可请求执行任意命令",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 左侧模型列表中的一项 */
@Composable
private fun AiModelListItem(
    model: AiModelConfig,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name.ifBlank { "未命名模型" },
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (active) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "使用中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = model.modelName.ifBlank { "未填写模型名" } + " · " + model.provider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!active) {
                IconButton(onClick = onActivate, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.Check,
                        contentDescription = "设为当前模型",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = MaterialSymbols.Delete,
                    contentDescription = "删除模型",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

