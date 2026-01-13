@echo off
REM Run the MCP Git Server
cd /d "%~dp0"
.\gradlew.bat -q runMcpGitServer
