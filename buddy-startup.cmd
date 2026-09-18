@echo off
setlocal
cd /d "%~dp0"

if not exist "%~dp0out\buddy\Main.class" (
    echo Buddy classes not found. Building once before startup...
    if not exist "%~dp0out" mkdir "%~dp0out"
    javac -cp "%~dp0src" -d "%~dp0out" "%~dp0src\buddy\Main.java"
    for /r "%~dp0src" %%f in (*.java) do @javac -cp "%~dp0src" -d "%~dp0out" "%%f"
)

set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_CMD=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA_CMD (
    where javaw >nul 2>nul
    if not errorlevel 1 for /f "delims=" %%I in ('where javaw') do set "JAVA_CMD=%%I"
)

if not defined JAVA_CMD (
    echo Buddy could not find javaw.exe in PATH or JAVA_HOME.
    exit /b 1
)

start "" "%JAVA_CMD%" -cp "%~dp0out" buddy.Main --autostart
