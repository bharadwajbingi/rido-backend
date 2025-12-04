# Rido Microservices - Complete Test Plan

## Services Overview

| Service | Port | Protocol | Description |
|---------|------|----------|-------------|
| Auth (Public) | 8443 | HTTPS/mTLS | User authentication |
| Auth (Admin) | 9091 | HTTP | Admin operations |
| Gateway | 8080 | HTTP | API Gateway + JWT validation |
| Profile | 8082 | HTTP | User profiles (via Gateway) |

---

## 1. AUTH SERVICE ENDPOINTS

### 1.1 Public Endpoints (No Auth Required)

| Endpoint | Method | Path | Description |
|----------|--------|------|-------------|
| Check Username | GET | `/auth/check-username` | Check username availability |
| Register | POST | `/auth/register` | Register new user |
| Login | POST | `/auth/login` | User login |
| Refresh Token | POST | `/auth/refresh` | Refresh access token |
| Logout | POST | `/auth/logout` | Logout user |
| JWKS | GET | `/auth/keys/jwks.json` | Get public keys |

### 1.2 Protected Endpoints (JWT Required)

| Endpoint | Method | Path | Description |
|----------|--------|------|-------------|
| Get Current User | GET | `/auth/me` | Get current user info |
| List Sessions | GET | `/auth/sessions` | List active sessions |
| Revoke All Sessions | POST | `/auth/sessions/revoke-all` | Revoke all sessions |
| Revoke One Session | POST | `/auth/sessions/{id}/revoke` | Revoke specific session |

### 1.3 Admin Endpoints (Port 9091)

| Endpoint | Method | Path | Description |
|----------|--------|------|-------------|
| Admin Health | GET | `/admin/health` | Health check |
| Admin Login | POST | `/admin/login` | Admin login |
| Create Admin | POST | `/admin/create` | Create new admin |
| Rotate Keys | POST | `/admin/key/rotate` | Rotate JWT signing keys |
| Audit Logs | GET | `/admin/audit/logs` | Get audit logs |

---

## 2. PROFILE SERVICE ENDPOINTS

### 2.1 Profile Management
- GET `/profile/me` - Get profile (X-User-ID required)
- PUT `/profile/me` - Update profile
- POST `/profile/me/photo` - Upload photo URL

### 2.2 Rider Addresses
- GET `/profile/rider/addresses` - Get addresses
- POST `/profile/rider/addresses` - Add address
- DELETE `/profile/rider/addresses/{id}` - Delete address

### 2.3 Driver Documents
- GET `/profile/driver/documents` - Get documents
- POST `/profile/driver/documents` - Upload document

### 2.4 Admin Document Management
- POST `/profile/admin/driver/{id}/approve` - Approve document
- POST `/profile/admin/driver/{id}/reject` - Reject document

---

## 3. TEST CASES SUMMARY

### Positive Tests (50+)
- Valid registration, login, token refresh, logout
- Session management operations
- Profile CRUD operations
- Address and document management
- Admin operations

### Negative Tests (80+)
- Missing/invalid fields
- Authentication failures
- Authorization failures
- Rate limiting
- Account lockout
- JWT attacks (tampered, expired, wrong issuer)
- Replay attacks
- Device mismatch
- Large payloads

---

## 4. TEST EXECUTION ORDER

1. Admin Login → Get admin token
2. Register User → Login → Get tokens
3. Protected Endpoints (using token)
4. Refresh Token Flow
5. Profile Operations
6. Session Management
7. Logout & Token Invalidation
8. Security Attack Tests
9. Rate Limit Tests
