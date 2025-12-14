# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

RUN apk add --no-cache dos2unix

# copy only gradle infra first
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY build-logic build-logic
RUN dos2unix gradlew && chmod +x gradlew

# now copy source
COPY services services

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:profile:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/services/profile/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
