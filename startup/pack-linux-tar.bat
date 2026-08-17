@echo off
echo [INFO] Cpoying file...
copy ..\build\libs\NTB-all.jar .\NTB-shrunk.jar

echo [INFO] Packing...
tar -czvf NTB.tar.gz .\linux-startup.sh .\NTB-shrunk.jar

echo [INFO] Deleting temp files...
del .\NTB-shrunk.jar

echo [INFO] Done!