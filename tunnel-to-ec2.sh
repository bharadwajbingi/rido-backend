#!/bin/bash

# =============================================================================
# SSH Tunnel Script - Connect EC2 Auth to Local Infrastructure
# =============================================================================
# 
# This script creates reverse SSH tunnels from your local machine to EC2,
# allowing the Auth service running on EC2 to connect to:
#   - Postgres (localhost:5432)
#   - Redis (localhost:6379)
#   - Vault (localhost:8200)
#
# Usage:
#   1. Update the configuration variables below
#   2. Make executable: chmod +x tunnel-to-ec2.sh
#   3. Run: ./tunnel-to-ec2.sh
#
# =============================================================================

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION - Update these values for your setup
# ─────────────────────────────────────────────────────────────────────────────

EC2_HOST="YOUR_EC2_PUBLIC_IP_HERE"        # e.g., "13.232.XX.XX" or "ec2-XX-XX-XX-XX.ap-south-1.compute.amazonaws.com"
EC2_USER="ec2-user"                       # Default for Amazon Linux
SSH_KEY="$HOME/.ssh/rido-ec2-key.pem"     # Path to your EC2 SSH key

# Ports to forward
POSTGRES_PORT=5432
REDIS_PORT=6379
VAULT_PORT=8200

# ─────────────────────────────────────────────────────────────────────────────
# Color codes for output
# ─────────────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ─────────────────────────────────────────────────────────────────────────────
# Pre-flight checks
# ─────────────────────────────────────────────────────────────────────────────

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  SSH Tunnel to EC2 - Local Infrastructure Connection      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if configuration is set
if [ "$EC2_HOST" = "YOUR_EC2_PUBLIC_IP_HERE" ]; then
    echo -e "${RED}ERROR: Please update EC2_HOST in this script!${NC}"
    echo "Edit this file and set your EC2 public IP address."
    exit 1
fi

# Check if SSH key exists
if [ ! -f "$SSH_KEY" ]; then
    echo -e "${RED}ERROR: SSH key not found at: $SSH_KEY${NC}"
    echo "Please update the SSH_KEY variable or place your key at the specified path."
    exit 1
fi

# Check key permissions
if [ "$(stat -c %a "$SSH_KEY" 2>/dev/null || stat -f %A "$SSH_KEY" 2>/dev/null)" != "600" ] && [ "$(stat -c %a "$SSH_KEY" 2>/dev/null || stat -f %A "$SSH_KEY" 2>/dev/null)" != "400" ]; then
    echo -e "${YELLOW}WARNING: SSH key has incorrect permissions${NC}"
    echo "Fixing permissions..."
    chmod 600 "$SSH_KEY"
fi

# Check if local services are running
echo -e "${YELLOW}Checking local services...${NC}"

check_port() {
    local port=$1
    local service=$2
    
    if nc -z localhost $port 2>/dev/null || timeout 1 bash -c "cat < /dev/null > /dev/tcp/localhost/$port" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} $service (port $port) is running"
        return 0
    else
        echo -e "  ${RED}✗${NC} $service (port $port) is NOT running"
        return 1
    fi
}

all_services_ok=true
check_port $POSTGRES_PORT "Postgres" || all_services_ok=false
check_port $REDIS_PORT "Redis" || all_services_ok=false
check_port $VAULT_PORT "Vault" || all_services_ok=false

if [ "$all_services_ok" = false ]; then
    echo ""
    echo -e "${YELLOW}Some services are not running locally!${NC}"
    echo -e "Start them with: ${BLUE}cd infra && docker compose up -d postgres redis vault${NC}"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}Starting SSH tunnels...${NC}"
echo -e "  Target: ${BLUE}$EC2_USER@$EC2_HOST${NC}"
echo -e "  Tunnels:"
echo -e "    • Postgres: ${YELLOW}localhost:$POSTGRES_PORT${NC} → ${BLUE}EC2:$POSTGRES_PORT${NC}"
echo -e "    • Redis:    ${YELLOW}localhost:$REDIS_PORT${NC} → ${BLUE}EC2:$REDIS_PORT${NC}"
echo -e "    • Vault:    ${YELLOW}localhost:$VAULT_PORT${NC} → ${BLUE}EC2:$VAULT_PORT${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop tunnels${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Tunnel creation function with auto-reconnect
# ─────────────────────────────────────────────────────────────────────────────

create_tunnel() {
    attempt=1
    
    while true; do
        if [ $attempt -gt 1 ]; then
            echo -e "${YELLOW}Reconnection attempt #$attempt...${NC}"
        fi
        
        ssh -i "$SSH_KEY" \
            -o ServerAliveInterval=60 \
            -o ServerAliveCountMax=3 \
            -o ExitOnForwardFailure=yes \
            -o StrictHostKeyChecking=no \
            -o UserKnownHostsFile=/dev/null \
            -o LogLevel=ERROR \
            -R $POSTGRES_PORT:localhost:$POSTGRES_PORT \
            -R $REDIS_PORT:localhost:$REDIS_PORT \
            -R $VAULT_PORT:localhost:$VAULT_PORT \
            -N \
            "$EC2_USER@$EC2_HOST"
        
        exit_code=$?
        
        if [ $exit_code -eq 0 ]; then
            echo -e "${GREEN}Tunnel closed gracefully.${NC}"
            exit 0
        elif [ $exit_code -eq 255 ]; then
            echo -e "${RED}Connection failed (exit code: $exit_code)${NC}"
            echo -e "${YELLOW}Retrying in 5 seconds...${NC}"
        else
            echo -e "${RED}Connection lost (exit code: $exit_code)${NC}"
            echo -e "${YELLOW}Retrying in 5 seconds...${NC}"
        fi
        
        sleep 5
        ((attempt++))
    done
}

# ─────────────────────────────────────────────────────────────────────────────
# Cleanup on exit
# ─────────────────────────────────────────────────────────────────────────────

cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down tunnels...${NC}"
    echo -e "${GREEN}Done!${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# ─────────────────────────────────────────────────────────────────────────────
# Start the tunnel
# ─────────────────────────────────────────────────────────────────────────────

create_tunnel
