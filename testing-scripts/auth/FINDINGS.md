# Auth Service Test Findings

The following critical issues were identified by the test suite:

## 1. Admin Port Exposure (Critical)
- **Test ID**: `14-admin-port-tests.sh`
- **Finding**: Admin endpoints (`/admin/login`) are accessible on the Main Port (8081).
- **Risk**: Attackers can access admin interfaces without network isolation (VPN/SSH).
- **Remediation**: Configure `SecurityConfig` to restrict `/admin/**` paths to the Admin Port (9091) only or block them at the Gateway.

## 2. mTLS Enforcement Bypass (Critical)
- **Test ID**: `16-mtls-tests.sh`
- **Finding**: Connections to the Auth Service succeed (200 OK) without providing a valid Client Certificate.
- **Risk**: Unauthorized services or attackers bypassing Gateway can directly access the Auth Service.
- **Remediation**: Ensure `server.ssl.client-auth=need` is set in `application.yml` or `security.require-ssl-client-auth` is enforced in Tomcat/Spring.

## 3. Rate Limit Bypass (High)
- **Test ID**: `04-rate-limiting.sh`
- **Finding**: The 11th request in a loop (Limit: 10/min) returned `200 OK` (Success) instead of `429` (Rate Limit Block).
- **Risk**: Attackers can spam endpoints (DoS or credential stuffing) bypassing the limit completely.
- **Remediation**: Verify `RateLimiterService` filter order, Cache keys (IP extraction), and Redis connectivity.

## Validated Features
The following features are **Verified Working**:
- User Registration & Login (Functional)
- JWT Generation & Refresh
- Account Lockout (5 attempts -> Locked)
- Duplicate User Prevention (Integrity)
- Security Headers (XSS, Frame Options)
- Service Metadata Persistence (Device ID)
- Endpoint Discovery (All expected endpoints present)
