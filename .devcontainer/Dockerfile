FROM mcr.microsoft.com/vscode/devcontainers/universal:latest

# Enable i386 architecture for Android tools
RUN dpkg --add-architecture i386

# Install base system dependencies
RUN apt-get update && apt-get install -y \
    libc6:i386 \
    libstdc++6:i386 \
    zlib1g:i386 \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK core packages
RUN apt-get update && apt-get install -y \
    android-sdk \
    android-sdk-build-tools \
    android-sdk-platform-tools \
    && rm -rf /var/lib/apt/lists/*

# Install Android development tools
RUN apt-get update && apt-get install -y \
    android-tools-adb \
    android-tools-fastboot \
    gradle \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV ANDROID_SDK_ROOT=/usr/lib/android-sdk
ENV ANDROID_HOME=/usr/lib/android-sdk
ENV GRADLE_HOME=/usr/share/gradle
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/platform-tools:${GRADLE_HOME}/bin

WORKDIR /workspace
