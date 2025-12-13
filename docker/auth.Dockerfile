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

# 🔴 IMPORTANT: copy EXACT jar
COPY --from=builder /app/services/auth/build/libs/*-boot.jar app.jar

EXPOSE 8443 9091

# 🔴 THIS LINE FIXES EVERYTHING
ENTRYPOINT ["java","-jar","/app/app.jar"]
