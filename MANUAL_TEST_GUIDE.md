# Manual Testing Guide for Session Limit Enforcement

## Quick Test via Postman/cURL

Since automated testing encountered issues, here's a simplified manual test you can run:

### Prerequisites
- Auth service running on `http://localhost:8091`
- Postman, cURL, or REST client

---

## Test Scenario: Session Limit Enforcement

### Step 1: Register Test User

```bash
curl -X POST http://localhost:8091/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"sessiontest","password":"Test123!"}'
```

**Expected:** Success response

---

### Step 2: Login 5 Times (Create 5 Sessions)

Run this 5 times with different device IDs:

```bash
# Login 1
curl -X POST http://localhost:8091/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sessiontest","password":"Test123!","deviceId":"dev1"}'

# Login 2 (change dev1 to dev2)
curl -X POST http://localhost:8091/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sessiontest","password":"Test123!","deviceId":"dev2"}'

# Repeat for dev3, dev4, dev5
```

**Save the access tokens from each login!**

---

### Step 3: Check Active Sessions

```bash
curl -X GET http://localhost:8091/auth/sessions \
  -H "Authorization: Bearer <ACCESS_TOKEN_FROM_LOGIN_5>"
```

**Expected:** Array with 5 sessions

---

### Step 4: Create 6th Session (Trigger Revocation)

```bash
curl -X POST http://localhost:8091/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sessiontest","password":"Test123!","deviceId":"dev6"}'
```

**Expected:** Success, new tokens returned

---

### Step 5: Verify Still Only 5 Sessions

```bash
curl -X GET http://localhost:8091/auth/sessions \
  -H "Authorization: Bearer <ACCESS_TOKEN_FROM_STEP_4>"
```

**Expected:**  
✅ Array with exactly 5 sessions  
✅ `dev1` (oldest) should NOT be in the list  
✅ `dev2` through `dev6` should be present

---

### Step 6: Verify dev1 Token Was Revoked

Try to refresh using dev1's token:

```bash
curl -X POST http://localhost:8091/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN_FROM_DEV1>","deviceId":"dev1"}'
```

**Expected:**  
❌ `401 Unauthorized`  
Message: "Refresh token revoked"

---

### Step 7: Check Application Logs

```bash
docker logs auth --tail 50 | grep "session limit"
```

**Expected:**  
You should see log entries like:
```
WARN ... User <uuid> exceeded session limit (5/5). Revoking 1 oldest session(s).
INFO ... Auto-revoked session <id> for user <uuid> (deviceId: dev1, created: ...)
```

---

### Step 8: Check Audit Logs (Database)

Connect to PostgreSQL and query:

```sql
SELECT * FROM audit_logs 
WHERE event_type = 'SESSION_REVOKED' 
ORDER BY timestamp DESC 
LIMIT 5;
```

**Expected:**  
Entry with:
- `event_type` = 'SESSION_REVOKED'
- `metadata` contains 'SESSION_LIMIT_EXCEEDED'
- `user_id` matches your test user

---

## Success Criteria

✅ Users can have exactly 5 concurrent sessions  
✅ 6th login automatically revokes oldest session  
✅ Revoked tokens cannot be refreshed  
✅ Application logs show revocation events  
✅ Audit logs record security events  

---

## If Tests Pass

The session limit enforcement is working correctly! You can mark issue #1 as **RESOLVED** and move to production deployment after completing other P0 fixes.

## If Tests Fail

Check:
1. Auth service logs for errors: `docker logs auth --tail 100`
2. Database connection: Is PostgreSQL running?
3. Build artifacts: Did the new code deploy? `docker exec auth ls -la /app/BOOT-INF/classes/com/rido/auth/service/TokenService.class`

---

**Next Steps:**  
Run these tests and report results. If successful, the P0 session limit fix is complete!
