# SSH Tunnel Setup Guide - EC2 to Local Infrastructure

## Overview
This guide helps you create SSH tunnels from your local laptop to EC2, allowing the Auth service on EC2 to connect to Postgres, Redis, and Vault running locally.

## Architecture
```
Laptop (Local)                    EC2 (Cloud)
┌────────────────────┐           ┌──────────────────┐
│ Postgres :5432     │◄───────┐  │                  │
│ Redis    :6379     │◄─────┐ │  │  Auth Service    │
│ Vault    :8200     │◄───┐ │ │  │  connects to:    │
└────────────────────┘    │ │ │  │  - localhost:5432│
         ▲                │ │ │  │  - localhost:6379│
         │                │ │ │  │  - localhost:8200│
         │   SSH Reverse  │ │ │  └──────────────────┘
         │   Tunnels      │ │ │
         └────────────────┴─┴─┘
```

## Prerequisites

### 1. EC2 SSH Access
- EC2 instance running
- SSH key file (e.g., `rido-ec2-key.pem`)
- EC2 public IP or hostname

### 2. Local Services Running
```bash
# Start local infrastructure
cd infra
docker compose up -d postgres redis vault
```

Verify services are running:
```bash
docker compose ps
# Should show: postgres, redis, vault all "Up"
```

## SSH Tunnel Setup

### Option 1: Manual Tunnels (For Testing)

Open **3 separate terminal windows** and run one command in each:

#### Terminal 1 - Postgres Tunnel
```bash
# Forward local Postgres (5432) to EC2's localhost:5432
ssh -i ~/.ssh/rido-ec2-key.pem \
    -R 5432:localhost:5432 \
    -N \
    ec2-user@YOUR_EC2_IP
```

#### Terminal 2 - Redis Tunnel
```bash
# Forward local Redis (6379) to EC2's localhost:6379
ssh -i ~/.ssh/rido-ec2-key.pem \
    -R 6379:localhost:6379 \
    -N \
    ec2-user@YOUR_EC2_IP
```

#### Terminal 3 - Vault Tunnel
```bash
# Forward local Vault (8200) to EC2's localhost:8200
ssh -i ~/.ssh/rido-ec2-key.pem \
    -R 8200:localhost:8200 \
    -N \
    ec2-user@YOUR_EC2_IP
```

**Explanation:**
- `-R 5432:localhost:5432` = Reverse tunnel: EC2's port 5432 → Laptop's port 5432
- `-N` = Don't execute remote commands, just forward ports
- `-i` = SSH key file

### Option 2: Single Command (All Tunnels)

```bash
ssh -i ~/.ssh/rido-ec2-key.pem \
    -R 5432:localhost:5432 \
    -R 6379:localhost:6379 \
    -R 8200:localhost:8200 \
    -N \
    ec2-user@YOUR_EC2_IP
```

### Option 3: Automated Script (Recommended for Production)

Create `tunnel-to-ec2.sh`:

```bash
#!/bin/bash

# Configuration
EC2_HOST="YOUR_EC2_IP"
EC2_USER="ec2-user"
SSH_KEY="$HOME/.ssh/rido-ec2-key.pem"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting SSH tunnels to EC2...${NC}"

# Function to create tunnel with auto-reconnect
create_tunnel() {
    while true; do
        ssh -i "$SSH_KEY" \
            -o ServerAliveInterval=60 \
            -o ServerAliveCountMax=3 \
            -o ExitOnForwardFailure=yes \
            -o StrictHostKeyChecking=no \
            -R 5432:localhost:5432 \
            -R 6379:localhost:6379 \
            -R 8200:localhost:8200 \
            -N \
            "$EC2_USER@$EC2_HOST"
        
        echo -e "${RED}Connection lost. Reconnecting in 5 seconds...${NC}"
        sleep 5
    done
}

# Run tunnel with auto-reconnect
create_tunnel
```

Make it executable:
```bash
chmod +x tunnel-to-ec2.sh
./tunnel-to-ec2.sh
```

### Option 4: Background Service (systemd on Linux/Mac)

Create `~/.config/systemd/user/ec2-tunnel.service`:

```ini
[Unit]
Description=SSH Tunnel to EC2 for Rido Infrastructure
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/ssh -i %h/.ssh/rido-ec2-key.pem \
    -o ServerAliveInterval=60 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -R 5432:localhost:5432 \
    -R 6379:localhost:6379 \
    -R 8200:localhost:8200 \
    -N \
    ec2-user@YOUR_EC2_IP
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
```

Enable and start:
```bash
systemctl --user enable ec2-tunnel.service
systemctl --user start ec2-tunnel.service
systemctl --user status ec2-tunnel.service
```

## EC2 Configuration

### 1. Enable GatewayPorts on EC2

