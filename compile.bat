@echo off
chcp 65001 >nul
if not exist out mkdir out
rem javac ไม่ขยาย src\*.java ใน JDK ใหม่ จึงระบุ 2 ไฟล์หลัก แล้วให้ -sourcepath หาคลาสที่เหลือเอง
javac -encoding UTF-8 -d out -sourcepath src src\GameServer.java src\GameFrame.java
pause
