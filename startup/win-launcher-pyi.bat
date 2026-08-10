@echo off
set "PROJECT_DIR=%~dp0"
set "ICON_FILE=%PROJECT_DIR%res-py\logo.ico"
if not exist "%ICON_FILE%" set "ICON_FILE=%PROJECT_DIR%res-py\icon.ico"
pyinstaller --onedir --noconsole --noconfirm --distpath "%PROJECT_DIR%pyi" --workpath "%PROJECT_DIR%pyi/build" --specpath "%PROJECT_DIR%pyi" --version-file "%PROJECT_DIR%res-py/vinfo.txt" --name "NTB" --icon="%ICON_FILE%" win-launcher.py