import subprocess
import re
import sys
import ctypes
from ctypes import wintypes
import hashlib
import os
import webbrowser

# Windows 常量
CREATE_NO_WINDOW = 0x08000000
SW_HIDE = 0
MB_YESNO = 0x04
MB_ICONQUESTION = 0x20
IDYES = 6

# TaskDialog 常量
TDF_ENABLE_HYPERLINKS = 0x0001
TDN_HYPERLINK_CLICKED = 0
TD_WARNING_ICON = 0xFFFF
TD_INFORMATION_ICON = 0xFFFF - 1
TD_ERROR_ICON = 0xFFFF - 2
BUTTON_OK = 1
BUTTON_CANCEL = 2
IDOK = 1
IDCANCEL = 2

# TaskDialog 回调返回码
S_OK = 0

# TASKDIALOG_COMMON_BUTTON_FLAGS
TDCBF_OK_BUTTON = 0x0001
TDCBF_CANCEL_BUTTON = 0x0002
TDCBF_YES_BUTTON = 0x0004
TDCBF_NO_BUTTON = 0x0008

# TASKDIALOG_BUTTON 结构体
class TASKDIALOG_BUTTON(ctypes.Structure):
    _fields_ = [
        ("nButtonID", ctypes.c_int),
        ("pszButtonText", wintypes.LPCWSTR),
    ]

# TASKDIALOGCONFIG 结构体
class TASKDIALOGCONFIG(ctypes.Structure):
    _fields_ = [
        ("cbSize", wintypes.DWORD),
        ("hwndParent", wintypes.HWND),
        ("hInstance", wintypes.HINSTANCE),
        ("dwFlags", wintypes.DWORD),
        ("dwCommonButtons", wintypes.DWORD),
        ("pszWindowTitle", wintypes.LPCWSTR),
        ("pszMainIcon", wintypes.LPVOID),
        ("pszMainInstruction", wintypes.LPCWSTR),
        ("pszContent", wintypes.LPCWSTR),
        ("cButtons", wintypes.UINT),
        ("pButtons", ctypes.POINTER(TASKDIALOG_BUTTON)),
        ("nDefaultButton", ctypes.c_int),
        ("cRadioButtons", wintypes.UINT),
        ("pRadioButtons", ctypes.POINTER(TASKDIALOG_BUTTON)),
        ("nDefaultRadioButton", ctypes.c_int),
        ("pszVerificationText", wintypes.LPCWSTR),
        ("pszExpandedInformation", wintypes.LPCWSTR),
        ("pszExpandedControlText", wintypes.LPCWSTR),
        ("pszCollapsedControlText", wintypes.LPCWSTR),
        ("pszFooterIcon", wintypes.LPVOID),
        ("pszFooter", wintypes.LPCWSTR),
        ("pfCallback", ctypes.c_void_p),
        ("lpCallbackData", wintypes.LPARAM),
        ("cxWidth", wintypes.UINT),
    ]

# 全局回调用户数据：存放超链接点击后的回调函数
_hyperlink_callback = None

def _task_dialog_callback(hwnd, msg, wparam, lparam, refdata):
    """TaskDialog 回调：处理超链接点击通知"""
    if msg == TDN_HYPERLINK_CLICKED:
        # lparam 指向超链接文本
        link = ctypes.wstring_at(lparam) if lparam else ""
        if link and _hyperlink_callback is not None:
            _hyperlink_callback(link)
    return S_OK

TASKDIALOGCALLBACK = ctypes.CFUNCTYPE(
    ctypes.c_int,
    wintypes.HWND,
    wintypes.UINT,
    wintypes.WPARAM,
    wintypes.LPARAM,
    wintypes.LPARAM
)

def show_task_dialog(title, content, hyperlink_callback=None, main_instruction=None, icon=TD_INFORMATION_ICON):
    """
    显示支持可点击超链接的 TaskDialog 消息框。
    
    内容中使用 <a href="URL">显示文本</a> 语法定义链接。
    超链接被点击时，会调用 hyperlink_callback(url)。
    """
    global _hyperlink_callback
    _hyperlink_callback = hyperlink_callback
    
    # 创建回调函数引用（防止被垃圾回收）
    callback_fn = TASKDIALOGCALLBACK(_task_dialog_callback)
    
    config = TASKDIALOGCONFIG()
    config.cbSize = ctypes.sizeof(TASKDIALOGCONFIG)
    config.hwndParent = None
    config.hInstance = None
    config.dwFlags = TDF_ENABLE_HYPERLINKS
    config.dwCommonButtons = TDCBF_OK_BUTTON
    config.pszWindowTitle = title
    config.pszMainIcon = ctypes.cast(icon, wintypes.LPVOID)
    config.pszMainInstruction = main_instruction if main_instruction else None
    config.pszContent = content
    config.cButtons = 0
    config.pButtons = None
    config.nDefaultButton = BUTTON_OK
    config.cRadioButtons = 0
    config.pRadioButtons = None
    config.nDefaultRadioButton = 0
    config.pszVerificationText = None
    config.pszExpandedInformation = None
    config.pszExpandedControlText = None
    config.pszCollapsedControlText = None
    config.pszFooterIcon = None
    config.pszFooter = None
    
    # 注册回调
    if hyperlink_callback is not None:
        config.pfCallback = ctypes.cast(callback_fn, ctypes.c_void_p)
    else:
        config.pfCallback = None
    config.lpCallbackData = 0
    config.cxWidth = 0
    
    # 调用 TaskDialogIndirect
    result = wintypes.INT()
    try:
        hr = ctypes.windll.comctl32.TaskDialogIndirect(
            ctypes.byref(config),
            ctypes.byref(result),
            None,
            None
        )
        if hr != S_OK:
            return None
        return result.value
    except AttributeError:
        # 系统不支持 TaskDialog 时回退到 MessageBox
        show_message_box(title, content.replace('<a href="', '').replace('">', ': ').replace('</a>', ''))
        return None
    except Exception:
        return None

