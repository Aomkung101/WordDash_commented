@echo off
chcp 65001 >nul
rem ใช้: run_server.bat [port] [ผู้เล่นขั้นต่ำ]   (ค่าเริ่มต้น 5000 และ 3)
java -Dfile.encoding=UTF-8 -cp out GameServer %*
pause
