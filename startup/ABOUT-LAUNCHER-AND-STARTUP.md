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
```bash
#!/bin/bash

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed 's/^1\.//' | cut -d'.' -f1)

if ! command -v java &> /dev/null; then
    echo -e "\033[31m错误\033[0m: 未找到 Java，需要安装 Java 11 或更高版本"
    exit 1
fi

if [ "$JAVA_VERSION" -lt 11 ]; then
    echo -e "\033[31m错误\033[0m: Java 版本太低 (当前: $JAVA_VERSION)"
    echo -e "需要安装 Java 11 或更高版本"
    exit 1
fi

java -jar NTB-all.jar
```
或直接在终端中键入
```bash
java -jar NTB-all.jar
```
