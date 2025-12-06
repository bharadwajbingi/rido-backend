# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
# Fix line endings for all text files (Windows CRLF -> Unix LF)
# This is necessary because Windows uses CRLF and Linux/Docker uses LF
RUN apk add --no-cache dos2unix && \
    dos2unix gradlew && \
    find . -name "*.sql" -exec dos2unix {} \; && \
    find . -name "*.properties" -exec dos2unix {} \; && \
    chmod +x gradlew
# Use cache mount for Gradle dependencies to speed up builds
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:auth:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/auth/build/libs/*.jar app.jar
COPY --from=builder /app/services/auth/src/main/resources/application.yml application.yml
EXPOSE 8443 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
