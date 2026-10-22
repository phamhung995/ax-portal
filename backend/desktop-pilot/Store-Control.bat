@echo off
setlocal
title SDS Skills Store Pilot Control
:menu
echo.
echo ===== SDS Skills Store - Local Pilot =====
echo 1. Start Store
echo 2. Stop Store
echo 3. Store status
echo 4. Exit
choice /C 1234 /N /M "Choose [1-4]: "
if errorlevel 4 exit /b 0
if errorlevel 3 set "ACTION=Status"
if errorlevel 2 if not errorlevel 3 set "ACTION=Stop"
if errorlevel 1 if not errorlevel 2 set "ACTION=Start"
echo.
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0store-control.ps1" -Action %ACTION%
echo.
pause
goto menu
