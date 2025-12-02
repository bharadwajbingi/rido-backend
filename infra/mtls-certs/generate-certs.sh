#!/bin/bash

# ==============================================================================
# mTLS CERTIFICATE GENERATION SCRIPT
# ==============================================================================
# This script generates:
# 1. Root CA certificate for internal services
# 2. Service certificates for Gateway, Auth, and future services
# 3. All certificates are valid for 365 days
#
# IMPORTANT: 
# - Never commit private keys to Git
# - Add mtls-certs/*.key to .gitignore
# ==============================================================================

set -e

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"
DAYS_VALID=365

echo "🔒 Generating mTLS Certificates for Rido Services"
echo "=================================================="
echo ""

# Clean up existing certificates
if [ -d "$CERTS_DIR/ca" ]; then
    echo "⚠️  Removing existing certificates..."
    rm -rf "$CERTS_DIR/ca" "$CERTS_DIR/gateway" "$CERTS_DIR/auth" "$CERTS_DIR/matching" "$CERTS_DIR/trips"
fi

# Create directories
mkdir -p "$CERTS_DIR/ca"
mkdir -p "$CERTS_DIR/gateway"
mkdir -p "$CERTS_DIR/auth"
mkdir -p "$CERTS_DIR/matching"
mkdir -p "$CERTS_DIR/trips"

echo "📁 Created certificate directories"
echo ""

# ==============================================================================
# 1. Generate Root CA
# ==============================================================================
echo "🔑 Step 1: Generating Root CA..."

openssl genpkey -algorithm RSA -out "$CERTS_DIR/ca/ca.key" -pkeyopt rsa_keygen_bits:4096

openssl req -new -x509 -key "$CERTS_DIR/ca/ca.key" \
    -out "$CERTS_DIR/ca/ca.crt" \
    -days $DAYS_VALID \
    -subj "/C=IN/ST=Karnataka/L=Bangalore/O=Rido/OU=Internal Services/CN=Rido Root CA"

echo "✅ Root CA created"
echo ""

# ==============================================================================
# 2. Generate Service Certificates
# ==============================================================================

generate_service_cert() {
    SERVICE_NAME=$1
    SERVICE_DIR="$CERTS_DIR/$SERVICE_NAME"
    
    echo "🔑 Generating certificate for: $SERVICE_NAME"
    
    # Generate private key
    openssl genpkey -algorithm RSA -out "$SERVICE_DIR/${SERVICE_NAME}.key" -pkeyopt rsa_keygen_bits:2048
    
    # Generate CSR
    openssl req -new -key "$SERVICE_DIR/${SERVICE_NAME}.key" \
        -out "$SERVICE_DIR/${SERVICE_NAME}.csr" \
        -subj "/C=IN/ST=Karnataka/L=Bangalore/O=Rido/OU=Services/CN=${SERVICE_NAME}"
    
    # Create extension file for SAN
    cat > "$SERVICE_DIR/${SERVICE_NAME}.ext" << EOF
subjectAltName = DNS:${SERVICE_NAME},DNS:localhost
extendedKeyUsage = serverAuth,clientAuth
EOF
    
    # Sign with CA
    openssl x509 -req -in "$SERVICE_DIR/${SERVICE_NAME}.csr" \
        -CA "$CERTS_DIR/ca/ca.crt" \
        -CAkey "$CERTS_DIR/ca/ca.key" \
        -CAcreateserial \
        -out "$SERVICE_DIR/${SERVICE_NAME}.crt" \
        -days $DAYS_VALID \
        -extfile "$SERVICE_DIR/${SERVICE_NAME}.ext"
    
    # Clean up CSR and extension file
    rm "$SERVICE_DIR/${SERVICE_NAME}.csr" "$SERVICE_DIR/${SERVICE_NAME}.ext"
    
    echo "   ✅ $SERVICE_NAME certificate created"
}

echo "🔑 Step 2: Generating Service Certificates..."
echo ""

generate_service_cert "gateway"
generate_service_cert "auth"
generate_service_cert "matching"
generate_service_cert "trips"

echo ""
echo "=================================================="
echo "✅ Certificate Generation Complete!"
echo "=================================================="
echo ""
echo "📂 Certificate Locations:"
echo "   Root CA:  $CERTS_DIR/ca/ca.crt"
echo "   Gateway:  $CERTS_DIR/gateway/gateway.{crt,key}"
echo "   Auth:     $CERTS_DIR/auth/auth.{crt,key}"
echo "   Matching: $CERTS_DIR/matching/matching.{crt,key}"
echo "   Trips:    $CERTS_DIR/trips/trips.{crt,key}"
echo ""
echo "⚠️  IMPORTANT: Add *.key files to .gitignore!"
echo ""

# Verify certificates
echo "🔍 Verifying certificates..."
echo ""

verify_cert() {
    SERVICE=$1
    openssl verify -CAfile "$CERTS_DIR/ca/ca.crt" "$CERTS_DIR/$SERVICE/$SERVICE.crt" > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "   ✅ $SERVICE certificate is valid"
    else
        echo "   ❌ $SERVICE certificate verification failed"
    fi
}

verify_cert "gateway"
verify_cert "auth"
verify_cert "matching"
verify_cert "trips"

echo ""
echo "=================================================="
echo "🎉 All certificates generated and verified!"
echo "=================================================="
