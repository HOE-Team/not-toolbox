import subprocess
import re
import sys
import ctypes
from ctypes import wintypes
import hashlib
import os
import platform
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
JAVA_INSTALL_URLS = {
    "x64": "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/x64/windows/OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.msi",
    "arm64": "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/aarch64/windows",
}

def _normalize_arch(value):
    value = (value or "").strip().lower()
    if value in ("arm64", "aarch64"):
        return "arm64"
    if value in ("amd64", "x86_64", "x64"):
        return "x64"
    return value

def get_native_arch():
    """
    当前 Windows 的原始架构。

    ARM64 Windows 上如果运行的是 x64 版启动器，platform.machine() 会返回
    AMD64；此时用 PROCESSOR_ARCHITEW6432 识别真实的 ARM64 系统。
    """
    arch = _normalize_arch(
        platform.machine() or os.environ.get("PROCESSOR_ARCHITECTURE", "")
    )
    if arch == "x64":
        host_arch = _normalize_arch(os.environ.get("PROCESSOR_ARCHITEW6432", ""))
        if host_arch == "arm64":
            return "arm64"
    return arch

def get_java_install_url():
    arch = get_native_arch()
    return JAVA_INSTALL_URLS.get(arch, JAVA_INSTALL_URLS["x64"])

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

if getattr(sys, "frozen", False):
    # PyInstaller 打包后的 exe 所在目录
    APP_DIR = os.path.dirname(os.path.abspath(sys.executable))
else:
    # 直接从源码运行启动器时，使用脚本所在目录
    APP_DIR = os.path.dirname(os.path.abspath(__file__))
JAR_PATH = os.path.join(APP_DIR, "binary", "NTB-shrunk.jar")
HASH_FILE = os.path.join(APP_DIR, "binary_sha256.sha256")

def _read_expected_sha256():
    """
    读取 binary_sha256.sha256 中的 SHA256。
    兼容两种格式：
      <64位哈希>
      <64位哈希>  <文件名>
    忽略空行和 # 注释。
    """
    try:
        with open(HASH_FILE, "r", encoding="utf-8") as f:
            for raw_line in f:
                line = raw_line.strip()
                if not line or line.startswith("#"):
                    continue
                token = line.split()[0].lower()
                if re.fullmatch(r"[0-9a-f]{64}", token):
                    return token
        return None
    except Exception:
        return None

def _file_sha256(filepath):
    try:
        sha256_hash = hashlib.sha256()
        with open(filepath, "rb") as f:
            for byte_block in iter(lambda: f.read(1024 * 1024), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest().lower()
    except Exception:
        return None

def check_jar_integrity():
    """基础 SHA256 校验：只用于发现下载损坏或打包错误。"""
    if not os.path.exists(JAR_PATH):
        show_message_box(
            "文件缺失",
            f"找不到主程序文件：{JAR_PATH}\n\n请重新安装或检查安装包完整性。"
        )
        return False

    expected = _read_expected_sha256()
    if expected is None:
        show_message_box(
            "校验文件缺失",
            f"找不到或无法读取校验文件：{HASH_FILE}\n\n请重新安装。"
        )
        return False

    actual = _file_sha256(JAR_PATH)
    if actual is None:
        show_message_box(
            "校验错误",
            f"无法读取主程序文件：{JAR_PATH}"
        )
        return False

    if actual != expected:
        show_message_box(
            "文件校验失败",
            f"主程序文件的 SHA256 与校验文件不一致。\n\n"
            f"期望：{expected}\n"
            f"实际：{actual}\n\n"
            f"文件可能损坏或打包错误，请重新下载安装包。"
        )
        return False

    return True

def _skip_integrity():
    """开发构建可通过 --skip-integrity 或 NTB_SKIP_INTEGRITY=1 跳过基础校验。"""
    return "--skip-integrity" in sys.argv or os.environ.get("NTB_SKIP_INTEGRITY") == "1"

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

def get_java_arch():
    """读取 Java 自身的 os.arch，用于识别 x64 模拟环境。"""
    try:
        startupinfo = subprocess.STARTUPINFO()
        startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startupinfo.wShowWindow = SW_HIDE
        result = subprocess.run(
            ["java", "-XshowSettings:properties", "-version"],
            capture_output=True,
            text=True,
            shell=False,
            creationflags=CREATE_NO_WINDOW,
            startupinfo=startupinfo
        )
        match = re.search(r"(?m)^\s*os\.arch\s*=\s*(\S+)", result.stderr + result.stdout)
        return _normalize_arch(match.group(1)) if match else None
    except Exception:
        return None

def check_java_arch():
    native = get_native_arch()
    java_arch = get_java_arch()
    if native not in ("x64", "arm64") or java_arch is None or java_arch == native:
        return True

    native_label = "ARM64" if native == "arm64" else "x64"
    java_label = "ARM64" if java_arch == "arm64" else "x64"
    if show_yes_no_box(
        "Java 架构不匹配",
        f"当前 Windows 为 {native_label}，检测到 Java 为 {java_label}。\n\n"
        f"建议安装 {native_label} 版 Java 21，否则程序可能通过模拟层运行，"
        f"性能和本地组件兼容性可能受影响。\n\n仍要继续吗？"
    ):
        return True
    return False

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
        return check_java_arch()
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
    # 按当前 Windows 架构选择 x64 / ARM64 的 JRE
    download_url = get_java_install_url()
    download_name = os.path.basename(download_url)
    try:
        webbrowser.open(download_url)
    except Exception as e:
        show_message_box(
            "打开浏览器失败",
            f"无法自动打开浏览器，请手动复制以下链接到浏览器下载：\n\n"
            f"{download_url}\n\n"
            f"错误信息：{str(e)}"
        )
        return False
    
    # 使用 TaskDialog 显示带可点击超链接的下载提示
    show_task_dialog(
        "下载 Java",
        f"已打开浏览器，正在从 TUNA 镜像下载 Java {min_version} JRE 安装包\n\n"
        f"若下载还未开始，请点击此链接重新下载：\n"
        f"<a href=\"{download_url}\">{download_name}</a>\n\n"
        f"下载完成后，请运行下载的 MSI 文件完成安装。\n"
        f"安装完成后，重新运行本程序。",
        main_instruction=f"下载 Java {min_version} JRE",
        hyperlink_callback=_open_download_link
    )
    return False

def run_jar():
    """运行 JAR 文件"""
    # 基础完整性校验（可被开发构建跳过）
    if not _skip_integrity() and not check_jar_integrity():
        sys.exit(1)

    # 检查 Java 环境（要求 Java 21+）
    if check_java_version(21):
        try:
            # 隐藏窗口运行 Java JAR
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = SW_HIDE
            
            subprocess.run(
                ["java", "-jar", JAR_PATH],
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
