#!/bin/bash

# ==============================================================================
# VAULT INITIALIZATION SCRIPT
# ==============================================================================
# This script initializes HashiCorp Vault for the Rido backend.
# It is idempotent: safe to run multiple times.
#
# PREREQUISITES:
# - Vault container running (docker-compose up -d vault)
# - jq installed (optional, for pretty output)
# ==============================================================================

export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root'

echo "Waiting for Vault to be ready..."
until curl -s $VAULT_ADDR/v1/sys/health > /dev/null; do
    echo "Vault is not ready yet. Retrying in 2s..."
    sleep 2
done

echo "Vault is UP!"

# 1. Enable KV v2 secrets engine at 'secret/' if not already enabled
# In dev mode, 'secret/' is enabled by default as v2, but we check to be sure.
MOUNT_CHECK=$(curl -s --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/sys/mounts | grep '"secret/":')

if [ -z "$MOUNT_CHECK" ]; then
    echo "Enabling KV v2 secrets engine at 'secret/'..."
    curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
        --request POST \
        --data '{"type": "kv", "options": {"version": "2"}}' \
        $VAULT_ADDR/v1/sys/mounts/secret
else
    echo "KV v2 secrets engine already enabled at 'secret/'."
fi

# 2. Write Database Credentials (idempotent put)
# Path: secret/data/auth
# Spring Cloud Vault reads from: secret/data/{application}/{profile}
# Here we write to 'secret/data/auth' which matches application name 'auth'
echo "Writing database credentials to 'secret/data/auth'..."

curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    --data '{
        "data": {
            "spring.datasource.username": "rh_user",
            "spring.datasource.password": "rh_pass"
        }
    }' \
    $VAULT_ADDR/v1/secret/data/auth | grep "created_time" > /dev/null

if [ $? -eq 0 ]; then
    echo "✅ Database credentials written successfully."
else
    echo "⚠️  Failed to write database credentials (or no change)."
fi

# 3. Verify
echo "Verifying stored secrets..."
curl -s --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/secret/data/auth

echo ""
echo "=================================================="
echo "Vault initialization complete."
echo "=================================================="
