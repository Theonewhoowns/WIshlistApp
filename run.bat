@echo off
setlocal
cd /d "%~dp0"
java -jar "target\wishlist-app-2.0.0.jar" %*
if errorlevel 1 pause