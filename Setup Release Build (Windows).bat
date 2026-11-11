@echo off
rem Double-click me. Sets up this PC and builds the signed .aab for Google Play.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup-release.ps1"
echo.
pause
