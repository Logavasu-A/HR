@echo off
setlocal
cd /d "%~dp0backend"
call "%~dp0backend\mvn.cmd" spring-boot:run
exit /b %errorlevel%
