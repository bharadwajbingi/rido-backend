# Smoke Tests

Quick validation tests to ensure critical functionality works after deployments.

## Test Suite

### 01-session-limit-enforcement.sh
**Purpose:** Verify session limit enforcement (max 5 sessions)

**What it tests:**
- Creates 6 login sessions with different device IDs
- Verifies only 5 sessions remain active
- Confirms oldest session is automatically revoked
- Checks application logs for revocation events

**Expected outcome:** ✅ Exactly 5 sessions active, oldest revoked

---

### 02-timing-attack-mitigation.sh
**Purpose:** Verify timing attack mitigation prevents username enumeration

**What it tests:**
- Measures login response time for valid user + wrong password
- Measures login response time for invalid user + any password
- Verifies response times are consistent (within ±50ms)
- Confirms error messages are identical

**Expected outcome:** ✅ Constant-time responses, no information leakage

---

### 03-basic-auth-flow.sh
**Purpose:** Quick smoke test of core authentication functionality

**What it tests:**
- User registration
- Login with valid credentials
- Token refresh
- Logout

**Expected outcome:** ✅ All core auth operations work

---

## Running Tests

### Run All Smoke Tests
```bash
cd testing-scripts/smoke
./01-session-limit-enforcement.sh
./02-timing-attack-mitigation.sh
./03-basic-auth-flow.sh
```

### Run Individual Test
```bash
cd testing-scripts/smoke
./01-session-limit-enforcement.sh
```

---

## Success Criteria

All tests should:
- Exit with code 0 (success)
- Display ✅ for all assertions
- Complete in < 30 seconds

---

## When to Run

- ✅ After every deployment
- ✅ Before merging P0 fixes
- ✅ After infrastructure changes
- ✅ Weekly regression testing
