#!/bin/bash
# Production mTLS Health Check
# Verifies that the Auth service is reachable and healthy using Client Certificates.

# Path to Gateway Certs (acting as the client)
# Assuming running from testing-scripts/smoke
CERT_DIR="../../infra/mtls-certs"
CRT="$CERT_DIR/gateway/gateway.crt"
KEY="$CERT_DIR/gateway/gateway.key"

URL="https://localhost:8081/actuator/health"

echo "========================================================"
echo "🔐 Verifying Auth Service Health (Production mTLS Mode)"
echo "   Target: $URL"
echo "   Client Cert: $CRT"
echo "========================================================"

if [ ! -f "$CRT" ]; then
    echo "❌ Certificate file not found: $CRT"
    echo "   Ensure you are running from testing-scripts/smoke"
    exit 1
fi

# Run curl with client cert
response=$(curl -s -k --cert "$CRT" --key "$KEY" "$URL")

echo "Response:"
echo "$response"

if [[ "$response" == *"UP"* ]]; then
    echo ""
    echo "✅ SUCCESS: Auth Service is UP and accepting mTLS connections!"
    exit 0
else
    echo ""
    echo "❌ FAILURE: Could not connect or service is not UP."
    exit 1
fi