SSH into EC2:
```bash
ssh -i ~/.ssh/rido-ec2-key.pem ec2-user@YOUR_EC2_IP
```

Edit SSH config:
```bash
sudo vim /etc/ssh/sshd_config
```

Add/modify:
```
GatewayPorts yes
```

Restart SSH:
```bash
sudo systemctl restart sshd
```

### 2. Verify Tunnels on EC2

Once tunnels are active, verify from EC2:

```bash
# Check if ports are listening
sudo netstat -tlnp | grep -E '5432|6379|8200'

# Test Postgres
psql -h localhost -p 5432 -U rh_user -d ride_hailing

# Test Redis
redis-cli -h localhost -p 6379 ping
# Should return: PONG

# Test Vault
curl http://localhost:8200/v1/sys/health
```

## GitHub Secrets Configuration

Add these secrets to your GitHub repository:

1. **`EC2_HOST`** - Your EC2 public IP or domain
2. **`EC2_SSH_KEY`** - Your EC2 SSH private key (entire content)
3. **`DB_USERNAME`** - `rh_user`
4. **`DB_PASSWORD`** - `rh_pass`
5. **`VAULT_TOKEN`** - `root` (dev mode)
6. **`ADMIN_USERNAME`** - `admin`
7. **`ADMIN_PASSWORD`** - `SuperSecretAdmin123`

## Testing the Connection

### 1. Start Local Infrastructure
```bash
cd infra
docker compose up -d postgres redis vault
```

### 2. Start SSH Tunnels
```bash
./tunnel-to-ec2.sh
# Keep this running!
```

### 3. Deploy Auth to EC2
```bash
git add .
git commit -m "Deploy auth with tunnel config"
git push origin main
```

GitHub Actions will:
- Build Docker image
- Push to ECR
- Deploy to EC2
- Auth on EC2 will connect to your local Postgres/Redis/Vault via tunnel

### 4. Verify Deployment

Check logs on EC2:
```bash
ssh -i ~/.ssh/rido-ec2-key.pem ec2-user@YOUR_EC2_IP
docker logs -f auth
```

Test endpoints:
```bash
# From anywhere
curl -k https://YOUR_EC2_IP:8443/admin/health

# Admin endpoint
curl http://YOUR_EC2_IP:9091/admin/health
```

## Troubleshooting

### Tunnel Connection Issues

**Problem:** Tunnel keeps disconnecting
```bash
# Increase keepalive settings
ssh -i ~/.ssh/rido-ec2-key.pem \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=5 \
    ...
```

**Problem:** Port already in use on EC2
```bash
# On EC2, kill existing process
sudo netstat -tlnp | grep 5432
sudo kill -9 <PID>
```

### Auth Service Issues

**Problem:** Auth can't connect to Postgres
```bash
# On EC2, verify tunnel
telnet localhost 5432

# Check firewall
sudo iptables -L -n
```

**Problem:** Connection refused
```bash
# Ensure local services are accessible
# On laptop:
netstat -an | grep -E '5432|6379|8200'
```

### Windows-Specific Setup

If using Windows (WSL2), you need to forward from WSL to Windows:

```bash
# In WSL2, get Windows host IP
export WINDOWS_HOST=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}')

# Create tunnel to EC2 via Windows host
ssh -i ~/.ssh/rido-ec2-key.pem \
    -R 5432:$WINDOWS_HOST:5432 \
    -R 6379:$WINDOWS_HOST:6379 \
    -R 8200:$WINDOWS_HOST:8200 \
    -N \
    ec2-user@YOUR_EC2_IP
```

## Cost Optimization Tips

1. **Stop EC2 when not testing**: `aws ec2 stop-instances --instance-ids i-xxxxx`
2. **Use t3.micro or t4g.micro** (free tier eligible)
3. **Don't leave tunnels running overnight** (unnecessary data transfer)
4. **Monitor data transfer** in AWS billing dashboard

## Security Notes

⚠️ **Important:** This setup is for **development/testing only!**

For production, you should:
- Use AWS RDS instead of local Postgres
- Use AWS ElastiCache instead of local Redis
- Use AWS Secrets Manager instead of Vault
- Enable mTLS between services
- Use VPC and Security Groups properly

## Quick Reference

```bash
# Start local infra
cd infra && docker compose up -d postgres redis vault

# Start tunnel
ssh -i ~/.ssh/rido-ec2-key.pem -R 5432:localhost:5432 -R 6379:localhost:6379 -R 8200:localhost:8200 -N ec2-user@YOUR_EC2_IP

# Deploy
git push origin main

# Check logs
ssh ec2-user@YOUR_EC2_IP docker logs -f auth

# Stop tunnel
# Press Ctrl+C

# Stop local infra
cd infra && docker compose down
```
