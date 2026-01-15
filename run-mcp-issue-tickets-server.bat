@echo off
REM Run the MCP Issue Tickets Server
cd /d "%~dp0"
.\gradlew.bat -q runMcpIssueTicketsServer
