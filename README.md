<h1 align="center">NOT Toolbox/“非工具箱”</h1>
<h3 align="center">Neural/智能, Open-source/开源, Trans-platform/跨平台</h3>

<div align="center">
    <img width="150" src="images/logo.png" alt="项目Logo">
</div>

<h4 align="center">一款多功能、现代化、AI加持的包管理器前端</h4>

<div align="center">

[![Stars](https://img.shields.io/github/stars/HOE-Team/not-toolbox?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEiIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHBhdGggZD0iTTggLjI1YS43NS43NSAwIDAgMSAuNjczLjQxOGwxLjg4yAzLjgxNSA0LjIxLjYxMmEuNzUuNzUgMCAwIDEgLjQxNiAxLjI3OWwtMy4wNDYgMi45Ny43MTkgNC4xOTJhLjc1MS43NTEgMCAwIDEtMS4wODguNzkxTDggMTIuMzQ3bC0zLjc2NiAxLjk4YS43NS43NSAwIDAgMS0xLjA4OC0uNzlsLjcyLTQuMTk0TC44MTggNi4zNzRhLjc1Ljc1IDAgMCAxIC40MTYtMS4yOGw0LjIxLS42MTFMNy4zMjcuNjY4QS43NS43NSAwIDAgMSA4IC4yNVoiIGZpbGw9IiNlYWM1NGYiLz48L3N2Zz4=&logoSize=auto&label=Stars&labelColor=444444&color=eac54f)](https://github.com/HOE-Team/not-toolbox)
[![LICENSE](https://img.shields.io/github/license/HOE-Team/not-toolbox?style=for-the-badge)](https://github.com/HOE-Team/not-toolbox/blob/main/LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/HOE-Team/not-toolbox?label=Release&logo=github&style=for-the-badge)](https://github.com/HOE-Team/not-toolbox/releases)

</div>

> [!NOTE]
> 我们诚挚的邀请您参与我们的生态建设，欢迎通过Issue、Pull Request协助我们开发NOT Toolbox及[资源仓库](https://github.com/HOE-Team/not-toolbox-resource)，我们将不胜感激。

> [!NOTE]
> Agent和完整包管理功能已经进入测试阶段！点击[此处](https://github.com/HOE-Team/not-toolbox/releases/latest)了解最新的功能更改

## 📑 目录

- [✨ 特性](#-特性)
- [🖥️ 系统要求](#️-系统要求)
- [🚀 安装和运行](#-安装和运行)
- [📦 JAR分发说明](#-jar分发说明)
- [🔐 发布签名与校验](#-发布签名与校验)
- [🤝 如何贡献](#-如何贡献)
- [📁 项目结构](#-项目结构)
- [🔗 技术栈](#-技术栈)
- [🔨 开发人员内容](#-开发人员内容)
- [📜 版权与许可证](#-版权与许可证)


## ✨ 特性

* **Linux\Win跨平台**：支持Windows 10+与主流Linux发行版
* **包管理器集成**：自动检测系统包管理器（APT、DNF、PACMAN、ZYPPER、SCOOP、WINGET等）
* **终端功能集成**: 集成终端模拟器，支持多会话管理
* **现代UI**：采用 Material Design 3 设计风格
* **极佳可迁移性**：使用 Kotlin + Compose Multiplatform 跨平台框架
* **代码透明**：完全开源，可供任何人审计
* **去本地化**：不打包任何第三方应用，完全由云端+包管理器获取

## 🖥️ 系统要求

### **操作系统**：
- **Linux**  
推荐Arch、Debian/Ubuntu、Fedora、openSUSE等主流发行版，Linux Kernel 4.x+
- **Windows**  
Windows 10 1507+（内部版本 10240），需要至少存在一种包管理器。

* **Java环境**：Java 21 或更高版本
* **网络**：需要稳定网络连接（用于包管理器操作）
* **权限**：Linux平台上需要sudo权限，否则将无法使用大部分功能，在Windows需要用户同意UAC（由包管理器发起，NOT Toolbox程序不提升权限）

## 🚀 安装和运行
### 使用启动器(Windows)或启动脚本(Linux)
**Windows**
1. 从 [Releases](https://github.com/HOE-Team/not-toolbox/releases) 下载`NTB.exe`
2. 运行并安装（若未安装Java21会自动跳转至TUNA下载JRE 21安装包，安装即可）
3. 点击快捷方式通过启动器快速启动NOT Toolbox

**Linux**
1. 从 [Releases](https://github.com/HOE-Team/not-toolbox/releases) 下载`NTB.tar.gz`
2. 使用 
   ```bash
   tar -zxvf NTB.tar.gz
   ```
   解压压缩包
3. `cd NTB` 进入解压后的目录
4. 执行：
   ```bash
   sh linux-startup.sh
   ```

### 通过java -jar运行JAR文件
1. 确保已安装Java 21或更高版本
2. 从本项目的 [Releases](https://github.com/HOE-Team/not-toolbox/releases) 页面下载 `NTB-all.jar`
3. 运行应用程序：
   ```bash
   java -jar NTB-all.jar
   ```

### 从源代码构建
```bash
# 克隆仓库
git clone https://github.com/HOE-Team/not-toolbox.git
cd not-toolbox

# 默认构建跨平台 JAR（包含 Windows/Linux 的 x64 与 ARM64 Skiko 原生库）
./gradlew fatJar
# 或构建一个经过 ProGuard 压缩的包
./gradlew shrinkJar

# 仅 Windows：只包含 Windows x64/ARM64 的 Skiko 原生库
./gradlew fatJar -PntbPlatform=windows
./gradlew shrinkJar -PntbPlatform=windows

# 仅 Linux：只包含 Linux x64/ARM64 的 Skiko 原生库
./gradlew fatJar -PntbPlatform=linux
./gradlew shrinkJar -PntbPlatform=linux

# 运行应用程序
java -jar build/libs/NTB-all.jar
java -jar build/libs/NTB-shrunk.jar

# 平台专用产物
java -jar build/libs/NTB-windows.jar
java -jar build/libs/NTB-linux.jar
java -jar build/libs/NTB-windows-shrunk.jar
java -jar build/libs/NTB-linux-shrunk.jar
```

构建产物：

| 命令 | JAR |
|---|---|
| 默认 / `-PntbPlatform=all` | `NTB-all.jar`、`NTB-shrunk.jar` |
| `-PntbPlatform=windows` | `NTB-windows.jar`、`NTB-windows-shrunk.jar` |
| `-PntbPlatform=linux` | `NTB-linux.jar`、`NTB-linux-shrunk.jar` |

## 📦 JAR分发说明

本项目使用JAR分发，具有以下优势：
- **跨平台**：可在任何支持Java的Linux与Windows系统上运行
- **便携性**：单个JAR文件包含所有依赖
- **易于分发**：无需复杂的安装过程

## 🔐 发布签名与校验

正式发布使用 GPG 对 `binary_sha256.sha256` 做分离签名。启动器只做基础 SHA256 完整性校验；GPG 签名用于确认发布来源，需要用户主动验证。

**发布公钥指纹：**

```text
4900 8F79 0CDB B34E C1D2 6A82 CE50 A883 DE74 5329
```

公钥随安装包分发，位于 `res/GoldenHoe_pub.asc`；应用资源中也包含一份 `gpg/GoldenHoe_pub.asc`。

### 发布的文件

| 文件 | 说明 |
|---|---|
| `binary/NTB-shrunk.jar` | 主程序 |
| `binary_sha256.sha256` | 主程序 SHA256 |
| `binary_sha256.sha256.asc` | 上述校验文件的 GPG 签名 |
| `res/GoldenHoe_pub.asc` | 发布公钥 |

### 用户校验

Windows（安装目录）：

```powershell
gpg --import .\res\GoldenHoe_pub.asc
gpg --verify .\binary_sha256.sha256.asc .\binary_sha256.sha256
(Get-FileHash .\binary\NTB-shrunk.jar -Algorithm SHA256).Hash.ToLower()
```

Linux（解压目录）：

```bash
gpg --import ./res/GoldenHoe_pub.asc
gpg --verify ./binary_sha256.sha256.asc ./binary_sha256.sha256
sha256sum ./binary/NTB-shrunk.jar
```

### 启动器校验

Windows 启动器会读取 `binary_sha256.sha256` 并核对主程序 SHA256。校验失败会阻止启动，用于发现下载损坏或打包错误。

开发构建可以跳过：

```powershell
NTB.exe --skip-integrity
```

或设置环境变量 `NTB_SKIP_INTEGRITY=1`。

> [!NOTE]
> SHA256 只能发现文件损坏/打包不一致，不能证明发布来源。发布来源由 GPG 签名保证，但它需要用户核对公钥指纹；如果公钥、签名、程序被一起替换，包内校验无法发现。

### 维护者签名

以下命令跨平台，不需要仓库里的 PowerShell 辅助脚本。先进入 `startup` 目录。

1. 生成与当前 JAR 匹配的哈希：

   Windows PowerShell：
   ```powershell
   $h = (Get-FileHash ..\build\libs\NTB-shrunk.jar -Algorithm SHA256).Hash.ToLower()
   Set-Content -Path binary_sha256.sha256 -Value $h -NoNewline -Encoding ascii
   ```

   Linux：
   ```bash
   sha256sum ../build/libs/NTB-shrunk.jar > binary_sha256.sha256
   ```

   macOS：
   ```bash
   shasum -a 256 ../build/libs/NTB-shrunk.jar > binary_sha256.sha256
   ```

2. 使用本机 GPG keyring 中的私钥签名：
   ```bash
   gpg --local-user 49008F790CDBB34EC1D26A82CE50A883DE745329 \
       --armor --detach-sign \
       --output binary_sha256.sha256.asc \
       binary_sha256.sha256
   ```

3. 发布时同时上传 `binary_sha256.sha256.asc` 与安装包内的 `res/GoldenHoe_pub.asc`（仓库路径为 `startup/res-py/GoldenHoe_pub.asc`）。

私钥不会进入仓库，也不应复制到项目目录；`/gpgkeys` 仅用于本机导入。

## 🤝 如何贡献
欢迎向我们发送 Issue 或 Pull Request。

### 创建一个 Pull Request

1. **创建分支**
   ```bash
   git checkout -b feat/agoodfeat
   ```

2. **提交代码**
   ```bash
   git add .
   git commit -m "[feat] A good feat."
   ```

3. **推送分支**
   ```bash
   git push origin feat/agoodfeat
   ```

4. **创建 PR**
   - 前往 GitHub 仓库
   - 点击 "New Pull Request"
   - 选择你的分支
   - 填写标题和描述
   - 提交

### 提交信息格式
```
类型: 描述

[feat]    - 新功能(或[feature])
[fix]     - 修复bug
[docs]    - 文档更新
[style]   - 代码格式
[refactor]- 代码重构
[chore]   - 杂项
```

### 示例
```
git commit -m "[feat] 添加新功能"
git commit -m "[fix] 修复已知问题"
```



## 📁 项目结构

```
not-toolbox/
├── src/main/kotlin/         # Kotlin源代码
│   ├── components/          # 可复用组件
│   ├── screens/             # 主要界面
│   ├── utils/               # 工具类（包管理器、系统信息等）
│   ├── theme/               # 主题配置
│   └── config/              # 配置类
├── LICENSES/                # 非主项目的授权信息或许可证
│   ├── LICENSE-MIT-NNETB    # NNETB(上游)许可证
│   ├── LICENSE-GPLV3-       # NNETB For Linux(上游)许可证
|   |     NNETB-FOR-LINUX
│   ├── LICENSE-MIT-NTB-     # NTB Launcher(启动器)许可证
|   |     LAUNCHER
│   └── LICENSE-MIT-NTB-     # NTB LPT(打包程序)许可证
|         LAUNCHER-PACK-TOOLS
├── startup/                 # 启动脚本与启动器
│   ├── res-py               # 打包用资源文件（含发布公钥）
│   └── pyi                  # 打包操作目录
├── src/main/resources/      # 资源文件
│   └── gpg/                 # 发布公钥与指纹信息
├── build.gradle.kts         # Gradle构建配置
└── README.md                # 项目说明
```

## 🔗 技术栈及许可信息

| 组件 | 用途                        | 开源协议           |
|------|-----------------------------|--------------------|
| [Kotlin](https://kotlinlang.org/) | 主要编程语言                | Apache 2.0         |
| [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) | 跨平台声明式 UI 框架        | Apache 2.0         |
| [OSHI](https://github.com/oshi/oshi) | 操作系统和硬件信息获取      | MIT                |
| [Ktor](https://ktor.io) | 异步网络框架                | Apache 2.0         |
| [Gradle(Kotlin DSL)](https://gradle.org/) | 编译工具                    | GPL 2.0            |
| [ProGuard](https://github.com/Guardsquare/proguard) | 混淆与压缩工具 | GPL2.0 |            |
| [Skiko](https://github.com/JetBrains/skiko) | 渲染库                      | Apache 2.0         |
| [Python](https://python.org) | Windows启动器使用语言       | PSF 2.0            |
| [PyInstaller](https://pyinstaller.org) | 启动器打包工具              | GPL 2.0            |
| [Inno Setup](https://jrsoftware.org/isdl.php) | Windows平台安装程序打包工具 | Inno Setup License |
| [Multiplatform Markdown Render](https://github.com/mikepenz/multiplatform-markdown-renderer) | 跨平台Markdown渲染库        | Apache 2.0         |
## 📬 联系方式

你可以通过我们的电子邮箱 hoe_software_team@outlook.com 发送邮件联系我们，或者加入我们的团队QQ群：1081639867。

如果你觉得本项目对你有帮助，欢迎给个 Star 支持！

## 🔨 开发人员内容
### Debug常量
将此变量改为“true”启用调试，程序将使用本地存储的Packages Lists而不是从远程拉取。  
> [!CAUTION]
> 非调试用途不要修改变量，因为本地调试包列表不是最新的且质量不佳，Fork项目也可能遭受针对此的供应链攻击。

`MainApp.kt:`
```kotlin
// 编译时常量：true=启用本地DEBUG包列表，false=从远程拉取
const val IS_DEBUG = false
```

### GPG 签名（开发者）

<details>
<summary>查看 GPG 签名与验证事宜</summary>

正式发布由维护者使用 GPG 私钥签名，普通开发构建不需要私钥，也不需要 GPG。
签名需要本机安装 GnuPG（`gpg` 在 PATH 中，或安装在默认 GnuPG 目录）。

**维护者签名（在 `startup` 目录执行，跨平台）：**

1. 确认私钥已经在当前用户 keyring 中（不会输出私钥内容）：
   ```bash
   gpg --list-secret-keys 49008F790CDBB34EC1D26A82CE50A883DE745329
   ```

2. 生成与当前 JAR 匹配的发布哈希。

   Windows PowerShell：
   ```powershell
   $h = (Get-FileHash ..\build\libs\NTB-shrunk.jar -Algorithm SHA256).Hash.ToLower()
   Set-Content -Path binary_sha256.sha256 -Value $h -NoNewline -Encoding ascii
   ```

   Linux：
   ```bash
   sha256sum ../build/libs/NTB-shrunk.jar > binary_sha256.sha256
   ```

   macOS：
   ```bash
   shasum -a 256 ../build/libs/NTB-shrunk.jar > binary_sha256.sha256
   ```

3. 用 keyring 中的私钥签名：
   ```bash
   gpg --local-user 49008F790CDBB34EC1D26A82CE50A883DE745329 \
       --armor --detach-sign \
       --output binary_sha256.sha256.asc \
       binary_sha256.sha256
   ```

签名产物为 `startup/binary_sha256.sha256.asc`。

**验证发布：**

```bash
gpg --verify binary_sha256.sha256.asc binary_sha256.sha256
```

再核对 JAR 哈希：

- Windows PowerShell：`(Get-FileHash ..\build\libs\NTB-shrunk.jar -Algorithm SHA256).Hash.ToLower()`
- Linux：`sha256sum ../build/libs/NTB-shrunk.jar`
- macOS：`shasum -a 256 ../build/libs/NTB-shrunk.jar`

> [!NOTE]
> 仓库不跟踪 `.ps1` 辅助脚本；上面的 GPG 命令是跨平台的，Windows、Linux、macOS 都可以直接执行。

**Fork 仓库 / 分支开发者：**

- **不要使用维护者私钥，也不要向维护者索要私钥。** Fork 应该生成或使用自己的 GPG key。
- 只是开发/测试、不对外发布时，不需要签名，正常执行 `gradlew build` 即可。
- 如果 Fork 要对外发布，需要把仓库里的“官方公钥”替换成自己的：

  1. 生成或导入自己的 GPG key，并记下指纹：
     ```powershell
     gpg --full-generate-key
     gpg --list-secret-keys --keyid-format=long
     ```
  2. 导出自己的公钥：
     ```powershell
     gpg --armor --output "startup\res-py\<你的名字>_pub.asc" --export <你的指纹>
     ```
  3. 同步替换这些位置：
     - `startup/res-py/GoldenHoe_pub.asc`
     - `src/main/resources/gpg/GoldenHoe_pub.asc`
     - `startup/res-py/FINGERPRINT.txt`
     - `src/main/resources/gpg/FINGERPRINT.txt`
     - `src/main/kotlin/utils/ReleaseKey.kt` 中的 `FINGERPRINT` / `PUBLIC_KEY_RESOURCE` / `PUBLIC_KEY_PACKAGE_PATH` / `PUBLIC_KEY_URL`
     - 如果改了文件名，还要同步 `pack-exe.iss`、`pack-linux-tar.bat` 中的公钥路径
  4. 构建 JAR，生成哈希，然后用自己的 key 签名：
     ```bash
     cd startup
     # 哈希生成见上文（Windows / Linux / macOS 三种命令）
     gpg --local-user <你的指纹> \
         --armor --detach-sign \
         --output binary_sha256.sha256.asc \
         binary_sha256.sha256
     ```
  5. 验证自己的发布（仍在上一步的 `startup` 目录）：
     ```bash
     gpg --import res-py/<你的名字>_pub.asc
     gpg --verify binary_sha256.sha256.asc binary_sha256.sha256
     ```
  6. 在 README、About 和 Release 说明中明确这是 **Fork 构建**，不要使用官方名义发布。

- 如果在官方仓库的普通分支上开发，不需要签名；正式发布由维护者完成。测试构建如果要自签，请使用自己的 key，不要覆盖或提交官方公钥。
- 私钥只放在本机 keyring 或 CI Secret，不要提交到仓库；`/gpgkeys` 已在 `.gitignore` 中。

**注意：**

- GPG 签名是分离签名，不会让 Java 自动拒绝被修改的 JAR。
- 启动器只做 SHA256 基础校验；GPG 签名需要用户主动验证。
- 密钥指纹必须通过官网、README 或 Release 页面核对。

</details>

### Packages Lists JSON RAW 源仓库
参见 [HOE-Team/not-toolbox-resource](https://github.com/HOE-Team/not-toolbox-resource)

### Pack Tool 及启动器
参见本仓库的 `startup` 文件夹中的 [适用于 NOT Toolbox 的 Windows 平台打包工具、启动器与 Linux 平台启动脚本](./startup/ABOUT-LAUNCHER-AND-STARTUP.md) 文档。

---

## 📜 版权与许可证

版权所有 © 2026 HOE Team。保留部分权利。

本项目（NOT Toolbox）基于 **[GNU GPL v3 许可证](LICENSE)** 开源，是独立于 [NNETB](https://github.com/HOE-Team/NNETB) （[MIT 许可证](LICENSE-MIT-NNETB)）和 [NNETB-For-Linux](https://github.com/HOE-Team/NNETB-For-Linux) （[GPLv3 许可证](LICENSE-GPLV3-NNETB-FOR-LINUX)）的跨平台下游分支。

启动脚本及启动器授权信息参见 [适用于 NOT Toolbox 的 Windows 平台打包工具、启动器与 Linux 平台启动脚本](./startup/ABOUT-LAUNCHER-AND-STARTUP.md) 文档。

> [!NOTE]
> 这份许可证(GNU GPL v3)意味着：
> 
> 你可以自由使用、修改、复制、分发这个项目代码，无论是在个人项目还是商业项目中。 
> 
> 如果你修改并重新发布这个代码，你必须以 GPL v3 许可证公开你的修改后的源代码（即“传染性”开源）。 
> 
> 你可以用它来开发商业软件并销售，但你必须同时以 GPL v3 许可证提供你修改后的完整源代码。 
> 
> 作者不提供任何保证，如果使用该软件导致任何问题，你需要自己承担风险。 

> [!WARNING]
> ### 著作权声明
> NOT Toolbox 的徽标为 HOE Team 所有，受法律保护。未经明确书面授权，不得用于商业用途或进行修改后使用。
