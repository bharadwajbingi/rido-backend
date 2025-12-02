FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle :profile:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/profile/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
