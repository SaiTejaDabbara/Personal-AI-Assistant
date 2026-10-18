@echo off
setlocal
cd /d "%~dp0"

if not exist "%~dp0\.dist\Buddy.jar" (
    echo Buddy jar not found. Building once before startup...
    powershell -ExecutionPolicy Bypass -File "%~dp0build.ps1"
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

if exist "%~dp0\.dist\Buddy.jar" (
    start "" "%JAVA_CMD%" -jar "%~dp0\.dist\Buddy.jar" --autostart
    exit /b 0
)

echo Buddy startup failed because .dist\Buddy.jar was not generated successfully.
exit /b 1
