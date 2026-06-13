@echo off
set "PROJECT_DIR=%~dp0"
pyinstaller --onedir --noconsole --noconfirm --distpath "%PROJECT_DIR%pyi" --workpath "%PROJECT_DIR%pyi/build" --specpath "%PROJECT_DIR%pyi" --version-file "%PROJECT_DIR%res-py/vinfo.txt" --name "NTB" --icon="%PROJECT_DIR%res-py/logo.ico" win-launcher.py
pause