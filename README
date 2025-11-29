# Rido — Real-Time Ride-Hailing Backend

A modern backend inspired by Uber/Ola, built with microservices, WebFlux, Redis, PostgreSQL, Kafka, Docker, and JWT.

## 🏗 Architecture Overview

Rido is designed with five microservices:

### 1. API Gateway (Kotlin)

- Central routing
- JWT validation
- X-User-Id injection
- Rate-limiting (upcoming)

### 2. Auth Service (Java)

- Signup / login
- Argon2id password hashing
- Refresh token rotation
- Replay attack detection
- Redis brute-force protection

### 3. Matching Service (Upcoming)

- Driver assignment
- Redis GEO for proximity
- Kafka for real-time matching

### 4. Trips Service (Upcoming)

- Trip lifecycle
- Event-driven state machine

### 5. Payments Service (Upcoming)

- Mock payments
- Retry logic + DLQ

## 🔧 Tech Stack

### Languages

- Java 21 (Auth Service)
- Kotlin (API Gateway)

### Frameworks

- Spring Boot 3
- Spring WebFlux
- Spring Cloud Gateway
- Spring Security

### Infrastructure

- Redis
- PostgreSQL
- Kafka
- Docker + Docker Compose

## 🔐 Auth Service Features (~60% Complete)

### ✅ Completed

- **Password Security**: Argon2id hashing with strong salt + cost parameters
- **JWT Access Tokens**: Short-lived, includes JTI, signed using shared secret
- **Refresh Tokens**: Stored as SHA-256 hashes, rotation on every use, replay attack detection
- **Brute-Force Protection**: Redis per-IP counter, lockout after threshold
- **Logout**: Revokes all refresh tokens
- **Global Error Handling**: Clean JSON error responses with unified error codes
- **Postman Test Suite**: Comprehensive coverage of all endpoints

### Key Security Features

- **Replay Attack Detection**: If a refresh token is reused, all user sessions are revoked
- **Token Rotation**: New refresh token issued on every use
- **IP-Based Lockout**: Prevents brute-force attacks with Redis counters

## 🚪 API Gateway Features (Kotlin)

### ✅ Completed

- **Routing**: Routes requests to appropriate microservices
  - `/auth/**` → Auth Service
  - Future: `/trip/**`, `/matching/**`, `/payments/**`
- **JWT Authentication Filter**: Validates access tokens, skips public endpoints, injects X-User-Id header
- **Error Handling**: Standardized responses for invalid/missing/expired JWT

## 📚 Project Structure

```
rido-backend/
│
├── gateway/                 # Kotlin API Gateway
│   └── JwtAuthFilter.kt
│
├── auth/                    # Java Auth Service
│   ├── controller/
│   ├── service/
│   ├── model/
│   ├── repo/
│   ├── util/
│   └── config/
│
└── infra/                   # docker-compose, env setup
```

## 🚀 Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 21
- Kotlin
- Gradle

### Running Locally

1. **Start Infrastructure**

   ```bash
   docker compose up -d
   ```

2. **Run Auth Service**

   ```bash
   cd auth
   ./gradlew bootRun
   ```

3. **Run API Gateway**
   ```bash
   cd gateway
   ./gradlew bootRun
   ```

## 🧪 API Endpoints

### Register

```http
POST /auth/register
Content-Type: application/json

{
  "username": "u1",
  "password": "p1"
}
```

### Login

```http
POST /auth/login
Content-Type: application/json

{
  "username": "u1",
  "password": "p1"
}
```

### Refresh Token

```http
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "<your_refresh_token>"
}
```

### Get Profile

```http
GET /auth/me
Authorization: Bearer <access_token>
```

### Logout

```http
POST /auth/logout
Authorization: Bearer <access_token>
```

## 📊 Current Progress

| Service          | Status           |
| ---------------- | ---------------- |
| Auth Service     | 60% completed    |
| API Gateway      | JWT + routing ✅ |
| Matching Service | Not started      |
| Trips Service    | Not started      |
| Payments Service | Not started      |
| Infrastructure   | Partial          |

## 🔥 Milestone 2 — Upcoming

### Immediate Priorities

- JTI blacklist implementation
- Device/session model
- Integration tests (Testcontainers)

### Core Ride-Hailing Systems

- **Matching Service**: Redis GEO + Kafka for driver-rider matching
- **Trips Service**: State machine for trip lifecycle
- **Payments Service**: Retry logic + Dead Letter Queue

## 🔒 Security Highlights

- **Stateless JWT Authentication**: No server-side session storage
- **Argon2id Password Hashing**: Industry-standard secure password storage
- **Refresh Token Rotation**: Reduces token compromise risk
- **Replay Attack Detection**: Automatic session revocation on suspicious activity
- **Brute-Force Protection**: IP-based rate limiting with Redis

## 🧪 Testing

Comprehensive Postman test suite covering:

- User registration
- Login flow
- Token refresh and rotation
- Replay attack scenarios
- Brute-force lockout behavior
- Logout functionality

## 📝 License

This project is currently in development.

---

**Status**: 🏗 In Active Development | **Next Milestone**: JTI Blacklist & Session Management
