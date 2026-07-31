# 适用于 NOT Toolbox 的 Windows 平台打包工具、启动器与 Linux 平台启动脚本

## 适用于 NOT Toolbox 的启动器、打包工具

### NTB 启动器(NTB Launcher)
**概览**：  
NTB 启动器用于校验二进制完整性、环境检查和使用 java -jar 指令启动NTB-all.jar  

**实现**：
- 使用的语言：Python3
- 使用的库： 
    ```python 
    import subprocess
    import re
    import sys
    import ctypes
    import hashlib
    import os
    ```
**关于复用**：  
如果你更改了 NOT Toolbox 代码的内容并且希望复用 NOT Toolbox Launcher ，你需要修改文件中的 SHA256 校验和内容，您需要修改第 40 行的 expcted_hash 变量，具体如下：
```python
    # 计算 SHA256
    calculated_hash = get_file_sha256(jar_path)

    # 修改此处
    expected_hash = "41F8227043D9FFCB40D20B58D9477AA870920E15E2D3C7A6863F37130FF5C334"
```
你也可以删除代码中关于 SHA256 检验的代码来禁用校验。

**NTB Launcher 授权信息**：  
NTB Launcher 使用 [MIT License](../LICENSES/LICENSE-MIT-NTB-LAUNCHER) 开源  
版权所有 ©2026 HOE Team。保留所有权利。

### NTB Launcher 打包工具
**将 NTB 启动器打包为 exe 可执行文件**：  
> [!NOTE]
> 你的计算机需要安装Python3并通过pip安装pyinstaller

你可以通过直接运行 win-launcher-pyi.bat（Windows Only） 来直接打包为exe，你也可以执行如下代码来实现打包
```powershell
pyinstaller --onefile win-launcher.py
```
更多打包参数请在 shell 键入`pyinstaller --help`查看，如需更改图标，请将你的新图标命名为 logo.ico 并放入 ./res-py 目录，如需更改应用属性，请修改 ./res-py/vinfo.txt。

**连同 jar 文件一同打包为安装程序**： 
> [!NOTE]
> 你的计算机需要安装InnoSetup 6.x

在命令行执行
```cmd
iscc pack-exe.iss
```
若需修改安装程序的信息，请修改pack-exe.iss

**NTB Launcher Pack Tools 授权信息**：  
NTB Launcher 使用 [MIT License](../LICENSES/LICENSE-MIT-NTB-LAUNCHER-PACK-TOOL) 开源  
版权所有 ©2026 HOE Team。保留所有权利。

## Linux 平台启动脚本

**功能**：  
Linux 启动脚本（linux-startup.sh）会在启动时自动获取 sudo 权限，并检查 Java 环境（要求 Java 21 或更高版本），然后以 root 权限启动 NOT Toolbox，确保包管理器操作（如安装软件包）可以正常执行。

**为什么不直接使用 `java -jar` 启动？**  
由于 NOT Toolbox 在 Linux 平台上需要执行包管理器操作（如 apt、dnf、pacman 等），这些操作通常需要 root 权限。直接使用 `java -jar` 启动将无法进行需要权限的操作。

**脚本内容**：
```bash
#!/bin/bash

# NOT Toolbox Linux 启动脚本
# 此脚本会在启动时获取 sudo 权限，并将 Java 要求提升至 Java 21+

# 检查 sudo 是否可用
if ! command -v sudo &> /dev/null; then
    echo -e "\033[31m错误\033[0m: 未找到 sudo 命令，请先安装 sudo 或使用 root 用户直接执行"
    exit 1
fi

# 检查是否已是 root 用户
if [ "$(id -u)" -eq 0 ]; then
    echo -e "\033[32m提示\033[0m: 当前已是 root 用户，无需获取 sudo 权限"
else
    # 获取 sudo 权限（验证用户密码并缓存凭据）
    echo -e "\033[33m提示\033[0m: NOT Toolbox 需要 sudo 权限以进行包管理器操作"
    if sudo -v; then
        echo -e "\033[32m成功\033[0m: 已获取 sudo 权限"
    else
        echo -e "\033[31m错误\033[0m: 未能获取 sudo 权限，无法继续启动"
        exit 1
    fi
fi

# Java 版本检查
if ! command -v java &> /dev/null; then
    echo -e "\033[31m错误\033[0m: 未找到 Java，需要安装 Java 21 或更高版本"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed 's/^1\.//' | cut -d'.' -f1)

if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "\033[31m错误\033[0m: Java 版本太低 (当前: $JAVA_VERSION)"
    echo -e "需要安装 Java 21 或更高版本"
    exit 1
fi

echo -e "\033[32m完成\033[0m: Java 版本检查通过 (Java $JAVA_VERSION)"

# 使用 sudo 权限启动 NOT Toolbox
exec sudo java -jar NTB-all.jar
```

**使用方式**：
```bash
sh linux-startup.sh
```

> [!WARNING]
> 由于脚本会使用 sudo 启动程序，运行时会请求输入用户密码以获取权限。
> 如果你的系统当前已是 root 用户，脚本会自动检测并跳过 sudo 验证步骤。
> 如果你无法使用 sudo 权限，请考虑以 root 用户身份运行脚本。
