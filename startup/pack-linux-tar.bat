@echo off
echo [INFO] Cpoying file...
copy ..\build\libs\NTB-all.jar .\NTB-all.jar

echo [INFO] Packing...
tar -czvf NTB.tar.gz .\linux-startup.sh .\NTB-all.jar

echo [INFO] Deleting temp files...
del .\NTB-all.jar

echo [INFO] Done!
pause