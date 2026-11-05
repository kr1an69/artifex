@echo off
title ARTIFEX - Spring Boot Backend Server
echo ======================================================================
echo           ARTIFEX BACKEND SERVER (Spring Boot 4 + MySQL)
echo ======================================================================
echo.

set ADB_PATH="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if exist %ADB_PATH% (
    echo [*] Dang thiet lap ADB Reverse tren cong 8080...
    %ADB_PATH% reverse tcp:8080 tcp:8080 >nul 2>&1
    echo [*] ADB Reverse da duoc khoi tao!
) else (
    adb reverse tcp:8080 tcp:8080 >nul 2>&1
)

echo [*] Dang khoi chay Spring Boot...
cd /d "%~dp0\backend" 2>nul || cd /d "%~dp0"
call mvnw.cmd spring-boot:run
pause
