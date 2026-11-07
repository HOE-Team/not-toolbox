@echo off
setlocal

echo [INFO] Copying files...
copy ..\build\libs\NTB-all.jar .\NTB-shrunk.jar >nul

if exist .\staging rmdir /s /q .\staging
mkdir .\staging
copy .\linux-startup.sh .\staging\ >nul
copy .\NTB-shrunk.jar .\staging\ >nul
if exist .\binary_sha256.sha256 copy .\binary_sha256.sha256 .\staging\ >nul
if exist .\binary_sha256.sha256.asc copy .\binary_sha256.sha256.asc .\staging\ >nul
mkdir .\staging\res
copy .\res-py\GoldenHoe_pub.asc .\staging\res\ >nul
if exist .\res-py\FINGERPRINT.txt copy .\res-py\FINGERPRINT.txt .\staging\res\ >nul

echo [INFO] Packing...
tar -czvf NTB.tar.gz -C .\staging .

echo [INFO] Deleting temp files...
rmdir /s /q .\staging
del .\NTB-shrunk.jar

echo [INFO] Done!
endlocal
