#!/bin/bash
# .devcontainer/init-adb.sh

# Kill any existing ADB server
adb kill-server

# Start ADB server listening on all interfaces
adb start-server

# Wait for ADB to be ready
sleep 5

# Connect to the emulator
adb connect localhost:5555