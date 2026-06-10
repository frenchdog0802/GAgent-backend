# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy gradle wrapper and related files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Grant execution rights to the gradlew script
RUN chmod +x ./gradlew

# Download dependencies (this step will be cached unless build.gradle changes)
RUN ./gradlew dependencies --no-daemon || true

# Copy the rest of the application source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime (Debian-based for git + gh + CI sandbox tooling)
FROM eclipse-temurin:17-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
      git \
      gh \
      bash \
      maven \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port (can be overridden via environment variables if needed)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
