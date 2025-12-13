FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

RUN apk add --no-cache dos2unix

# Copy gradle wrapper explicitly
COPY gradlew gradlew
COPY gradle/wrapper gradle/wrapper
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties

RUN dos2unix gradlew && chmod +x gradlew

# Copy build files
COPY build.gradle.kts settings.gradle.kts ./
COPY build-logic build-logic

# Copy services LAST (better cache)
COPY services services

RUN ./gradlew :services:auth:bootJar -x test --no-daemon