# Java 下载地址（TUNA 镜像）
JAVA_INSTALL_URL = "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/x64/windows/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.msi"

def _open_download_link(url):
    """打开下载链接"""
    try:
        webbrowser.open(url)
    except Exception:
        pass

def show_message_box(title, message, style=0x10):
    ctypes.windll.user32.MessageBoxW(0, message, title, style)

def show_yes_no_box(title, message):
    """显示是/否提示框，返回 True 表示用户点击了"是" """
    result = ctypes.windll.user32.MessageBoxW(0, message, title, MB_YESNO | MB_ICONQUESTION)
    return result == IDYES

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

def read_expected_sha256():
    """从 binary_sha256.sha256 文件中读取期望的 SHA256 值"""
    hash_file_path = "./binary_sha256.sha256"
    
    # 检查哈希文件是否存在
    if not os.path.exists(hash_file_path):
        show_message_box(
            "校验文件缺失",
            f"找不到校验文件：{hash_file_path}\n\n"
            f"请确保该文件存在于程序所在目录。"
        )
        return None
    
    try:
        with open(hash_file_path, "r") as f:
            content = f.read().strip()
            # 只提取 SHA256 值（不带前缀和后缀）
            return content.upper()
    except Exception:
        show_message_box(
            "校验文件错误",
            f"无法读取校验文件：{hash_file_path}"
        )
        return None

def check_jar_integrity():
    jar_path = "./binary/NTB-shrunk.jar"
    
    # 检查文件是否存在
    if not os.path.exists(jar_path):
        show_message_box(
            "文件缺失",
            f"找不到主程序文件：{jar_path}\n\n请确保该文件存在于程序所在目录或重新安装。"
        )
        return False
    
    # 读取期望的 SHA256 值
    expected_hash = read_expected_sha256()
    if expected_hash is None:
        return False
    
    # 计算 SHA256
    calculated_hash = get_file_sha256(jar_path)
    
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
            "校验失败",
            f"文件 {jar_path} 的哈希值校验失败，\n\n"
            f"期望 SHA256：{expected_hash}\n"
            f"实际 SHA256：{calculated_hash}\n\n"
            f"文件可能损坏或被篡改，重新安装以解决此问题。"
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

def check_java_version(min_version=21):
    java_version = get_java_version()
    
    if java_version is None:
        # 未找到 Java，询问是否打开浏览器引导下载
        if show_yes_no_box(
            "Java 环境错误",
            f"未找到 Java 运行时环境，无法运行此程序。\n\n"
            f"需要 Java {min_version} 或更高版本。\n\n"
            f"是否下载 Java {min_version} JRE？"
        ):
            return download_and_install_java(min_version)
        show_message_box(
            "Java 环境错误",
            f"未找到 Java 运行时环境，需要安装 Java {min_version} 或更高版本。"
        )
        return False
    
    if java_version >= min_version:
        return True
    else:
        # 版本过低，询问是否打开浏览器引导下载
        if show_yes_no_box(
            "Java 版本过低",
            f"当前 Java 版本：{java_version}\n\n"
            f"需要 Java {min_version} 或更高版本才能运行此程序。\n\n"
            f"是否下载 Java {min_version} JRE？"
        ):
            return download_and_install_java(min_version)
        show_message_box(
            "Java 版本过低",
            f"当前 Java 版本：{java_version}\n\n需要 Java {min_version} 或更高版本才能运行此程序。"
        )
        return False

def download_and_install_java(min_version):
    """
    打开浏览器引导用户从 TUNA 镜像下载 Java JRE MSI 并手动安装。
    下载并安装完成后，用户需重新运行本程序。
    """
    # 打开浏览器到下载页面
    try:
        webbrowser.open(JAVA_INSTALL_URL)
    except Exception as e:
        show_message_box(
            "打开浏览器失败",
            f"无法自动打开浏览器，请手动复制以下链接到浏览器下载：\n\n"
            f"{JAVA_INSTALL_URL}\n\n"
            f"错误信息：{str(e)}"
        )
        return False
    
    # 使用 TaskDialog 显示带可点击超链接的下载提示
    show_task_dialog(
        "下载 Java",
        f"已打开浏览器，正在从 TUNA 镜像下载 Java {min_version} JRE 安装包\n\n"
        f"若下载还未开始，请点击此链接重新下载：\n"
        f"<a href=\"{JAVA_INSTALL_URL}\">OpenJDK21U-jre_x64_windows_hotspot_21.0.12_8.msi</a>\n\n"
        f"下载完成后，请运行下载的 MSI 文件完成安装。\n"
        f"安装完成后，重新运行本程序。",
        main_instruction=f"下载 Java {min_version} JRE",
        hyperlink_callback=_open_download_link
    )
    return False

def run_jar():
    """运行 JAR 文件"""
    # 首先检查 JAR 文件完整性和哈希值
    if not check_jar_integrity():
        sys.exit(1)
    
    # 然后检查 Java 环境（要求 Java 21+）
    if check_java_version(21):
        try:
            # 隐藏窗口运行 Java JAR
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = SW_HIDE
            
            subprocess.run(
                ["java", "-jar", "./binary/NTB-shrunk.jar"],
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
