@echo off
REM Run the MCP Project Documentation Server
cd /d "%~dp0"
.\gradlew.bat -q runMcpProjectDocsServer
