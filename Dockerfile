FROM thyrlian/android-sdk:latest

WORKDIR /app

# Copy your project files
COPY . .

# Accept licenses
RUN /opt/license-accepter.sh

# Grant execute permission to gradlew
RUN chmod +x ./gradlew

# Build command
CMD ["./gradlew", "assembleDebug", "--stacktrace"]