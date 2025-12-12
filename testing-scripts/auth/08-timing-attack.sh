#!/bin/bash
source ./common.sh

section "08 - Timing Attack Analysis (Username Enumeration)"

# Note: This is a rough heuristic. Real timing attacks require statistical analysis (thousands of requests).
# We will do 5 samples each and compare averages.

RANDOM_NUM=$((RANDOM % 100000))
VALID_USER="timing_user_${RANDOM_NUM}"
PASSWORD="Password123!"

register_user "$VALID_USER" "$PASSWORD"

measure_time() {
    local user=$1
    local pass=$2
    # %{time_total} in seconds
    curl -s -w "%{time_total}" -o /dev/null -X POST "$AUTH_URL/auth/login" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$user\", \"password\": \"$pass\"}"
}

echo "Measuring Valid User (Bad Pass)..."
TOTAL_VALID=0
for i in {1..5}; do
    VAL=$(measure_time "$VALID_USER" "WrongPass")
    echo "  Run $i: ${VAL}s"
    TOTAL_VALID=$(awk "BEGIN {print $TOTAL_VALID + $VAL}")
done

echo "Measuring Invalid User..."
TOTAL_INVALID=0
for i in {1..5}; do
    VAL=$(measure_time "ghost_user_99999" "AnyPass")
    echo "  Run $i: ${VAL}s"
    TOTAL_INVALID=$(awk "BEGIN {print $TOTAL_INVALID + $VAL}")
done

AVG_VALID=$(awk "BEGIN {print $TOTAL_VALID / 5}")
AVG_INVALID=$(awk "BEGIN {print $TOTAL_INVALID / 5}")

echo "Avg Valid User (Bad Pass): ${AVG_VALID}s"
echo "Avg Invalid User:          ${AVG_INVALID}s"

DIFF=$(awk "BEGIN {print $AVG_VALID - $AVG_INVALID}")
if [ $(awk "BEGIN {print ($DIFF < 0) ? -$DIFF : $DIFF}") ]; then
    ABS_DIFF=$DIFF 
    # bash float math is hard, skipping complex logic using awk mostly
fi

# If difference is > 0.1s (100ms), it's very suspicious.
SUSPICIOUS=$(awk "BEGIN {print ($DIFF > 0.1 || $DIFF < -0.1) ? 1 : 0}")

if [ "$SUSPICIOUS" -eq 1 ]; then
    warn "Timing Difference Detected (>100ms)! Possible Username Enumeration."
else
    pass "Timing Difference < 100ms (Likely Safe)"
fi

section "Timing Analysis Complete"
