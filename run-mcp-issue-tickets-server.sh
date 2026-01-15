#!/bin/bash
# Run the MCP Issue Tickets Server
cd "$(dirname "$0")"
./gradlew -q runMcpIssueTicketsServer
