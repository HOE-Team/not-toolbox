// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package config

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.ai.AiAppConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** AI 功能配置文件（config/ai_config.json） */
private val aiConfigPath: Path = Path.of("config", "ai_config.json")

private val aiJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

/** 读取 AI 配置；文件缺失或损坏时返回空配置 */
fun loadAiConfig(): AiAppConfig {
    return try {
        if (!Files.exists(aiConfigPath)) return AiAppConfig()
        val text = Files.readString(aiConfigPath)
        aiJson.decodeFromString<AiAppConfig>(text)
    } catch (e: Exception) {
        AiAppConfig()
    }
}

/** 原子写入 AI 配置 */
fun saveAiConfig(config: AiAppConfig) {
    try {
        val dir = aiConfigPath.parent
        if (dir != null) Files.createDirectories(dir)
        val tmp = dir.resolve("ai_config.json.tmp")
        Files.writeString(tmp, aiJson.encodeToString(config))
        Files.move(tmp, aiConfigPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Exception) {
        // 忽略写入错误
    }
}