// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/**
 * 离线模式执行项类型
 * PATH: 可执行文件
 * COMMAND: 指令
 */
enum class MOfflineEntryType {
    PATH, COMMAND
}

/**
 * 离线模式执行项（多实例列表中的一个条目）
 * id: 唯一标识
 * type: 类型
 * value: 路径（PATH）或 指令（COMMAND）
 */
@Serializable
data class OfflineItem(
    val id: String,
    val type: MOfflineEntryType,
    val value: String
)

private val listConfigPath: Path = Path.of("config", "list_offline.json")
private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

/** list_offline.json 的根结构 */
@Serializable
private data class OfflineListWrapper(
    val items: List<OfflineItem> = emptyList()
)

// ============ list_offline.json：多实例列表 ============

/**
 * 生成唯一条目 ID（时间戳 + 随机数）
 */
fun generateItemId(): String {
    return System.currentTimeMillis().toString() + "_" + SecureRandom().nextInt(100000, 999999)
}

/**
 * 读取所有本地条目
 */
fun loadOfflineItems(): List<OfflineItem> {
    return try {
        if (!Files.exists(listConfigPath)) return emptyList()
        val text = Files.readString(listConfigPath)
        val wrapper = json.decodeFromString<OfflineListWrapper>(text)
        // 兼容旧值：若没有 id，重新生成
        wrapper.items.map { if (it.id.isBlank()) it.copy(id = generateItemId()) else it }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 写入条目列表到 list_offline.json
 */
private fun saveOfflineItems(items: List<OfflineItem>) {
    try {
        val dir = listConfigPath.parent
        if (dir != null) Files.createDirectories(dir)
        val tmp = dir.resolve("list_offline.json.tmp")
        Files.writeString(tmp, json.encodeToString(OfflineListWrapper(items)))
        Files.move(tmp, listConfigPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Exception) {
        // ignore write errors for now
    }
}

/**
 * 添加一个本地条目，返回新条目
 */
fun addOfflineItem(type: MOfflineEntryType, value: String): OfflineItem {
    val item = OfflineItem(
        id = generateItemId(),
        type = type,
        value = value
    )
    saveOfflineItems(loadOfflineItems() + item)
    return item
}

/**
 * 更新条目（编辑指令/路径）
 */
fun updateOfflineItem(id: String, value: String) {
    val updated = loadOfflineItems().map {
        if (it.id == id) it.copy(value = value) else it
    }
    saveOfflineItems(updated)
}

/**
 * 删除条目
 */
fun deleteOfflineItem(id: String) {
    saveOfflineItems(loadOfflineItems().filter { it.id != id })
}

/**
 * 通过 id 获取条目
 */
fun findOfflineItem(id: String?): OfflineItem? {
    if (id == null) return null
    return loadOfflineItems().firstOrNull { it.id == id }
}

