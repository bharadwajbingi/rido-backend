# Auth Service Test Suite

## 1. Endpoint Map

| Method | URL | Description | Auth | Rate Limit |
|:--- |:--- |:--- |:--- |:--- |
| GET | /auth/check-username | Check availability | No | - |
| POST | /auth/register | Register User | No | 10/min/ip |
| POST | /auth/login | Login | No | 50/min/ip |
| POST | /auth/refresh | Refresh Token | No | 20/min/ip |
| POST | /auth/logout | Logout | Yes (Token) | - |
| GET | /auth/me | Current User | Yes | - |
| GET | /auth/sessions | List Sessions | Yes | - |
| POST | /auth/sessions/revoke-all | Revoke All | Yes | - |
| POST | /auth/sessions/{id}/revoke | Revoke One | Yes | - |
| GET | /admin/health | Health Check | No | - |
| POST | /admin/login | Admin Login | No | - |
| POST | /admin/create | Create Admin | Yes (Admin) | - |
| POST | /admin/key/rotate | Rotate Keys | Yes (Admin) | - |
| GET | /admin/audit/logs | View Logs | Yes (Admin) | - |
| GET | /auth/keys/.well-known/jwks.json| Public Keys | No | - |

## 2. Test Coverage Matrix

| Category | Scripts | Description |
|:--- |:--- |:--- |
| **Discovery** | `01-endpoint-discovery.sh` | Checks availability and status codes of all endpoints. |
| **Functional** | `02-functional-tests.sh` | Full Register -> Login -> Me -> Logout flow. |
| **Validation** | `03-input-validation.sh`, `17-json-robustness-tests.sh` | Bad inputs, large payloads, malformed JSON. |
| **Security** | `09-jwt-forgery-tests.sh`, `15-security-headers-tests.sh` | Token tampering, security headers, algorithm substitution. |
| **Rate Limiting** | `04-rate-limiting.sh` | Verifies blocking after N requests. |
| **State** | `05-lockout.sh`, `06-session-limit.sh`, `07-refresh-flow.sh` | Locking accounts, limiting sessions, token rotation. |
| **Admin** | `14-admin-port-tests.sh` | Ensures admin internal endpoints are NOT on public port. |
| **Resilience** | `10-redis-outage-tests.sh` | Instructions for manual outage testing. |
| **Integrity** | `18-data-integrity-tests.sh`, `19-cross-service-behavior-tests.sh` | Duplicates, metadata persistence. |
| **Debug** | `13-debug-endpoint-tests.sh` | Ensures no accidental exposure of actuator. |

## 3. Usage Instructions

1. **Ensure Services are Running**: `docker-compose up` or run locally.
2. **Make Scripts Executable** (if on Linux/Mac, on Windows use git bash):
   ```bash
   chmod +x *.sh
   ```
3. **Run All Tests**:
   ```bash
   ./run-all.sh
   ```
4. **Run Specific Test**:
   ```bash
   ./02-functional-tests.sh
   ```

## 4. Configuration
Modify `common.sh` if your ports differ:
- `AUTH_URL`: Default `http://localhost:8081`
- `ADMIN_URL`: Default `http://localhost:9091`
