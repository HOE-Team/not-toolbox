// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.InputStreamReader

/**
 * 包管理器类型
 */
enum class PackageManagerType {
    // Linux 包管理器
    APT,      // Debian/Ubuntu
    DNF,      // Fedora/RHEL
    PACMAN,   // Arch/Manjaro
    ZYPPER,   // openSUSE
    EMERGE,   // Gentoo
    NIX,      // NixOS
    // Windows 包管理器
    WINGET,   // Windows Package Manager
    SCOOP,    // Scoop
    CHOCOLATEY, // Chocolatey
    UNKNOWN   // 未知或未安装
}

/**
 * JSON 包信息类（用于反序列化）
 * 从 packages-<platform>-<pm>.json 文件读取
 */
@Serializable
data class JsonPackageInfo(
    val name: String,
    val description: String? = null,
    val url: String? = null,
    val category: String? = null,
    val isProprietarySoftware: Boolean = false,
    val licenseUrl: String? = null,
    val eulaUrl: String? = null,
    val licenseType: String? = null,
    val aptName: String? = null,
    val dnfName: String? = null,
    val pacmanName: String? = null,
    val zypperName: String? = null,
    val emergeName: String? = null,
    val nixName: String? = null,
    val wingetName: String? = null,
    val scoopName: String? = null,
    val chocolateyName: String? = null
)

/**
 * 包信息类，用于存储不同包管理器对应的包名
 */
data class PackageInfo(
    val name: String,           // 包名
    val description: String? = null,  // 描述
    val url: String? = null,         // 官网URL
    val category: String? = null,    // 分类
    val isProprietarySoftware: Boolean = false,  // 是否专有软件
    val licenseUrl: String? = null,  // 许可证URL
    val eulaUrl: String? = null,     // EULA URL
    val licenseType: String? = null, // 许可证类型
    val aptName: String? = null,      // APT包名
    val dnfName: String? = null,      // DNF包名
    val pacmanName: String? = null,   // Pacman包名
    val zypperName: String? = null,   // Zypper包名
    val emergeName: String? = null,   // Emerge包名
    val nixName: String? = null,      // Nix包名
    val wingetName: String? = null,   // Winget包名
    val scoopName: String? = null,    // Scoop包名
    val chocolateyName: String? = null // Chocolatey包名
) {
    /**
     * 根据包管理器类型获取对应的包名
     */
    fun getPackageNameForManager(manager: PackageManagerType): String? {
        return when (manager) {
            PackageManagerType.APT -> aptName ?: name
            PackageManagerType.DNF -> dnfName ?: name
            PackageManagerType.PACMAN -> pacmanName ?: name
            PackageManagerType.ZYPPER -> zypperName ?: name
            PackageManagerType.EMERGE -> emergeName ?: name
            PackageManagerType.NIX -> nixName ?: name
            PackageManagerType.WINGET -> wingetName ?: name
            PackageManagerType.SCOOP -> scoopName ?: name
            PackageManagerType.CHOCOLATEY -> chocolateyName ?: name
            PackageManagerType.UNKNOWN -> null
        }
    }
}

/**
 * 包管理器工具类
 */
