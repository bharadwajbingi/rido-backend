#!/bin/bash
# Run test batches with pauses to avoid rate limiting

echo "=== RIDO API TEST BATCHES ==="
echo "Running 207 tests in 7 batches with 30s pause between batches"
echo ""

BATCHES_DIR="/mnt/c/Users/bhara/OneDrive/Desktop/Rido/1/rido-backend/postman-collections/batches"
RESULTS=""

run_batch() {
    local batch=$1
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Running: $batch"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    OUTPUT=$(npx newman run "$BATCHES_DIR/$batch" --delay-request 100 --suppress-exit-code 2>&1)
    PASSED=$(echo "$OUTPUT" | grep -oP 'assertions.*?\K\d+(?=\s*\|)' | head -1)
    FAILED=$(echo "$OUTPUT" | grep -oP 'failed.*?\K\d+' | tail -1)
    
    echo "$OUTPUT" | tail -20
    echo ""
    echo "Batch Result: $PASSED assertions, $FAILED failed"
    echo ""
    
    RESULTS="$RESULTS\n$batch: $FAILED failed"
}

# Batch 1: Setup & Registration
run_batch "Batch1_Setup_Registration.json"
echo "Waiting 30s for rate limit reset..."
sleep 30

# Batch 2: Login
run_batch "Batch2_Login.json"
echo "Waiting 30s..."
sleep 30

# Batch 3: Protected
run_batch "Batch3_Protected.json"
echo "Waiting 30s..."
sleep 30

# Batch 4: Refresh & JWKS
run_batch "Batch4_Refresh_JWKS.json"
echo "Waiting 30s..."
sleep 30

# Batch 5: Logout & Admin
run_batch "Batch5_Logout_Admin.json"
echo "Waiting 30s..."
sleep 30

# Batch 6: Security & Check Username
run_batch "Batch6_Security_Check.json"
echo "Waiting 30s..."
sleep 30

# Batch 7: Sessions
run_batch "Batch7_Sessions.json"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "FINAL SUMMARY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "$RESULTS"
echo ""
echo "All batches complete!"
