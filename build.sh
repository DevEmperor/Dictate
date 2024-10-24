#!/bin/bash

# Build the Docker image
echo "Building Docker image..."
docker build -t dictate-builder .

# Run the container
echo "Running container..."
docker run --rm -v $(pwd):/app dictate-builder

# Copy the APK to root directory
echo "Copying APK to root directory..."
cp app/build/outputs/apk/debug/app-debug.apk ./dictate-debug.apk

echo "Build completed. APK is available as dictate-debug.apk"