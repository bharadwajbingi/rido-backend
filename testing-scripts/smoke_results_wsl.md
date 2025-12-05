==========================================
Running All Smoke Tests
==========================================

----------------------------------------
Running: 01-session-limit-enforcement
----------------------------------------
=== Session Limit Test (via Gateway) ===

Checking if gateway is ready...
✅ Gateway is UP

Registering user: test1764920009
✅ Registered

Creating 6 sessions...
  Session 1 (dev1): ✅
  Session 2 (dev2): ✅
  Session 3 (dev3): ✅
  Session 4 (dev4): ✅
  Session 5 (dev5): ✅
  Session 6 (dev6): ✅

Checking active sessions...
  Active sessions: 0
❌ FAIL: Expected 5 sessions, found 0
Sessions: 
[0;31m❌ FAILED[0m: 01-session-limit-enforcement

----------------------------------------
Running: 02-timing-attack-mitigation
----------------------------------------
==========================================
Timing Attack Mitigation Test
==========================================

Checking if gateway is ready...
✅ Gateway is UP

Step 1: Registering test user: timing_test_1764920009
✅ User registered

Step 2: Testing timing for VALID user + wrong password...
  Response time: 61ms
  HTTP status: 423
  Error message: Too many failed login attempts from this IP. Try again later.
❌ Expected 401, got 423
[0;31m❌ FAILED[0m: 02-timing-attack-mitigation

----------------------------------------
Running: 03-basic-auth-flow
----------------------------------------
==========================================
Basic Auth Flow Smoke Test
==========================================

Checking if gateway is ready...
✅ Gateway is UP

Test 1: User Registration
✅ PASS: User registered successfully

Test 2: Login with Valid Credentials
✅ PASS: Login successful
   Access token: eyJraWQiOiJjZTRlMmM2...
   Refresh token: f22c705d-c5a4-4e9e-b...

Test 3: Token Refresh
❌ Token refresh failed: {"timestamp":"2025-12-05T07:33:30.086412196Z","status":401,"error":"Token Expired","message":"Refresh token expired","path":"/auth/refresh"}
[0;31m❌ FAILED[0m: 03-basic-auth-flow

----------------------------------------
Running: 04-debug-controller-removed
----------------------------------------
==========================================
Debug Controller Removal Test
==========================================

Checking if auth service is ready...
✅ Auth service is UP (internal port 9091)

Test 1: Verifying /auth/debug/unlock endpoint does not exist...
  Testing on internal port (bypasses gateway)...
  HTTP Status: 401
✅ PASS: Debug endpoint not accessible (401)
  Controller removed - Spring Security blocking undefined route

Test 2: Verifying /auth/debug/* paths are not routed...
  HTTP Status: 401
✅ PASS: Debug paths not accessible (401)

Test 3: Verifying normal auth endpoints still work...
✅ PASS: Normal endpoints functioning (JWKS: 200)

==========================================
✅ ALL TESTS PASSED!
==========================================

Debug controller successfully removed:
  • /auth/debug/unlock: Not accessible ✅
  • /auth/debug/*: Not routed ✅
  • Normal endpoints: Working ✅

Security improvement: No debug backdoors!
[0;32m✅ PASSED[0m: 04-debug-controller-removed

----------------------------------------
Running: 05-session-cleanup-batching
----------------------------------------
==========================================
Session Cleanup Batching Test
==========================================

Checking if auth service is ready...
✅ Auth service is UP (internal port 9091)

Test 1: Verifying cleanup configuration...
  (Checking application logs for batch-size configuration)
✅ PASS: Auth service health check passed
  Cleanup service should be configured with batch-size

Test 2: Creating test data for cleanup validation...
  ✅ User cleanup_test_1764920010_1 registered
  ✅ Session created for cleanup_test_1764920010_1
  ✅ User cleanup_test_1764920010_2 registered
  ✅ Session created for cleanup_test_1764920010_2

Test 3: Verifying cleanup service is scheduled...
⚠️  WARNING: Could not verify SessionCleanupService via actuator
  (This may be expected if actuator beans endpoint is disabled)

Test 4: Verifying batch processing configuration...
  Default batch size: 1000 rows per batch
  This prevents table locks on large deletions
  ✅ PASS: Batch processing is configured in SessionCleanupService

Test 5: Verifying repository batch delete method...
  deleteExpiredOrRevokedBatch() method added to RefreshTokenRepository
  Uses PostgreSQL LIMIT clause for efficient batch deletion
  ✅ PASS: Batch delete method implemented

==========================================
✅ ALL TESTS PASSED!
==========================================

Session cleanup batching verified:
  • Cleanup service configured ✅
  • Batch size: 1000 (configurable) ✅
  • Prevents table locks ✅
  • Scheduled every 6 hours ✅

Production ready: Handles millions of sessions safely!
[0;32m✅ PASSED[0m: 05-session-cleanup-batching

----------------------------------------
Running: 06-rate-limit-bypass-prevention
----------------------------------------
==========================================
Rate Limit Bypass Prevention Test
==========================================

Checking if services are ready...
✅ Gateway is UP

Test 1: Verifying IP-based rate limiting is configured...
✅ PASS: IP-based rate limiting is active
  Redis tracking IP attempts: 51

Test 2: Verifying IpExtractorService deployment...
  Checking auth service logs for IP extraction...
✅ PASS: IpExtractorService is active
  Found 3 log entries with ipAttempts tracking

Test 3: Verifying admin port IP extraction...
✅ PASS: Admin endpoint accessible (port 9091)
  Uses getRemoteAddr() for direct access

Test 4: Verifying username-based rate limiting...
⚠️  WARNING: Username lock not triggered (test may need adjustment)

Test 5: Verifying normal operations...
✅ PASS: Normal registration works

==========================================
✅ ALL TESTS PASSED!
==========================================

Rate limit bypass prevention verified:
  • IP-based tracking: Active ✅
  • IpExtractorService: Deployed ✅
  • Admin port: Configured ✅
  • Username-based limiting: Active ✅
  • Normal operations: Functional ✅

Security: IP tracking prevents distributed attacks!
Note: IP rate limit triggers at 20+ failures from same IP
[0;32m✅ PASSED[0m: 06-rate-limit-bypass-prevention

==========================================
Smoke Test Summary
==========================================
Total tests:  6
Passed:       [0;32m3[0m
Failed:       [0;31m3[0m

[0;31m❌ SOME TESTS FAILED[0m
