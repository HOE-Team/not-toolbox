@echo off
setlocal EnableExtensions

rem Build the PyInstaller launcher for the architecture of the current Python.
rem PyInstaller cannot cross-compile: run this script with x64 Python on x64
rem Windows, or with ARM64 Python on Windows ARM64.
rem
rem Usage:
rem   win-launcher-pyi.bat            (auto-detect from current Python)
rem   win-launcher-pyi.bat x64
rem   win-launcher-pyi.bat arm64

set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"

set "TARGET_ARCH=%~1"
if not defined TARGET_ARCH (
    for /f "delims=" %%I in ('python -c "import platform;print(platform.machine())"') do set "TARGET_ARCH=%%I"
)

for /f "delims=" %%I in ('python -c "import platform;print(platform.machine())"') do set "PYTHON_ARCH=%%I"

if /I "%TARGET_ARCH%"=="AMD64" set "TARGET_ARCH=x64"
if /I "%TARGET_ARCH%"=="X86_64" set "TARGET_ARCH=x64"
if /I "%TARGET_ARCH%"=="ARM64" set "TARGET_ARCH=arm64"
if /I "%TARGET_ARCH%"=="AARCH64" set "TARGET_ARCH=arm64"

if /I "%PYTHON_ARCH%"=="AMD64" set "PYTHON_ARCH=x64"
if /I "%PYTHON_ARCH%"=="X86_64" set "PYTHON_ARCH=x64"
if /I "%PYTHON_ARCH%"=="ARM64" set "PYTHON_ARCH=arm64"
if /I "%PYTHON_ARCH%"=="AARCH64" set "PYTHON_ARCH=arm64"

if not defined TARGET_ARCH (
    echo [ERROR] Unable to detect the target architecture. Is Python installed?
    exit /b 1
)

if /I not "%TARGET_ARCH%"=="x64" if /I not "%TARGET_ARCH%"=="arm64" (
    echo [ERROR] Unsupported architecture: %TARGET_ARCH% ^(expected x64 or arm64^)
    exit /b 1
)

if /I not "%TARGET_ARCH%"=="%PYTHON_ARCH%" (
    echo [ERROR] PyInstaller cannot cross-compile.
    echo         Target: %TARGET_ARCH%, current Python: %PYTHON_ARCH%
    echo         Run this script with a %TARGET_ARCH% Python installation.
    exit /b 1
)

set "ICON_FILE=%PROJECT_DIR%res-py\logo.ico"
if not exist "%ICON_FILE%" set "ICON_FILE=%PROJECT_DIR%res-py\icon.ico"

echo [INFO] Building NTB.exe for %TARGET_ARCH% with %PYTHON_ARCH% Python...
python -m PyInstaller --onedir --noconsole --noconfirm --distpath "%PROJECT_DIR%pyi" --workpath "%PROJECT_DIR%pyi/build" --specpath "%PROJECT_DIR%pyi" --version-file "%PROJECT_DIR%res-py/vinfo.txt" --name "NTB" --icon="%ICON_FILE%" win-launcher.py
if errorlevel 1 (
    echo [ERROR] PyInstaller build failed.
    exit /b 1
)

> "%PROJECT_DIR%pyi\NTB-arch.txt" echo %TARGET_ARCH%
echo [INFO] Launcher built: %PROJECT_DIR%pyi\NTB\NTB.exe ^(%TARGET_ARCH%^)
endlocal
