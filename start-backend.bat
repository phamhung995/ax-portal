@echo off
title Skill Management - Backend (Spring Boot :8080)
color 0A

echo ============================================================
echo   SKILL MANAGEMENT - BACKEND
echo   Spring Boot 3.4.2 ^| Java 21 ^| Port: 8080
echo ============================================================
echo.

:: ==================== 1. Load .env Configuration ====================
set "DB_HOST=localhost"
set "DB_PORT=5432"
set "DB_NAME=skill_manager"
set "DB_USERNAME=postgres"
set "DB_PASSWORD=postgres"
set "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/skill_manager"

if exist "%~dp0.env" (
    for /f "usebackq tokens=1,* delims==" %%i in ("%~dp0.env") do (
        if "%%i"=="DB_HOST" set "DB_HOST=%%j"
        if "%%i"=="DB_PORT" set "DB_PORT=%%j"
        if "%%i"=="DB_NAME" set "DB_NAME=%%j"
        if "%%i"=="DB_USERNAME" set "DB_USERNAME=%%j"
        if "%%i"=="DB_PASSWORD" set "DB_PASSWORD=%%j"
        if "%%i"=="SPRING_DATASOURCE_URL" set "SPRING_DATASOURCE_URL=%%j"
        if "%%i"=="JWT_SECRET" set "JWT_SECRET=%%j"
        if "%%i"=="CORS_ORIGINS" set "CORS_ORIGINS=%%j"
    )
)

:: ==================== 2. Set JAVA_HOME ====================
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Java\jdk-21" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    )
)
if "%JAVA_HOME%"=="" (
    echo [ERROR] JAVA_HOME is not set and JDK 21 was not found in default path!
    pause
    exit /b 1
)
echo [INFO] JAVA_HOME: %JAVA_HOME%

:: ==================== 3. Check PostgreSQL ====================
echo [INFO] Checking PostgreSQL on %DB_HOST%:%DB_PORT% ...
powershell -NoProfile -Command "try { $t = New-Object System.Net.Sockets.TcpClient('%DB_HOST%', [int]'%DB_PORT%'); $t.Close(); Write-Host '[OK]   PostgreSQL is reachable.' } catch { Write-Host '[WARN] PostgreSQL not reachable on %DB_HOST%:%DB_PORT%.'; exit 1 }"
if errorlevel 1 (
    echo.
    echo  [ERROR] PostgreSQL is not running on %DB_HOST%:%DB_PORT%!
    echo  Please start your PostgreSQL 17 service or run start-database.bat first.
    echo.
    pause
    exit /b 1
)

echo.
echo [INFO] Database URL: %SPRING_DATASOURCE_URL%
echo [INFO] Starting Spring Boot Backend...
echo [INFO] URL: http://localhost:8080/skm
echo [INFO] Health: http://localhost:8080/skm/actuator/health
echo.
echo  Press Ctrl+C to stop the server.
echo ============================================================
echo.

cd /d "%~dp0backend"
call mvnw.cmd spring-boot:run

pause
