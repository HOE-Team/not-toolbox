import subprocess
import re
import sys
import ctypes
import hashlib
import os

# Windows 常量
CREATE_NO_WINDOW = 0x08000000
SW_HIDE = 0

def show_message_box(title, message, style=0x10):
    ctypes.windll.user32.MessageBoxW(0, message, title, style)

def get_file_sha256(filepath):
    """计算文件的 SHA256 哈希值"""
    try:
        sha256_hash = hashlib.sha256()
        with open(filepath, "rb") as f:
            # 分块读取，避免大文件占用过多内存
            for byte_block in iter(lambda: f.read(4096), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest().upper()
    except Exception:
        return None

def check_jar_integrity():
    jar_path = "./binary/NTB-all.jar"
    
    # 检查文件是否存在
    if not os.path.exists(jar_path):
        show_message_box(
            "文件缺失",
            f"找不到文件：{jar_path}\n\n请确保该文件存在于程序所在目录或重新安装。"
        )
        return False
    
    # 计算 SHA256
    calculated_hash = get_file_sha256(jar_path)
    expected_hash = "41F8227043D9FFCB40D20B58D9477AA870920E15E2D3C7A6863F37130FF5C334"
    
    if calculated_hash is None:
        show_message_box(
            "校验错误",
            f"无法读取或计算 {jar_path} 的 SHA256 值。"
        )
        return False
    
    if calculated_hash == expected_hash:
        return True
    else:
        show_message_box(
            "二进制被更改",
            f"文件 {jar_path} 的哈希值校验失败，\n\n"
            f"期望 SHA256：{expected_hash}\n"
            f"实际 SHA256：{calculated_hash}\n\n"
            f"结论：文件被篡改，需要重新安装。"
        )
        return False

def get_java_version():
    try:
        # 隐藏窗口运行 java -version
        startupinfo = subprocess.STARTUPINFO()
        startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startupinfo.wShowWindow = SW_HIDE
        
        result = subprocess.run(
            ["java", "-version"],
            capture_output=True,
            text=True,
            shell=False,
            creationflags=CREATE_NO_WINDOW,
            startupinfo=startupinfo
        )
        # java -version 输出到 stderr
        version_output = result.stderr + result.stdout
        
        # 匹配版本号格式：1.8.0 -> 8, 11.0.1 -> 11, 17.0.2 -> 17
        match = re.search(r'version "(\d+(?:\.\d+)*)"', version_output)
        if match:
            version_str = match.group(1)
            # 处理旧版本（如 1.8.0 -> 8）
            if version_str.startswith("1."):
                return int(version_str.split(".")[1])
            else:
                return int(version_str.split(".")[0])
        return None
    except FileNotFoundError:
        return None
    except Exception:
        return None

def check_java_version(min_version=11):
    java_version = get_java_version()
    
    if java_version is None:
        show_message_box(
            "Java 环境错误",
            "未找到 Java 运行时环境，需要安装 Java 11 或更高版本。"
        )
        return False
    
    if java_version >= min_version:
        return True
    else:
        show_message_box(
            "Java 版本过低",
            f"当前 Java 版本：{java_version}\n\n需要 Java {min_version} 或更高版本才能运行此程序。"
        )
        return False

def run_jar():
    """运行 JAR 文件"""
    # 首先检查 JAR 文件完整性和哈希值
    if not check_jar_integrity():
        sys.exit(1)
    
    # 然后检查 Java 环境
    if check_java_version(11):
        try:
            # 隐藏窗口运行 Java JAR
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = SW_HIDE
            
            subprocess.run(
                ["java", "-jar", "./binary/NTB-all.jar"],
                creationflags=CREATE_NO_WINDOW,
                startupinfo=startupinfo,
                shell=False
            )
        except FileNotFoundError:
            show_message_box("错误", "无法执行 java 命令，请检查 Java 安装。")
        except Exception as e:
            show_message_box("错误", f"运行 JAR 文件时出错：{str(e)}")
    else:
        sys.exit(1)

if __name__ == "__main__":
    run_jar()
