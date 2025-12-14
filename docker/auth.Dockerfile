# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app


RUN apk add --no-cache dos2unix

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY build-logic build-logic
RUN dos2unix gradlew && chmod +x gradlew

COPY services services

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:auth:bootJar -x test --no-daemon


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Copy the built Spring Boot jar
COPY --from=builder /app/services/auth/build/libs/*.jar app.jar

EXPOSE 8443 9091

# Health check on admin port
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9091/admin/health || exit 1

# 🔴 THIS LINE FIXES EVERYTHING
ENTRYPOINT ["java","-jar","/app/app.jar"]