object PackageManagerUtils {
    /**
     * 检测当前系统的包管理器
     */
    fun detectPackageManager(): PackageManagerType {
        return try {
            val os = System.getProperty("os.name").lowercase()
            // macOS 不支持 Linux/Windows 包管理器，直接返回 UNKNOWN
            if (os.contains("mac")) {
                return PackageManagerType.UNKNOWN
            }
            
            if (os.contains("windows")) {
                // Windows: 检测 winget、scoop、chocolatey
                when {
                    commandExistsWindows("winget") -> PackageManagerType.WINGET
                    commandExistsWindows("scoop") -> PackageManagerType.SCOOP
                    commandExistsWindows("choco") -> PackageManagerType.CHOCOLATEY
                    else -> PackageManagerType.UNKNOWN
                }
            } else {
                // Linux: 检测各种 Linux 包管理器
                when {
                    commandExists("apt-get") -> PackageManagerType.APT
                    commandExists("dnf") -> PackageManagerType.DNF
                    commandExists("yum") -> PackageManagerType.DNF  // yum是DNF的前身
                    commandExists("pacman") -> PackageManagerType.PACMAN
                    commandExists("zypper") -> PackageManagerType.ZYPPER
                    commandExists("emerge") -> PackageManagerType.EMERGE
                    commandExists("nix") -> PackageManagerType.NIX
                    else -> PackageManagerType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            PackageManagerType.UNKNOWN
        }
    }
    
    /**
     * 获取当前平台可用的包管理器列表（已安装的）
     */
    fun getAvailablePackageManagers(): List<PackageManagerType> {
        val os = System.getProperty("os.name").lowercase()
        val available = mutableListOf<PackageManagerType>()
        
        try {
            if (os.contains("windows")) {
                // Windows 包管理器
                if (commandExistsWindows("winget")) available.add(PackageManagerType.WINGET)
                if (commandExistsWindows("scoop")) available.add(PackageManagerType.SCOOP)
                if (commandExistsWindows("choco")) available.add(PackageManagerType.CHOCOLATEY)
            } else if (os.contains("linux")) {
                // Linux 包管理器
                if (commandExists("apt-get")) available.add(PackageManagerType.APT)
                if (commandExists("dnf") || commandExists("yum")) available.add(PackageManagerType.DNF)
                if (commandExists("pacman")) available.add(PackageManagerType.PACMAN)
                if (commandExists("zypper")) available.add(PackageManagerType.ZYPPER)
                if (commandExists("emerge")) available.add(PackageManagerType.EMERGE)
                if (commandExists("nix")) available.add(PackageManagerType.NIX)
            }
        } catch (_: Exception) { }
        
        return available
    }
    
    /**
     * 获取当前平台所有可能的包管理器列表（无论是否安装）
     */
    fun getAllPlatformPackageManagers(): List<PackageManagerType> {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("windows")) {
            listOf(PackageManagerType.WINGET, PackageManagerType.SCOOP, PackageManagerType.CHOCOLATEY)
        } else {
            listOf(PackageManagerType.APT, PackageManagerType.DNF, PackageManagerType.PACMAN, 
                   PackageManagerType.ZYPPER, PackageManagerType.EMERGE, PackageManagerType.NIX)
        }
    }
    
    /**
     * 检查命令是否存在（Linux/macOS）
     */
    private fun commandExists(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v $command"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查命令是否存在（Windows）
     */
    private fun commandExistsWindows(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", "where $command >nul 2>&1"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取包管理器的安装命令（带 --noconfirm / -y 自动确认）
     */
    fun getInstallCommand(manager: PackageManagerType, packageName: String): String? {
        return when (manager) {
            PackageManagerType.APT -> "sudo apt-get install -y $packageName"
            PackageManagerType.DNF -> "sudo dnf install -y $packageName"
            PackageManagerType.PACMAN -> "sudo pacman -S --noconfirm $packageName"
            PackageManagerType.ZYPPER -> "sudo zypper install -y $packageName"
            PackageManagerType.EMERGE -> "sudo emerge --ask=n $packageName"
            PackageManagerType.NIX -> "nix-env -i $packageName"
            PackageManagerType.WINGET -> "winget install --silent $packageName"
            PackageManagerType.SCOOP -> "scoop install $packageName"
            PackageManagerType.CHOCOLATEY -> "choco install -y $packageName"
            PackageManagerType.UNKNOWN -> null
        }
    }
    
    /**
     * 获取包管理器的更新命令（带 --noconfirm / -y 自动确认）
     */
    fun getUpdateCommand(manager: PackageManagerType): String? {
        return when (manager) {
            PackageManagerType.APT -> "sudo apt-get update && sudo apt-get upgrade -y"
            PackageManagerType.DNF -> "sudo dnf update -y"
            PackageManagerType.PACMAN -> "sudo pacman -Syu --noconfirm"
            PackageManagerType.ZYPPER -> "sudo zypper update -y"
            PackageManagerType.EMERGE -> "sudo emerge --sync && sudo emerge -u world --ask=n"
            PackageManagerType.NIX -> "nix-channel --update && nix-env -u"
            PackageManagerType.WINGET -> "winget upgrade --all --silent"
            PackageManagerType.SCOOP -> "scoop update *"
            PackageManagerType.CHOCOLATEY -> "choco upgrade all -y"
            PackageManagerType.UNKNOWN -> null
        }
    }
    
    /**
     * 获取包管理器的搜索命令
     */
    fun getSearchCommand(manager: PackageManagerType, query: String): String? {
        return when (manager) {
            PackageManagerType.APT -> "apt-cache search $query"
            PackageManagerType.DNF -> "dnf search $query"
            PackageManagerType.PACMAN -> "pacman -Ss $query"
            PackageManagerType.ZYPPER -> "zypper search $query"
            PackageManagerType.EMERGE -> "emerge --search $query"
            PackageManagerType.NIX -> "nix-env -qaP | grep $query"
            PackageManagerType.WINGET -> "winget search $query"
            PackageManagerType.SCOOP -> "scoop search $query"
            PackageManagerType.CHOCOLATEY -> "choco search $query"
            PackageManagerType.UNKNOWN -> null
        }
    }
    
    /**
     * 获取包管理器的显示名称
     */
    fun getPackageManagerDisplayName(manager: PackageManagerType): String {
        return when (manager) {
            PackageManagerType.APT -> "APT (Debian/Ubuntu)"
            PackageManagerType.DNF -> "DNF (Fedora/RHEL)"
            PackageManagerType.PACMAN -> "Pacman (Arch)"
            PackageManagerType.ZYPPER -> "Zypper (openSUSE)"
            PackageManagerType.EMERGE -> "Emerge (Gentoo)"
            PackageManagerType.NIX -> "Nix (NixOS)"
            PackageManagerType.WINGET -> "Winget (Windows)"
            PackageManagerType.SCOOP -> "Scoop (Windows)"
            PackageManagerType.CHOCOLATEY -> "Chocolatey (Windows)"
            PackageManagerType.UNKNOWN -> "选择包管理器"
        }
    }
    
    /**
     * 获取 JSON 包列表文件的路径
     * 格式: packages-<platform>-<pm>.json
     */
    fun getPackageListFileName(manager: PackageManagerType): String? {
        val os = System.getProperty("os.name").lowercase()
        val platform = when {
            os.contains("windows") -> "windows"
            os.contains("linux") -> "linux"
            else -> return null
        }
        
        val pmName = when (manager) {
            PackageManagerType.APT -> "apt"
            PackageManagerType.DNF -> "dnf"
            PackageManagerType.PACMAN -> "pacman"
            PackageManagerType.ZYPPER -> "zypper"
            PackageManagerType.EMERGE -> "emerge"
            PackageManagerType.NIX -> "nix"
            PackageManagerType.WINGET -> "winget"
            PackageManagerType.SCOOP -> "scoop"
            PackageManagerType.CHOCOLATEY -> "chocolatey"
            PackageManagerType.UNKNOWN -> return null
        }
        
        return "packages/packages-$platform-$pmName.json"
    }
}

/**
 * 常用工具的包名映射
 * 从外部 JSON 文件加载包列表
 */
object CommonPackages {
    private val json = Json { ignoreUnknownKeys = true }
    
    // 缓存已加载的包信息（name.lowercase() -> PackageInfo）
    private val packageCache = mutableMapOf<String, PackageInfo>()
    
    /**
     * 加载包列表
     * DEBUG 模式：从本地 packages-<platf>-<pm>.json 文件加载
     * 非 DEBUG 模式：从 GitHub RAW 远程拉取
     */
    private fun loadPackagesFromJson(manager: PackageManagerType, isDebug: Boolean = true): List<PackageInfo> {
        // 非调试模式：从远程拉取
        if (!isDebug) {
            println("DEBUG: 调试模式已关闭，从远程拉取包列表")
            return emptyList() // 实际由 ToolsScreen 通过协程调用 PackageListLoader
        }
        
        val fileName = PackageManagerUtils.getPackageListFileName(manager) ?: return emptyList()
        
        return try {
            val inputStream = CommonPackages::class.java.classLoader.getResourceAsStream(fileName)
            if (inputStream == null) {
                println("DEBUG: 未找到包列表文件: $fileName")
                return emptyList()
            }
            
            val reader = InputStreamReader(inputStream, "UTF-8")
            val jsonText = reader.readText()
            reader.close()
            
            val jsonPackages: List<JsonPackageInfo> = json.decodeFromString(jsonText)
            
            jsonPackages.map { jsonPkg ->
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
        } catch (e: Exception) {
            println("DEBUG: 加载包列表文件失败: $fileName - ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 加载所有平台的包列表并合并到缓存
     */
    private fun ensurePackagesLoaded() {
        if (packageCache.isNotEmpty()) return
        
        // 加载所有可能的包列表文件
        val allManagers = listOf(
            PackageManagerType.APT,
            PackageManagerType.DNF,
            PackageManagerType.PACMAN,
            PackageManagerType.ZYPPER,
            PackageManagerType.EMERGE,
            PackageManagerType.NIX,
            PackageManagerType.WINGET,
            PackageManagerType.SCOOP,
            PackageManagerType.CHOCOLATEY
        )
        
        for (manager in allManagers) {
            val packages = loadPackagesFromJson(manager)
            for (pkg in packages) {
                val existing = packageCache[pkg.name.lowercase()]
                if (existing == null) {
                    packageCache[pkg.name.lowercase()] = pkg
                } else {
                    // 合并包信息（保留已有的字段，补充新字段）
                    packageCache[pkg.name.lowercase()] = PackageInfo(
                        name = existing.name,
                        description = existing.description ?: pkg.description,
                        url = existing.url ?: pkg.url,
                        category = existing.category ?: pkg.category,
                        isProprietarySoftware = existing.isProprietarySoftware || pkg.isProprietarySoftware,
                        licenseUrl = existing.licenseUrl ?: pkg.licenseUrl,
                        eulaUrl = existing.eulaUrl ?: pkg.eulaUrl,
                        licenseType = existing.licenseType ?: pkg.licenseType,
                        aptName = existing.aptName ?: pkg.aptName,
                        dnfName = existing.dnfName ?: pkg.dnfName,
                        pacmanName = existing.pacmanName ?: pkg.pacmanName,
                        zypperName = existing.zypperName ?: pkg.zypperName,
                        emergeName = existing.emergeName ?: pkg.emergeName,
                        nixName = existing.nixName ?: pkg.nixName,
                        wingetName = existing.wingetName ?: pkg.wingetName,
                        scoopName = existing.scoopName ?: pkg.scoopName,
                        chocolateyName = existing.chocolateyName ?: pkg.chocolateyName
                    )
                }
            }
        }
        
        println("DEBUG: 已加载 ${packageCache.size} 个包信息")
    }
    
    /**
     * 根据工具名称获取包信息
     */
    fun getPackageInfo(toolName: String): PackageInfo? {
        ensurePackagesLoaded()
        return packageCache[toolName.lowercase()]
    }
    
    /**
     * 获取所有已加载的包信息
     */
    fun getAllPackages(): List<PackageInfo> {
        ensurePackagesLoaded()
        return packageCache.values.toList()
    }
    
    /**
     * 获取按分类分组的包列表
     */
    fun getPackagesByCategory(): Map<String, List<PackageInfo>> {
        ensurePackagesLoaded()
        return packageCache.values
            .filter { it.category != null }
            .groupBy { it.category!! }
    }
    
    /**
     * 获取所有分类名称（保持顺序）
     */
    fun getCategories(): List<String> {
        ensurePackagesLoaded()
        // 使用 linkedSetOf 保持插入顺序
        val categories = linkedSetOf<String>()
        // 按 name 排序以保持一致的顺序
        packageCache.values
            .sortedBy { it.name }
            .forEach { pkg ->
                if (pkg.category != null) {
                    categories.add(pkg.category)
                }
            }
        return categories.toList()
    }
    
    /**
     * 根据当前平台和包管理器加载对应的包列表
     * 只加载当前平台/包管理器对应的 JSON 文件
     */
    fun loadPackagesForCurrentPlatform(isDebug: Boolean = true): List<PackageInfo> {
        val manager = PackageManagerUtils.detectPackageManager()
        if (manager == PackageManagerType.UNKNOWN) return emptyList()
        
        return loadPackagesFromJson(manager, isDebug)
    }
    
    /**
     * 根据指定的包管理器加载对应的包列表
     */
    fun loadPackagesForManager(manager: PackageManagerType, isDebug: Boolean = true): List<PackageInfo> {
        if (manager == PackageManagerType.UNKNOWN) return emptyList()
        return loadPackagesFromJson(manager, isDebug)
    }
    
    /**
     * 重新加载包列表（用于调试）
     */
    fun reloadPackages() {
        packageCache.clear()
        ensurePackagesLoaded()
    }
}
