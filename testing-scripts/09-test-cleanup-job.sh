#!/bin/bash

# 09_verify_cleanup_job.sh
# Verifies that the cleanup job deletes expired/revoked tokens.
# NOTE: Since the job runs every 6 hours, we can't easily wait for it.
# Instead, we will manually trigger the cleanup logic via a test endpoint OR 
# we will just verify the database state before/after if we had a way to trigger it.
#
# HOWEVER, since we don't have a public endpoint to trigger cleanup, 
# this script will simulate the conditions and check DB state.

echo "----------------------------------------------------------------"
echo "TEST 09: Refresh Token Cleanup Verification (Manual DB Check)"
echo "----------------------------------------------------------------"

# 1. Create a user
U="cleanup_user_$RANDOM"
P="pass123"
echo "Creating user: $U"
curl -s -X POST "http://localhost:8080/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$U\", \"password\":\"$P\"}" > /dev/null

# 2. Login to get a token
echo "Logging in..."
LOGIN=$(curl -s -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$U\", \"password\":\"$P\"}")
RT=$(echo "$LOGIN" | jq -r '.refreshToken')

if [ "$RT" == "null" ]; then
  echo "Login failed!"
  exit 1
fi

echo "Got Refresh Token."

# 3. Manually expire this token in DB (simulate old token)
echo "Manually expiring token in DB..."
docker exec -i infra-postgres-1 psql -U rh_user -d ride_hailing -c \
"UPDATE refresh_tokens SET expires_at = NOW() - INTERVAL '1 hour' WHERE token_hash = encode(sha256('$RT'::bytea), 'hex');"

# 4. Check DB count (Should be 1 expired token)
echo "Checking DB count (should be 1)..."
COUNT_BEFORE=$(docker exec -i infra-postgres-1 psql -U rh_user -d ride_hailing -t -c \
"SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = encode(sha256('$RT'::bytea), 'hex');")
echo "Count Before Cleanup: $COUNT_BEFORE"

echo "----------------------------------------------------------------"
echo "NOTE: The cleanup job runs every 6 hours."
echo "To verify it works, you would need to wait or trigger it manually."
echo "Since we can't trigger it via curl, this script sets up the test data."
echo "You can check the logs later to see if it ran."
echo "----------------------------------------------------------------"
