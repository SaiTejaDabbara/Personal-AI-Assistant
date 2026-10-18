@echo off
setlocal
cd /d "%~dp0"

if not exist "%~dp0\.dist\Buddy.jar" (
    echo Buddy jar not found. Building once before launch...
    powershell -ExecutionPolicy Bypass -File "%~dp0build.ps1"
)

if exist "%~dp0\.dist\Buddy.jar" (
    java -jar "%~dp0\.dist\Buddy.jar" %*
    exit /b %ERRORLEVEL%
)

echo Buddy could not be started because the jar was not generated successfully.
exit /b 1
