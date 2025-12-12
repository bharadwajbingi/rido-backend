#!/bin/bash
# ============================================================================
# Master Smoke Test Runner
# Executes all numbered test scripts in order
# Handles global readiness check and environment setup
# ============================================================================

# Ensure we are in the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Global Configuration
# Global Configuration
# We use localhost because Docker Desktop handles port binding for both Windows and WSL
export AUTH_URL="http://localhost:8081"
export ADMIN_URL="http://localhost:9091"

# mTLS Certificate Configuration
CERT_DIR="../../infra/mtls-certs"
CRT="$CERT_DIR/gateway/gateway.crt"
KEY="$CERT_DIR/gateway/gateway.key"

echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}       RIDO BACKEND - SMOKE TEST SUITE       ${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo ""

# Setup Curl Wrapper for mTLS/HTTPS
echo "Setting up environment..."
if [[ "$AUTH_URL" == https* ]]; then
    if [ -f "$CRT" ]; then
        echo -e "${GREEN}🔐 mTLS Certificates found. Enabling secure mode.${NC}"
        
        # Resolve absolute paths reliably
        ABS_CRT=$(readlink -f "$CRT")
        ABS_KEY=$(readlink -f "$KEY")

        # Create a temporary 'curl' executable wrapper in PATH
        mkdir -p tmp_bin
        echo '#!/bin/bash' > tmp_bin/curl
        echo "/usr/bin/curl -k --cert \"$ABS_CRT\" --key \"$ABS_KEY\" \"\$@\"" >> tmp_bin/curl
        chmod +x tmp_bin/curl
        
        export PATH="$PWD/tmp_bin:$PATH"
        echo "   Injecting mTLS-enabled 'curl' wrapper into PATH"
    else
        echo -e "${YELLOW}⚠️  mTLS Certificates not found at $CRT${NC}"
        echo -e "${YELLOW}   Running in insecure HTTPS mode (no client certs). Might fail if mTLS enforced.${NC}"
        
        mkdir -p tmp_bin
        echo '#!/bin/bash' > tmp_bin/curl
        echo '/usr/bin/curl -k "$@"' >> tmp_bin/curl
        chmod +x tmp_bin/curl
        export PATH="$PWD/tmp_bin:$PATH"
    fi
fi

# Print resolved URLs
echo "   Auth URL:  $AUTH_URL"
echo "   Admin URL: $ADMIN_URL"

# Global Readiness Check
echo ""
echo "Checking Global Service Readiness..."
MAX_RETRIES=30
READY=false

for i in $(seq 1 $MAX_RETRIES); do
    # Try Admin port (usually http) first as it's simpler
    # Using /usr/bin/curl directly to bypass our wrapper for the initial plain HTTP check if possible, 
    # but since we modified PATH, 'curl' is the wrapper. 
    # The wrapper adds certs, which should be fine for HTTP (ignored) but let's be verbose on failure properly.
    
    if curl -s --connect-timeout 2 --max-time 2 "$ADMIN_URL/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Service is UP (Admin Port $ADMIN_URL)${NC}"
        READY=true
        break
    else
        # Fallback check for 127.0.0.1 if localhost fails (common IPv6/IPv4 binding issue)
        if curl -s --connect-timeout 2 --max-time 2 "${ADMIN_URL/localhost/127.0.0.1}/actuator/health" > /dev/null 2>&1; then
             echo -e "${GREEN}✅ Service is UP (Admin Port via 127.0.0.1)${NC}"
             READY=true
             break
        fi
    fi
    
    # Try Auth port (https)
    if curl -s --connect-timeout 2 --max-time 2 "$AUTH_URL/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Service is UP (Auth Port $AUTH_URL)${NC}"
        READY=true
        break
    fi
    
    echo -ne "   Waiting for service... ($i/$MAX_RETRIES) - Last attempt failed \r"
    sleep 2
done

if [ "$READY" = false ]; then
    echo -e "${RED}❌ Service NOT READY after 60 seconds. Aborting tests.${NC}"
    rm -rf tmp_bin
    exit 1
fi
echo ""

# Find and Run Tests(01-11)
TEST_SCRIPTS=$(ls | grep -E '^[0-9]+.*\.sh$' | sort)
FAILED_TESTS=0
PASSED_TESTS=0
FAILED_SCRIPTS=""

for script in $TEST_SCRIPTS; do
    echo -e "${BLUE}----------------------------------------------------------------------------${NC}"
    echo -e "${BLUE}Running: $script${NC}"
    echo -e "${BLUE}----------------------------------------------------------------------------${NC}"
    
    # Run in subshell, inheriting the manipulated PATH
    if bash "$script"; then
        echo -e "${GREEN}✅ PASSED: $script${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAILED: $script${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_SCRIPTS="${FAILED_SCRIPTS}\n  • ${script}"
    fi
    echo ""
    sleep 1
done

# Cleanup
rm -rf tmp_bin

# Summary
echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}       TEST SUMMARY       ${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "✅ ALL TESTS PASSED!"
    exit 0
else
    echo -e "❌ SOME TESTS FAILED:$FAILED_SCRIPTS"
    exit 1
fi
