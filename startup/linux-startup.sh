#!/bin/bash
# NOT Toolbox Linux Startup Shell Script

if ! command -v sudo &> /dev/null; then
    echo -e "\033[31m错误\033[0m: 未找到 sudo 命令，请先安装 sudo 或使用 root 用户直接执行"
    exit 1
fi

# 检查是否已是 root 用户
if [ "$(id -u)" -eq 0 ]; then
    echo -e "\033[32m提示\033[0m: 当前已是 root 用户"
else
    echo -e "\033[33m提示\033[0m: NOT Toolbox 需要提升权限"
    if sudo -v; then
        echo -e "\033[32m成功\033[0m: 完成"
    else
        echo -e "\033[31m错误\033[0m: 无法完成提权"
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

# 使用 sudo 权限启动 NOT Toolbox
exec sudo java -jar NTB-all.jar