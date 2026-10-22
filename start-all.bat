@echo off
title Skill Management - Start Services
color 0E

echo ============================================================
echo   SKILL MANAGEMENT - START SERVICES (BE + FE)
echo   PostgreSQL 17 is running natively on localhost:5432
echo ============================================================
echo.

:: ---- Step 1: Start Backend in new window ----
echo [STEP 1/2] Starting Backend in a new window...
start "Skill Management - Backend (Spring Boot :8080)" /D "%~dp0" cmd /c start-backend.bat

echo [INFO] Waiting for Backend to initialize on port 8080...
set /a ATTEMPTS=0
:WAIT_BACKEND
ping 127.0.0.1 -n 3 >nul
set /a ATTEMPTS+=1
powershell -NoProfile -Command "try { $t = New-Object System.Net.Sockets.TcpClient('localhost', 8080); $t.Close(); exit 0 } catch { exit 1 }"
if errorlevel 1 (
    if %ATTEMPTS% LSS 30 (
        goto WAIT_BACKEND
    )
    echo [WARN] Backend took longer than expected to bind port 8080, proceeding with Frontend...
) else (
    echo [OK]   Backend is listening on port 8080.
)
echo.

:: ---- Step 2: Start Frontend in new window ----
echo [STEP 2/2] Starting Frontend in a new window...
start "Skill Management - Frontend (Vue + Vite :5173)" /D "%~dp0" cmd /c start-frontend.bat

ping 127.0.0.1 -n 3 >nul

echo.
echo ============================================================
echo [DONE] All services started in separate windows.
echo.
echo   Database : postgresql://localhost:5432/skill_manager (Native)
echo   Backend  : http://localhost:8080/skm
echo   Frontend : http://localhost:5173/skm/
echo ============================================================
echo.
echo [INFO] Opening Skill Management in browser...
start http://localhost:5173/skm/

if "%~1"=="" pause
