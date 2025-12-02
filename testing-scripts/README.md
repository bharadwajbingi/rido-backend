# Testing Scripts

This directory contains organized test scripts for the Rido authentication system.

## Scripts Overview

### 1. Debug & Manual Testing
- **01-debug-curl.sh** - Debug script with verbose curl output for troubleshooting
- **02-manual-security-test.sh** - Manual security context testing with detailed output

### 2. Core Security Tests
- **03-test-security-context.sh** - Automated test for security context propagation (PRIMARY TEST)
- **04-test-jwt-validation.sh** - JWT token validation and claims verification
- **05-test-refresh-rotation.sh** - Refresh token rotation and security checks

### 3. Feature-Specific Tests
- **06-test-refresh-token-hashing.sh** - Refresh token hashing and storage verification
- **07-test-keyrotation-admin.sh** - Admin-only key rotation endpoint testing
- **08-test-secure-e2e.sh** - End-to-end secure endpoint testing

## Running Tests

### Quick Start
```bash
# Run the main security context test (recommended)
bash 03-test-security-context.sh

# Run all tests sequentially
for script in *.sh; do bash "$script"; done
```

### Prerequisites
- Docker Compose services running (`docker compose up -d`)
- Services healthy (wait ~10 seconds after startup)
- `jq` installed for JSON parsing

## Expected Results

All tests should pass with ✅ indicators:
- ✅ SecurityContext OK
- ✅ Roles OK
- ✅ JWT validation successful
- ✅ Refresh token rotation working

## Troubleshooting

If tests fail:
1. Check service logs: `docker compose logs gateway auth`
2. Verify Redis is running: `docker compose ps`
3. Run debug script: `bash 01-debug-curl.sh`
4. Check manual test: `bash 02-manual-security-test.sh`

## Test Coverage

- ✅ User registration and login
- ✅ JWT generation with correct claims (iss, aud, roles)
- ✅ Gateway JWT validation and header propagation
- ✅ Security context extraction in Auth service
- ✅ Role-based access control (@PreAuthorize)
- ✅ Refresh token rotation and hashing
- ✅ Admin-only endpoints
- ✅ Token blacklisting (logout)
