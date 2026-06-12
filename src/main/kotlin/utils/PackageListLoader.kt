// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.InputStreamReader

/**
 * 从 GitHub RAW 拉取包列表的加载器
 */
object PackageListLoader {
    private val json = Json { ignoreUnknownKeys = true }
    
    // GitHub RAW 基础 URL
    private const val RAW_BASE_URL = "https://raw.githubusercontent.com/HOE-Team/not-toolbox-resource/refs/heads/main"
    
    // 各包管理器的远程文件路径
    private val remoteFilePaths = mapOf(
        PackageManagerType.APT to "linux/packages-linux-apt.json",
        PackageManagerType.DNF to "linux/packages-linux-dnf.json",
        PackageManagerType.PACMAN to "linux/packages-linux-pacman.json",
        PackageManagerType.ZYPPER to "linux/packages-linux-zypper.json",
        PackageManagerType.EMERGE to "linux/packages-linux-emerge.json",
        PackageManagerType.NIX to "linux/packages-linux-nix.json",
        PackageManagerType.WINGET to "windows/packages-windows-winget.json",
        PackageManagerType.SCOOP to "windows/packages-windows-scoop.json",
        PackageManagerType.CHOCOLATEY to "windows/packages-windows-chocolatey.json"
    )
    
    /**
     * 获取远程包列表的 URL
     */
    fun getRemoteUrl(manager: PackageManagerType, proxyUrl: String? = null): String? {
        val path = remoteFilePaths[manager] ?: return null
        val rawUrl = "$RAW_BASE_URL/$path"
        
        // 如果配置了代理，使用代理 URL
        if (!proxyUrl.isNullOrBlank()) {
            val proxy = proxyUrl.trimEnd('/')
            return "$proxy/$rawUrl"
        }
        
        return rawUrl
    }
    
    /**
     * 从远程拉取包列表
     * @param manager 包管理器类型
     * @param proxyUrl 可选的 GitHub 代理地址（如 https://ghproxy.net 或自定义）
     * @return Result，成功包含包列表，失败包含错误信息
     */
    suspend fun fetchPackagesFromRemote(manager: PackageManagerType, proxyUrl: String? = null): Result<List<PackageInfo>> {
        val url = getRemoteUrl(manager, proxyUrl) ?: return Result.failure(Exception("不支持的包管理器"))
        
        return try {
            val client = HttpClient(CIO) {
                engine {
                    requestTimeout = 10_000 // 10 秒超时
                }
            }
            
            val response: HttpResponse = client.get(url)
            
            if (response.status != HttpStatusCode.OK) {
                client.close()
                return Result.failure(Exception("服务器返回错误: ${response.status.value} ${response.status.description}"))
            }
            
            val body = response.bodyAsText()
            client.close()
            
            // 解析 JSON
            val jsonPackages: List<JsonPackageInfo> = json.decodeFromString(body)
            
            val packages = jsonPackages.map { jsonPkg ->
                PackageInfo(
                    name = jsonPkg.name,
                    description = jsonPkg.description,
                    url = jsonPkg.url,
                    category = jsonPkg.category,
                    isProprietarySoftware = jsonPkg.isProprietarySoftware,
                    licenseUrl = jsonPkg.licenseUrl,
                    eulaUrl = jsonPkg.eulaUrl,
                    licenseType = jsonPkg.licenseType,
                    aptName = jsonPkg.aptName,
                    dnfName = jsonPkg.dnfName,
                    pacmanName = jsonPkg.pacmanName,
                    zypperName = jsonPkg.zypperName,
                    emergeName = jsonPkg.emergeName,
                    nixName = jsonPkg.nixName,
                    wingetName = jsonPkg.wingetName,
                    scoopName = jsonPkg.scoopName,
                    chocolateyName = jsonPkg.chocolateyName
                )
            }
            Result.success(packages)
        } catch (e: Exception) {
            println("PackageListLoader: 拉取远程包列表失败: ${e.message}")
            Result.failure(e)
        }
    }
}
