#!/bin/bash
# Run the MCP Task Board Server
cd "$(dirname "$0")"
./gradlew -q runMcpTaskBoardServer
