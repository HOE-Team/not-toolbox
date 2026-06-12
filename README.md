<h1 align="center">NOT Toolbox</h1>
<h2 align="center">Not nOt noT Toolbox(?)</h2>

<div align="center">
    <img width="150" src="images/logo.png" alt="项目Logo">
</div>

<h4 align="center">一款跨平台，功能强大，去本地化的工具箱</h4>

<div align="center">

[![Stars](https://img.shields.io/github/stars/HOE-Team/not-toolbox?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEiIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHBhdGggZD0iTTggLjI1YS43NS43NSAwIDAgMSAuNjczLjQxOGwxLjg4yAzLjgxNSA0LjIxLjYxMmEuNzUuNzUgMCAwIDEgLjQxNiAxLjI3OWwtMy4wNDYgMi45Ny43MTkgNC4xOTJhLjc1MS43NTEgMCAwIDEtMS4wODguNzkxTDggMTIuMzQ3bC0zLjc2NiAxLjk4YS43NS43NSAwIDAgMS0xLjA4OC0uNzlsLjcyLTQuMTk0TC44MTggNi4zNzRhLjc1Ljc1IDAgMCAxIC40MTYtMS4yOGw0LjIxLS42MTFMNy4zMjcuNjY4QS43NS43NSAwIDAgMSA4IC4yNVoiIGZpbGw9IiNlYWM1NGYiLz48L3N2Zz4=&logoSize=auto&label=Stars&labelColor=444444&color=eac54f)](https://github.com/HOE-Team/not-toolbox)
[![LICENSE](https://img.shields.io/github/license/HOE-Team/not-toolbox?style=for-the-badge)](https://github.com/HOE-Team/not-toolbox/blob/main/LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/HOE-Team/not-toolbox?label=Release&logo=github&style=for-the-badge)](https://github.com/HOE-Team/not-toolbox/releases)

</div>


## 📑 目录

- [✨ 特性](#-特性)
- [🖥️ 系统要求](#️-系统要求)
- [🚀 安装和运行](#-安装和运行)
- [📦 JAR分发说明](#-jar分发说明)
- [🤝 如何贡献](#-如何贡献)
- [📁 项目结构](#-项目结构)
- [🔗 技术栈](#-技术栈)
- [📜 版权与许可证](#-版权与许可证)
- [🔨 开发人员内容](#-开发人员内容)


## ✨ 特性

* **Linux\Win跨平台**：支持Windows 10+与主流Linux发行版
* **包管理器集成**：自动检测系统包管理器（APT、DNF、PACMAN、ZYPPER、SCOOP、WINGET等）
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
* **权限**：Linux平台上部分功能（如包管理器安装）需要sudo权限，在Windows需要用户同意UAC（由包管理器发起，NOT Toolbox程序不提升权限）

## 🚀 安装和运行

### 方法1：直接运行JAR文件
1. 确保已安装Java 21或更高版本
2. 从本项目的 [Releases](https://github.com/HOE-Team/not-toolbox/releases) 页面下载 `NTB-all.jar`
3. 运行应用程序：
   ```bash
   java -jar NTB-all.jar
   ```

### 方法2：从源代码构建
```bash
# 克隆仓库
git clone https://github.com/HOE-Team/not-toolbox.git
cd not-toolbox

# 构建可执行JAR
./gradlew fatJar

# 运行应用程序
java -jar build/libs/NTB-all.jar
```

## 📦 JAR分发说明

本项目使用JAR分发，具有以下优势：
- **跨平台**：可在任何支持Java的Linux与Windows系统上运行
- **便携性**：单个JAR文件包含所有依赖
- **易于分发**：无需复杂的安装过程

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
├── src/main/resources/      # 资源文件
├── build.gradle.kts         # Gradle构建配置
├── run.sh                   # Linux启动脚本
└── README.md                # 项目说明
```

## 🔗 技术栈

| 组件 | 用途 | 开源协议 |
|------|------|------|
| [Kotlin](https://kotlinlang.org/) | 主要编程语言 | Apache 2.0 |
| [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) | 跨平台声明式 UI 框架 | Apache 2.0 |
| [OSHI](https://github.com/oshi/oshi) | 操作系统和硬件信息获取 | MIT |
| [Ktor](https://ktor.io) | 异步网络框架 | Apache 2.0 |

## 📬 联系方式

你可以通过我们的电子邮箱 hoe_software_team@outlook.com 发送邮件联系我们，或者加入我们的团队QQ群：1081639867。

如果你觉得本项目对你有帮助，欢迎给个 Star 支持！

## 📜 版权与许可证

版权所有 © 2026 HOE Team。保留部分权利。

本项目（NOT Toolbox）基于 **[GNU GPL v3 许可证](LICENSE)** 开源，是独立于 [NNETB](https://github.com/HOE-Team/NNETB) （[MIT 许可证](LICENSE-MIT-NNETB)）和 [NNETB-For-Linux](https://github.com/HOE-Team/NNETB-For-Linux) （[GPLv3 许可证](LICENSE-GPLV3-NNETB-FOR-LINUX)）的跨平台下游分支。

## 🔨 开发人员内容
### Debug常量
将此变量改为“true”启用调试，程序将使用本地存储的Packages Lists而不是从远程拉取。  

`MainApp.kt:`
```kotlin
// 编译时常量：true=启用本地DEBUG包列表，false=从远程拉取
const val IS_DEBUG = false
```
---

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
