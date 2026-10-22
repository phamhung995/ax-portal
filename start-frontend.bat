@echo off
title Skill Management - Frontend (Vue + Vite :5173)
color 0B

echo ============================================================
echo   SKILL MANAGEMENT - FRONTEND
echo   Vue 3 + Vite ^| Node 24 ^| Port: 5173
echo ============================================================
echo.

:: Load .env from root if exists
if exist "%~dp0.env" (
    for /f "usebackq tokens=1,* delims==" %%i in ("%~dp0.env") do (
        if "%%i"=="FRONTEND_PORT" set "FRONTEND_PORT=%%j"
        if "%%i"=="VITE_API_TARGET" set "VITE_API_TARGET=%%j"
    )
)

:: Check Backend is reachable on port 8080
echo [INFO] Checking Backend on localhost:8080 ...
powershell -NoProfile -Command "try { $t = New-Object System.Net.Sockets.TcpClient('localhost', 8080); $t.Close(); Write-Host '[OK]   Backend is reachable.' } catch { Write-Host '[INFO] Backend is not yet reachable on port 8080. It will connect once ready.'; }"

echo.
echo [INFO] Starting Frontend Dev Server...
echo [INFO] URL:  http://localhost:5173/skm/
echo [INFO] API requests proxied to: http://localhost:8080/skm
echo.
echo  Press Ctrl+C to stop the server.
echo ============================================================
echo.

cd /d "%~dp0frontend"
call npm.cmd run dev

pause
