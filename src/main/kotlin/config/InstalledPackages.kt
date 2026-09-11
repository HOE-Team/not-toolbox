// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package config

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * installed_packages.json 的 schema 版本。
 * 版本不符时旧缓存会被视为无效并触发重新扫描。
 */
const val INSTALLED_PACKAGES_SCHEMA_VERSION = 1

/**
 * 单个已安装包的信息。
 * 使用包管理器的「原生包名」（如 `git`、`Git.Git`、`visual-studio-code-bin`）作为键。
 */
@Serializable
data class InstalledPackageEntry(
    val version: String? = null
)

/**
 * 某个包管理器的扫描结果（全量已安装集合）。
 */
@Serializable
data class InstalledManagerScan(
    val scannedAt: Long = 0L,
    val packages: Map<String, InstalledPackageEntry> = emptyMap()
)

/**
 * installed_packages.json 的根结构。
 * scans 的键为 [utils.PackageManagerType] 的枚举名（如 "PACMAN"、"WINGET"）。
 */
@Serializable
data class InstalledPackagesFile(
    val schemaVersion: Int = INSTALLED_PACKAGES_SCHEMA_VERSION,
    val platform: String = "",
    val scans: Map<String, InstalledManagerScan> = emptyMap()
)

private val installedPackagesPath: Path = Path.of("config", "installed_packages.json")
private val installedPackagesJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

/**
 * 当前平台标识：windows / linux / other。
 */
fun currentPlatformName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("windows") -> "windows"
        os.contains("linux") -> "linux"
        else -> "other"
    }
}

/**
 * 已安装包快照缓存文件是否存在。
 * 用于判定「缓存失效（文件被删除）」这一检测触发条件。
 */
fun installedPackagesFileExists(): Boolean = Files.exists(installedPackagesPath)

/**
 * 读取已安装包快照。
 * 文件不存在、解析失败、schema 版本或平台不符时，返回空快照（调用方据此触发重新扫描）。
 */
fun loadInstalledPackages(): InstalledPackagesFile {
    return try {
        if (!Files.exists(installedPackagesPath)) {
            return InstalledPackagesFile(platform = currentPlatformName())
        }
        val content = Files.readString(installedPackagesPath)
        val file = installedPackagesJson.decodeFromString<InstalledPackagesFile>(content)
        if (file.schemaVersion != INSTALLED_PACKAGES_SCHEMA_VERSION || file.platform != currentPlatformName()) {
            InstalledPackagesFile(platform = currentPlatformName())
        } else {
            file
        }
    } catch (e: Exception) {
        InstalledPackagesFile(platform = currentPlatformName())
    }
}

/**
 * 原子写入已安装包快照（先写临时文件再移动，避免中断导致文件损坏）。
 */
fun saveInstalledPackages(file: InstalledPackagesFile) {
    try {
        val dir = installedPackagesPath.parent
        if (dir != null) Files.createDirectories(dir)
        val tmp = dir.resolve("installed_packages.json.tmp")
        val normalized = file.copy(
            schemaVersion = INSTALLED_PACKAGES_SCHEMA_VERSION,
            platform = currentPlatformName()
        )
        Files.writeString(tmp, installedPackagesJson.encodeToString(normalized))
        Files.move(
            tmp,
            installedPackagesPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (e: Exception) {
        // 写入失败不影响运行时状态；下次启动会重新扫描
    }
}
