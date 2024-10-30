FROM mcr.microsoft.com/vscode/devcontainers/universal:latest

# Enable i386 architecture for Android tools
RUN dpkg --add-architecture i386

# Install Android SDK and related packages
RUN apt-get update && apt-get install -y \
    android-sdk \
    android-sdk-build-tools \
    android-sdk-platform-tools \
    android-tools-adb \
    android-tools-fastboot \
    # Dependencies for Android tools
    libc6:i386 \
    libstdc++6:i386 \
    zlib1g:i386 \
    libncurses5 \
    libpulse0 \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV ANDROID_SDK_ROOT=/usr/lib/android-sdk
ENV ANDROID_HOME=/usr/lib/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/tools

WORKDIR /workspace
