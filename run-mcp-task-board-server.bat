@echo off
REM Run the MCP Task Board Server
cd /d "%~dp0"
.\gradlew.bat -q runMcpTaskBoardServer
