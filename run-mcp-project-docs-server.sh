#!/bin/bash
# Run the MCP Project Documentation Server
cd "$(dirname "$0")"
./gradlew -q runMcpProjectDocsServer
