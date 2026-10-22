@echo off
title Skill Management - Stop Services
color 0C

echo ============================================================
echo   SKILL MANAGEMENT - STOP SERVICES (BE + FE)
echo ============================================================
echo.

:: ---- Stop Backend (port 8080) ----
echo [INFO] Stopping Backend (port 8080)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    echo [INFO] Terminating PID %%a (port 8080)
    taskkill /PID %%a /T /F >nul 2>&1
)
taskkill /FI "WINDOWTITLE eq Skill Management - Backend*" /T /F >nul 2>&1

:: ---- Stop Frontend (port 5173) ----
echo [INFO] Stopping Frontend (port 5173)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173 " ^| findstr "LISTENING"') do (
    echo [INFO] Terminating PID %%a (port 5173)
    taskkill /PID %%a /T /F >nul 2>&1
)
taskkill /FI "WINDOWTITLE eq Skill Management - Frontend*" /T /F >nul 2>&1

echo.
echo ============================================================
echo [DONE] Backend and Frontend services stopped.
echo   Note: Native PostgreSQL 17 remains running.
echo ============================================================
echo.
if "%~1"=="" pause
