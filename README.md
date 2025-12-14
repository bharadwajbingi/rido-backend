# Rido — Real-Time Ride-Hailing Backend

> ⚠️ **PRODUCTION READINESS STATUS**: This codebase requires **4-6 weeks of critical fixes** before production deployment.  
> See [Fix Surface Map](docs/fix_surface_map.md) for complete remediation roadmap.

A production-grade microservices backend for ride-hailing platforms, inspired by Uber/Ola. Built with Spring Boot 3, Kotlin, Java 21, reactive programming, and event-driven architecture.

[![Status](https://img.shields.io/badge/Status-Active_Development-yellow)](https://github.com/bharadwajbingi/rido-backend)
[![Production Ready](https://img.shields.io/badge/Production_Ready-NO-red)](docs/fix_surface_map.md)
[![Test Coverage](https://img.shields.io/badge/Test_Coverage-0%25-red)](docs/testing_surface_map.md)
[![Security Audit](https://img.shields.io/badge/Security_Audit-CRITICAL_GAPS-red)](docs/fix_surface_map.md#critical-security-gaps)

---

## 📋 Project Status (December 2025)

**Current Phase**: Active Development + Comprehensive Analysis Complete  
**Services Operational**: 3 of 6 (Auth, Gateway, Profile)  
**Production Ready**: ❌ **NO** - Critical security fixes required  
**Test Coverage**: ❌ 0% - Test suite not implemented  
**Security Audit**: ✅ Completed - [See Critical Gaps](#-critical-security-gaps)

### 📊 Comprehensive Analysis Deliverables

This project includes exhaustive analysis documentation:

1. **[Capability Map](docs/capability_map.md)** (48 KB)
   - Complete feature inventory across all services
   - 80+ files analyzed, 23 endpoints documented
   - Security mechanisms, data models, cross-service interactions cataloged
   
2. **[Testing Surface Map](docs/testing_surface_map.md)** (26 KB)
   - 380+ required test cases identified with 90+ critical gaps
   - Categorized coverage: endpoint, security, flow, load, migration tests
   - Estimated effort: 16-20 weeks comprehensive, 4-6 weeks minimum viable
   
3. **[Fix Surface Map](docs/fix_surface_map.md)** (53 KB)
   - 60+ critical fixes documented with code examples
   - Prioritized roadmap: P0 (4-6 weeks), P1 (3-4 weeks), P2 (2-3 weeks)
   - Security patches, architectural corrections, dependency fixes

---

## 🏗️ Architecture Overview

### Monorepo Structure

```
rido-backend/
├── build-logic/           # Shared Gradle convention plugins
│   └── convention/
│       └── src/main/kotlin/
│           ├── rido.java-conventions.gradle.kts
│           └── rido.kotlin-conventions.gradle.kts
│
├── services/              # Microservices
│   ├── auth/             # Authentication Service (Java + Spring Boot)
│   ├── gateway/          # API Gateway (Kotlin + Spring Cloud Gateway)
│   └── profile/          # Profile Service (Kotlin + R2DBC)
│
├── infra/                # Infrastructure & Deployment
│   ├── docker-compose.yml
│   ├── postgres/
│   ├── redis/
│   └── vault/
│
├── docs/                 # Documentation
│   ├── capability_map.md
│   ├── testing_surface_map.md
│   ├── fix_surface_map.md
│   └── adr/             # Architecture Decision Records
│       └── 001-monorepo-structure.md
│
├── openapi/             # API Specifications
│   ├── auth-api.yaml
│   ├── gateway-api.yaml
│   └── profile-api.yaml
│
└── docker/              # Service Dockerfiles
    ├── auth.Dockerfile
    ├── gateway.Dockerfile
    └── profile.Dockerfile
```

---

## 🔧 Technology Stack

### Core Technologies

| Component | Technology | Version |
|-----------|-----------|---------|
| **Languages** | Java, Kotlin | Java 21, Kotlin 1.9+ |
| **Framework** | Spring Boot | 3.2+ |
| **Build Tool** | Gradle (Kotlin DSL) | 8.5+ |
| **API Gateway** | Spring Cloud Gateway | 4.1+ |
| **Reactive** | Spring WebFlux, R2DBC | - |

### Infrastructure

| Service | Technology | Purpose |
|---------|-----------|---------|
| **Database** | PostgreSQL | Primary data store |
| **Cache** | Redis | Rate limiting, sessions, blacklists |
| **Secrets** | HashiCorp Vault | JWT key storage, credentials |
| **Messaging** | Apache Kafka | Event-driven communication |
| **Containerization** | Docker, Docker Compose | Local development & deployment |

---

## 🚀 Services

### 1. 🔐 Auth Service (Java + Spring Boot)

**Port**: 8081 (Public), 9091 (Admin)  
**Language**: Java 21  
**Database**: PostgreSQL  
**Cache**: Redis

**Features**:
- ✅ User registration & login (Argon2id password hashing)
- ✅ RS256 JWT tokens with JWKS key rotation (Vault-backed)
- ✅ Refresh token rotation with device binding
- ✅ Session management (multi-device, revocation)
- ✅ Rate limiting (Redis-based sliding window)
- ✅ Account lockout protection (5 attempts = 30min lock)
- ✅ Token blacklisting on logout
- ✅ Admin endpoints (dual-port architecture)
- ✅ Audit logging
- ⚠️ **Missing**: Session limit enforcement, password reset, replay protection

**Critical Gaps**:
- 🔴 Session limit not enforced (config exists but no logic)
- 🔴 Timing attack vulnerability (username enumeration)
- 🔴 Hardcoded Vault token in `application.yml`

**Key Endpoints**:
```
POST   /auth/register
POST   /auth/login
POST   /auth/refresh
POST   /auth/logout
GET    /auth/me
GET    /auth/sessions
POST   /auth/sessions/revoke-all
GET    /auth/keys/jwks.json

Admin (Port 9091):
POST   /admin/login
POST   /admin/create
POST   /admin/key/rotate
GET    /admin/audit/logs
```

---

### 2. 🌐 Gateway Service (Kotlin + Spring Cloud Gateway)

**Port**: 8080  
**Language**: Kotlin  
**Cache**: Redis

**Features**:
- ✅ Centralized routing to backend services
- ✅ RS256 JWT validation (JWKS-based)
- ✅ Token blacklist checking (Redis)
- ✅ Security header injection (`X-User-ID`, `X-User-Role`)
- ✅ CORS configuration
- ⚠️ **Missing**: Circuit breakers, rate limiting, request logging

**Critical Gaps**:
- 🔴 No circuit breakers (cascading failure risk)
- 🔴 No rate limiting (DDoS vulnerability)
- 🔴 Actuator endpoints exposed (information disclosure)

**Routes**:
```
/auth/**     → Auth Service (mTLS)
/profile/**  → Profile Service (HTTP - ⚠️ no mTLS)
/actuator/** → Management endpoints (⚠️ exposed)
```

---

### 3. 👤 Profile Service (Kotlin + R2DBC)

**Port**: 8080  
**Language**: Kotlin  
**Database**: PostgreSQL (R2DBC - Reactive)  
**Messaging**: Kafka

**Features**:
- ✅ User profile management (CRUD)
- ✅ Rider address management
- ✅ Driver document upload & verification
- ✅ Admin document approval/rejection
- ✅ Kafka event publishing (profile.updated, driver.approved, driver.rejected)
- ⚠️ **Missing**: Storage service (photo upload), Kafka consumers, admin role enforcement

**Critical Gaps** (⚠️ **SEVERE SECURITY VULNERABILITIES**):
- 🔴 **Admin authorization MISSING** - Any user can approve driver documents
- 🔴 **Document ownership validation MISSING** - User A can upload documents for User B
- 🔴 Hardcoded database credentials in `application.yml`
- 🔴 Kafka trusted packages set to `*` (deserialization vulnerability)
- 🔴 No mTLS from Gateway (trusts headers implicitly)

**Key Endpoints**:
```
GET    /profile/me
PUT    /profile/me
POST   /profile/me/photo

Rider:
GET    /profile/rider/addresses
POST   /profile/rider/addresses
DELETE /profile/rider/addresses/{id}

Driver:
GET    /profile/driver/documents
POST   /profile/driver/documents

Admin:
POST   /profile/admin/driver/{id}/approve
POST   /profile/admin/driver/{id}/reject
```

---

## 🚨 Critical Security Gaps

### Priority 0 - IMMEDIATE ACTION REQUIRED

| # | Service | Issue | Risk | Fix Time | Location |
|---|---------|-------|------|----------|----------|
| 1 | **Profile** | Admin authorization bypass | ANY user can approve documents | 2 hours | `AdminController.kt` |
| 2 | **Profile** | Document ownership Missing | User A uploads for User B | 1 hour | `DriverDocumentController.kt` |
| 3 | **Auth** | Session limit not enforced | Resource exhaustion DoS | 4 hours | `AuthService.java` |
| 4 | **Gateway** | No circuit breakers | Cascading failures | 1 day | Requires Resilience4j |
| 5 | **Infrastructure** | Redis SPOF | Total auth failure if Redis down | 2 days | Redis Sentinel config |
| 6 | **All Services** | Hardcoded secrets | Credential leakage | 4 hours | `application.yml` files |

**Total P0 Fixes**: 24 critical issues requiring **4-6 weeks** of focused effort.

See [Fix Surface Map](docs/fix_surface_map.md) for complete remediation roadmap with code examples.

---

## 📊 Service Maturity Assessment

| Service | Features | Security | Stability | Tests | Production Ready? |
|---------|----------|----------|-----------|-------|-------------------|
| **Auth** | 70% | 65% | 60% | 0% | ❌ 4-6 weeks |
| **Gateway** | 60% | 70% | 40% | 0% | ❌ 2-3 weeks |
| **Profile** | 65% | **30%** | 55% | 0% | ❌ **CRITICAL GAPS** |
| **Infrastructure** | 70% | 50% | 60% | N/A | ❌ HA not configured |

---

## 🚀 Getting Started

### Prerequisites

- **Java**: JDK 21+
- **Kotlin**: 1.9+
- **Docker**: 24.0+
- **Docker Compose**: 2.20+
- **Gradle**: 8.5+ (wrapper included)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/bharadwajbingi/rido-backend.git
   cd rido-backend
   ```

2. **Start infrastructure services**
   ```bash
   cd infra
   docker compose up -d postgres redis vault kafka
   ```

3. **Build all services**
   ```bash
   cd ..
   ./gradlew build
   ```

4. **Run services**
   
   Terminal 1 - Auth Service:
   ```bash
   ./gradlew :services:auth:bootRun
   ```
   
   Terminal 2 - Gateway:
   ```bash
   ./gradlew :services:gateway:bootRun
   ```
   
   Terminal 3 - Profile Service:
   ```bash
   ./gradlew :services:profile:bootRun
   ```

5. **Verify services**
   ```bash
   # Check Auth service
   curl http://localhost:8081/actuator/health
   
   # Check Gateway
   curl http://localhost:8080/actuator/health
   
   # Check Profile service
   curl http://localhost:8080/profile/actuator/health
   ```

### Environment Setup

Copy the example environment file:
```bash
cp .env.example .env
```

Configure required variables:
```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ride_hailing
DB_USERNAME=your_username
DB_PASSWORD=your_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Vault
VAULT_URL=http://localhost:8200
VAULT_TOKEN=your_vault_token

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT
JWT_ACCESS_TTL=300000
JWT_REFRESH_TTL=604800000

# Admin Bootstrap
FIRST_ADMIN_USERNAME=admin
FIRST_ADMIN_PASSWORD=change_this_password
```

---

## 🧪 Testing

### Current Status

⚠️ **Test Coverage**: 0% - No test suite implemented

### Required Testing (from Testing Surface Map)

- **Auth Service**: 200+ test cases
  - Endpoint tests, security tests, flow tests
  - JWT validation, rate limiting, account lockout
  - Session management, key rotation
  
- **Gateway Service**: 80+ test cases
  - Routing tests, JWT validation, JWKS refresh
  - Circuit breaker tests, failover scenarios
  
- **Profile Service**: 100+ test cases
  - CRUD operations, Kafka event publishing
  - Admin authorization, document ownership
  - R2DBC transactions

**Estimated Testing Effort**: 16-20 weeks comprehensive, 4-6 weeks minimum viable

See [Testing Surface Map](docs/testing_surface_map.md) for complete test requirements.

---

## 📖 API Documentation

### OpenAPI Specifications

Detailed API specifications available in `/openapi`:
- **[auth-api.yaml](openapi/auth-api.yaml)** - Authentication API (23 endpoints)
- **[gateway-api.yaml](openapi/gateway-api.yaml)** - Gateway routes & filters
- **[profile-api.yaml](openapi/profile-api.yaml)** - Profile management API

### Example Requests

#### Register User
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "SecurePass123!"
  }'
```

#### Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "SecurePass123!"
  }'
```

Response:
```json
{
  "accessToken": "eyJraWQiOiI2MjVlYzMxOS0...",
  "refreshToken": "a1b2c3d4e5f6..."
}
```

#### Get Profile (Authenticated)
```bash
curl -X GET http://localhost:8080/profile/me \
  -H "Authorization: Bearer <access_token>"
```

---

## 🔒 Security Features

### Implemented

- ✅ **RS256 JWT**: Asymmetric signing with rotating keys (Vault-backed)
- ✅ **JWKS Integration**: Public key distribution for Gateway
- ✅ **Argon2id Password Hashing**: Industry-standard secure storage
- ✅ **Refresh Token Rotation**: One-time use, bound to device
- ✅ **Account Lockout**: 5 failed attempts = 30min lock (Redis + DB)
- ✅ **Rate Limiting**: Sliding window per IP/user (Redis)
- ✅ **Token Blacklisting**: Immediate invalidation on logout
- ✅ **Audit Logging**: All critical actions logged
- ✅ **mTLS**: Gateway ↔ Auth service (certificate-based)
- ✅ **Dual-Port Admin**: Separate admin endpoints (port 9091)

### Missing (Critical)

- ❌ Session limit enforcement (DoS risk)
- ❌ Replay protection (nonce-based)
- ❌ Timing attack mitigation (username enumeration)
- ❌ Circuit breakers (cascading failure risk)
- ❌ Admin role enforcement in Profile (severe vulnerability)
- ❌ Document ownership validation (fraud risk)
- ❌ mTLS Gateway ↔ Profile (header spoofing risk)
- ❌ Redis high availability (single point of failure)

---

## 🎯 Roadmap

### Immediate (Week 1-2) - Critical Security Fixes

1. ✅ **Profile Admin Authorization** (2 hours)
   - Add role validation to admin endpoints
   - Prevent unauthorized document approvals
   
2. ✅ **Profile Document Ownership** (1 hour)
   - Validate driver ID matches authenticated user
   - Prevent cross-user document uploads
   
3. ✅ **Remove Hardcoded Secrets** (4 hours)
   - Move Vault token to environment variable
   - Remove DB credentials from application.yml
   
4. ✅ **Timing Attack Mitigation** (4 hours)
   - Constant-time password comparison
   - Dummy user for non-existent usernames

### Short-term (Week 3-6) - Infrastructure Stability

1. ✅ **Circuit Breakers** (1 day)
   - Add Resilience4j to Gateway
   - Configure fallback behavior
   
2. ✅ **Redis High Availability** (2 days)
   - Configure Redis Sentinel
   - Update connection configs in Auth + Gateway
   
3. ✅ **Session Limit Enforcement** (4 hours)
   - Implement max-active-sessions logic
   - Auto-revoke oldest session on limit
   
4. ✅ **Gateway Rate Limiting** (4 hours)
   - Add Redis-based rate limiter
   - Protect backend from DDoS

### Medium-term (Week 7-12) - Testing & Features

1. ✅ **P0 Test Suite** (4-6 weeks)
   - 200+ critical tests implemented
   - Endpoint, security, flow tests
   
2. ✅ **Password Reset Flow** (2-3 days)
   - Email-based reset tokens
   - Secure token generation
   
3. ✅ **Storage Service** (1 day)
   - S3 integration for photo uploads
   - Pre-signed URL generation

### Long-term (Week 13+) - Production Readiness

1. ✅ Distributed tracing (OpenTelemetry)
2. ✅ Comprehensive monitoring (Prometheus + Grafana)
3. ✅ Load testing & performance tuning
4. ✅ Kubernetes deployment manifests
5. ✅ CI/CD pipeline (GitHub Actions)

---

## 📚 Documentation

### Core Documentation

- **[README.md](README.md)** - This file (project overview)
- **[HIGH_LEVEL_DESIGN.md](HIGH_LEVEL_DESIGN.md)** - Architecture & design decisions
- **[Capability Map](docs/capability_map.md)** - Complete feature inventory (48 KB)
- **[Testing Surface Map](docs/testing_surface_map.md)** - Test requirements (26 KB)
- **[Fix Surface Map](docs/fix_surface_map.md)** - Remediation roadmap (53 KB)

### Architecture Decision Records (ADRs)

- **[ADR-001](docs/adr/001-monorepo-structure.md)** - Monorepo structure decision
- **[openapi/auth-api.yaml](openapi/auth-api.yaml)** - Auth service API
- **[openapi/gateway-api.yaml](openapi/gateway-api.yaml)** - Gateway routes
- **[openapi/profile-api.yaml](openapi/profile-api.yaml)** - Profile service API

---

## 🤝 Contributing

This is currently a personal project. Contributions, issues, and feature requests are welcome once the project reaches production readiness.

### Development Workflow

1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes and test locally
3. Commit with conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`
4. Push and create pull request

---

## 📝 License

This project is currently unlicensed. All rights reserved.

---

## 📊 Project Statistics

**Lines of Code**: ~15,000+ (estimated)  
**Services**: 3 operational, 3 planned  
**Endpoints**: 23 implemented  
**Analysis Documentation**: 127 KB (3 comprehensive docs)  
**Last Updated**: December 2025

---

## ⚠️ Important Notices

### Not Production Ready

This codebase is **NOT production-ready**. Critical security vulnerabilities exist that could lead to:
- Complete authentication bypass
- Data theft and fraud
- System-wide outages
- Credential leakage

**Estimated time to production**: 4-6 weeks minimum viable, 10-14 weeks comprehensive

### Security Disclaimer

This software is provided "as is" without warranty. Do not deploy to production without implementing all P0 fixes documented in the [Fix Surface Map](docs/fix_surface_map.md).

---

**Status**: 🏗️ Active Development + Analysis Complete  
**Critical**: Security Fixes Required  
**Timeline**: 4-6 weeks to MVP  
**Next**: Fix Profile admin authorization vulnerability (2 hours)

---

Built with ❤️ using Spring Boot, Kotlin, and Java
pr test
