@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

call :hash_file "..\build\libs\NTB-all.jar"
call :hash_file "NTB.tar.gz"
call :hash_file "installer\NOT_Toolbox_Setup.exe"

echo.
echo [INFO] Hash check complete.
pause
exit /b 0

:hash_file
set "FILE=%~1"

if not exist "%FILE%" (
    echo [ERROR] File not found: %FILE%
    goto :eof
)

echo.
echo **%~nx1**

for /f "delims=" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Path '%FILE%' -Algorithm SHA256).Hash.ToLower()"') do set "SHA256=%%H"
for /f "delims=" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Path '%FILE%' -Algorithm MD5).Hash.ToLower()"') do set "MD5=%%H"

echo SHA256: `!SHA256!`
echo MD5:   `!MD5!`

goto :eof

pause