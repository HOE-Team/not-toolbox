// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 单个模型的配置表单：草稿编辑 + 保存落盘

package screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import components.MaterialSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import utils.ai.AiModelConfig
import utils.ai.LlmProvider
import utils.ai.SecretStore
import utils.ai.ToolDanger
import utils.ai.ToolDispatcher

/** API Key 自动保存的防抖时长：停止输入这么久之后才加密落盘 */
private const val KEY_SAVE_DEBOUNCE_MS = 700L

/** 表单里的开关行 */
@Composable
private fun AiSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 单个模型的详细配置表单。
 *
 * 所有字段先改在本地草稿上，点「保存」才整体落盘（保存会重置当前对话，因此不能每键都做）。
 * **API Key 例外**：表单会载入已保存密钥的明文，用户可直接就地修改；改动静默防抖后自动加密落盘
 * （`onSaveKey`），不需要重新输入整段，也不必再点表单的「保存」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiModelForm(
    model: AiModelConfig,
    isActive: Boolean,
    onSave: (AiModelConfig) -> Unit,
    onSaveKey: (encrypted: String, plain: String) -> Unit,
    onActivate: () -> Unit
) {
    var draft by remember(model.id) { mutableStateOf(model) }
    // API Key：输入框直接放已保存密钥的明文（默认按密码遮蔽），因此可以改任意一位
    var keyInput by remember(model.id) { mutableStateOf("") }

    /** 已经落盘的密钥明文：用来判断输入框是否有改动（null = 还没载入） */
    var savedKey by remember(model.id) { mutableStateOf<String?>(null) }
    var keyVisible by remember(model.id) { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    val dirty = draft != model

    // 载入密钥明文（解密放后台线程）。切换模型、或密钥在别处被改写时同步过来；
    // 用户正在输入（与已保存值不同）时不覆盖，避免打字被回滚。
    LaunchedEffect(model.id, model.apiKeyEnc) {
        if (keyInput.isNotEmpty() && keyInput != savedKey) return@LaunchedEffect
        val plain = withContext(Dispatchers.Default) { SecretStore.unprotect(model.apiKeyEnc) }
        savedKey = plain
        keyInput = plain
    }

    // 改动后静默自动保存：防抖到停止输入再加密落盘（每次都加密会明显卡顿）
    LaunchedEffect(keyInput, savedKey) {
        val target = keyInput.trim()
        if (savedKey == null || target == savedKey) return@LaunchedEffect
        delay(KEY_SAVE_DEBOUNCE_MS)
        val encrypted = withContext(Dispatchers.Default) { SecretStore.protect(target) }
        // 同步草稿里的密文，否则「保存」按钮会把旧密文写回去
        savedKey = target
        draft = draft.copy(apiKeyEnc = encrypted)
        onSaveKey(encrypted, target)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "模型详情", style = MaterialTheme.typography.titleSmall)
            if (dirty) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "有未保存的改动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!isActive) {
                TextButton(onClick = onActivate) { Text("设为当前模型") }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSave(draft) }, enabled = dirty) {
                Icon(MaterialSymbols.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("保存")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text("显示名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = it }) {
            OutlinedTextField(
                value = draft.provider.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("提供商") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                LlmProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            providerExpanded = false
                            val keepBaseUrl = draft.baseUrl.isNotBlank() && draft.baseUrl != draft.provider.defaultBaseUrl
                            draft = draft.copy(
                                provider = provider,
                                baseUrl = if (keepBaseUrl) draft.baseUrl else ""
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.modelName,
            onValueChange = { draft = draft.copy(modelName = it) },
            label = { Text("模型名称") },
            placeholder = { Text("例如 gpt-4o-mini / deepseek-chat / qwen-plus") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.baseUrl,
            onValueChange = { draft = draft.copy(baseUrl = it) },
            label = { Text("Base URL（留空使用默认）") },
            placeholder = { Text(draft.provider.defaultBaseUrl.ifBlank { "https://example.com/v1" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "实际请求地址：" + draft.provider.chatCompletionsUrl(draft.baseUrl),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("API Key") },
            placeholder = { Text("粘贴 API Key") },
            singleLine = true,
            // 默认按密码遮蔽，点右侧眼睛图标在「明文 / 遮蔽」之间切换
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) {
                            MaterialSymbols.VisibilityOff
                        } else {
                            MaterialSymbols.VisibilitySymbol
                        },
                        contentDescription = if (keyVisible) "隐藏密钥" else "显示密钥",
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "已保存的密钥会填入本框，直接改任意一位即可；停止输入后自动保存并立即生效（清空即删除密钥）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.systemPrompt,
            onValueChange = { draft = draft.copy(systemPrompt = it) },
            label = { Text("系统提示词（留空使用默认）") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        AiSwitchRow(
            title = "流式输出",
            subtitle = "逐字显示回答；部分网关不支持时可关闭",
            checked = draft.useStream,
            onCheckedChange = { draft = draft.copy(useStream = it) }
        )
        AiSwitchRow(
            title = "允许调用内置工具",
            subtitle = "关闭后模型只能用自身知识回答，无法读取本机状态",
            checked = draft.toolsEnabled,
            onCheckedChange = { draft = draft.copy(toolsEnabled = it) }
        )
        AiSwitchRow(
            title = "修改系统的操作需要确认",
            subtitle = "安装 / 更新 / 卸载 / 执行命令前弹窗征求同意（建议保持开启）",
            checked = draft.confirmWriteOps,
            onCheckedChange = { draft = draft.copy(confirmWriteOps = it) }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Text(text = "工具清单", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "取消勾选即禁用该工具（不会写入提示词，模型也无法调用）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ToolDispatcher.allSpecs.forEach { spec ->
            val enabled = spec.name !in draft.disabledTools
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        val next = if (checked) {
                            draft.disabledTools - spec.name
                        } else {
                            draft.disabledTools + spec.name
                        }
                        draft = draft.copy(disabledTools = next)
                    }
                )
                Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = spec.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        if (spec.danger != ToolDanger.READ) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (spec.danger == ToolDanger.SHELL) "终端命令" else "写操作",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (spec.danger == ToolDanger.SHELL && !draft.disabledTools.contains(spec.name)) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "还需在左下角打开总开关",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = spec.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
