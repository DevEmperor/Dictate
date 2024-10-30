FROM thyrlian/android-sdk:latest

# Install additional packages needed for development environment
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    unzip \
    vim \
    && rm -rf /var/lib/apt/lists/*

# Accept licenses
RUN /opt/license-accepter.sh

# Set workspace directory
WORKDIR /workspace