FROM thyrlian/android-sdk:latest

# Create and set permissions for workspace
RUN mkdir -p /workspace/.gradle && \
    chmod -R 777 /workspace/.gradle

# Additional VS Code Dev Container specific setup
RUN apt-get update && apt-get install -y \
    git \
    openssh-client \
    make \
    android-tools-adb \
    && apt-get clean

# Use the provided helper script to accept licenses
RUN /opt/license-accepter.sh

WORKDIR /workspace