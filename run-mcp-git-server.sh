#!/bin/bash
# Run the MCP Git Server
cd "$(dirname "$0")"
./gradlew -q runMcpGitServer
