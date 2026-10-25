@echo off
chcp 65001 >nul
rem ใช้: run_client.bat [ip-server] [port] [ชื่อ]
java -Dfile.encoding=UTF-8 -cp out GameFrame %*
