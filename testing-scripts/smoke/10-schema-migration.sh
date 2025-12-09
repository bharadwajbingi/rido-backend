#!/bin/bash
# Smoke Test: Schema Migration Verification
# Verifies that Flyway has successfully managed the database schema

set -e

echo "=========================================="
echo "Schema Migration Verification"
echo "=========================================="
echo ""

# Test 1: Verify Flyway History Table Exists
echo "Test 1: Checking for flyway_schema_history table..."
HISTORY_CHECK=$(docker exec -e PGPASSWORD=rh_pass postgres psql -U rh_user -d ride_hailing -tAc \
  "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'flyway_schema_history');")

if [ "$HISTORY_CHECK" == "t" ]; then
    echo "✅ PASS: flyway_schema_history table exists"
else
    echo "❌ FAIL: flyway_schema_history table NOT found"
    exit 1
fi

# Test 2: Verify Baseline (V1) is present
echo "Test 2: Checking V1 baseline..."
V1_CHECK=$(docker exec -e PGPASSWORD=rh_pass postgres psql -U rh_user -d ride_hailing -tAc \
  "SELECT success FROM flyway_schema_history WHERE version = '0001';")

if [ "$V1_CHECK" == "t" ]; then
    echo "✅ PASS: V1 Migration (Baseline) marked as success"
else
    echo "❌ FAIL: V1 Migration not found or failed"
    exit 1
fi

# Test 3: Verify V9 (Column Removal) is present
echo "Test 3: Checking V9 migration..."
V9_CHECK=$(docker exec -e PGPASSWORD=rh_pass postgres psql -U rh_user -d ride_hailing -tAc \
  "SELECT success FROM flyway_schema_history WHERE version = '0009';")

if [ "$V9_CHECK" == "t" ]; then
    echo "✅ PASS: V9 Migration (Clean Lockout Fields) marked as success"
else
    echo "❌ FAIL: V9 Migration not found or failed"
    exit 1
fi

# Test 4: Verify Deprecated Columns are GONE
echo "Test 4: Verifying 'failed_login_attempts' column is removed from 'users'..."
COLUMN_CHECK=$(docker exec -e PGPASSWORD=rh_pass postgres psql -U rh_user -d ride_hailing -tAc \
  "SELECT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'failed_login_attempts');")

if [ "$COLUMN_CHECK" == "f" ]; then
    echo "✅ PASS: Column 'failed_login_attempts' successfully removed"
else
    echo "❌ FAIL: Column 'failed_login_attempts' still exists!"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ SCHEMA MIGRATION VERIFIED"
echo "=========================================="
