# Tailscale Setup Guide - Production-Grade Mesh VPN

## Why Tailscale > SSH Tunnels

| Feature | SSH Tunnels | Tailscale |
|---------|-------------|-----------|
| **Stability** | Breaks on network changes | Auto-reconnects |
| **Performance** | Single tunnel bottleneck | Peer-to-peer mesh |
| **Security** | Manual key management | Zero-trust, auto-rotating keys |
| **Simplicity** | Complex scripts needed | Install & forget |
| **Production-Ready** | No | Yes ✅ |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Tailscale Network                         │
│                    (100.x.x.x private IPs)                   │
│                                                              │
│  ┌──────────────────────┐       ┌─────────────────────┐    │
│  │  Your Laptop         │       │  AWS EC2 (Account-1)│    │
│  │  100.64.1.5          │       │  100.64.2.10        │    │
│  │                      │       │                      │    │
│  │  ├─ Postgres :5432   │◄──────┤  Auth Service       │    │
│  │  ├─ Redis    :6379   │◄──────┤  (stateless)        │    │
│  │  ├─ Vault    :8200   │◄──────┤                     │    │
│  │  └─ Kafka    :9092   │◄──────┤  Port 8443, 9091    │    │
│  └──────────────────────┘       └─────────────────────┘    │
│                                                              │
│  Services use Tailscale private IPs in env vars             │
│  e.g., DB_HOST=100.64.1.5 (not localhost!)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Install Tailscale

### On Your Laptop (Windows/WSL2)

#### 1. Install Tailscale on Windows
```powershell
# Download and install from: https://tailscale.com/download/windows
# Or use winget:
winget install tailscale.tailscale
```

#### 2. Start Tailscale
```powershell
# Open Tailscale from Start Menu
# Click "Log in" and authenticate with Google/GitHub
```

#### 3. Get Your Tailscale IP
```powershell
tailscale ip -4
# Example output: 100.64.1.5
```

Save this IP - this is your laptop's **private Tailscale IP**.

### On EC2 (Amazon Linux 2)

#### 1. SSH into EC2
```bash
ssh -i ~/.ssh/rido-ec2-key.pem ec2-user@YOUR_EC2_PUBLIC_IP
```

#### 2. Install Tailscale
```bash
# Download and install
curl -fsSL https://tailscale.com/install.sh | sh

# Start Tailscale
sudo tailscale up

# You'll get a URL like: https://login.tailscale.com/a/xxxxx
# Copy this URL and open in your browser to authenticate
```

#### 3. Get EC2 Tailscale IP
```bash
tailscale ip -4
# Example output: 100.64.2.10
```

#### 4. Enable IP Forwarding (Optional, for subnet routing)
```bash
echo 'net.ipv4.ip_forward = 1' | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

---

## Phase 2: Configure Firewall Rules

### On Laptop (Allow EC2 to access services)

#### Windows Firewall
```powershell
# Allow Postgres
New-NetFirewallRule -DisplayName "Tailscale-Postgres" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5432

# Allow Redis
New-NetFirewallRule -DisplayName "Tailscale-Redis" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 6379

# Allow Vault
New-NetFirewallRule -DisplayName "Tailscale-Vault" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8200

# Allow Kafka
New-NetFirewallRule -DisplayName "Tailscale-Kafka" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 9092
```

#### WSL2 Port Forwarding (if services run in WSL2)
```powershell
# Add to your PowerShell profile or run on startup
$WSL_IP = (wsl hostname -I).Trim()
netsh interface portproxy add v4tov4 listenport=5432 listenaddress=0.0.0.0 connectport=5432 connectaddress=$WSL_IP
netsh interface portproxy add v4tov4 listenport=6379 listenaddress=0.0.0.0 connectport=6379 connectaddress=$WSL_IP
netsh interface portproxy add v4tov4 listenport=8200 listenaddress=0.0.0.0 connectport=8200 connectaddress=$WSL_IP
netsh interface portproxy add v4tov4 listenport=9092 listenaddress=0.0.0.0 connectport=9092 connectaddress=$WSL_IP
```

### On EC2 (Allow outbound to Tailscale)

No configuration needed! Tailscale handles it automatically.

---

## Phase 3: Test Connectivity

### From EC2 → Laptop Services

```bash
# SSH into EC2
ssh -i ~/.ssh/rido-ec2-key.pem ec2-user@YOUR_EC2_PUBLIC_IP

# Replace with YOUR laptop's Tailscale IP
LAPTOP_IP="100.64.1.5"  # Get this from: tailscale ip -4

# Test Postgres
telnet $LAPTOP_IP 5432
# Or
nc -zv $LAPTOP_IP 5432

# Test Redis
redis-cli -h $LAPTOP_IP -p 6379 ping
# Expected: PONG

# Test Vault
curl http://$LAPTOP_IP:8200/v1/sys/health
# Expected: JSON response

# Test Kafka (if installed)
nc -zv $LAPTOP_IP 9092
```

---

## Phase 4: Update GitHub Actions

Now update your GitHub Actions to use **Tailscale IPs** instead of `localhost`:

### Update `.github/workflows/auth-ecr.yml`

```yaml
# In the "Deploy on EC2" step, change environment variables:

-e SPRING_DATASOURCE_URL="jdbc:postgresql://100.64.1.5:5432/ride_hailing?currentSchema=auth" \
-e REDIS_HOST=100.64.1.5 \
-e SPRING_DATA_REDIS_HOST=100.64.1.5 \
-e SPRING_CLOUD_VAULT_URI=http://100.64.1.5:8200 \
```

Or better yet, use **GitHub Secrets**:

```yaml
-e POSTGRES_HOST="${{ secrets.TAILSCALE_LAPTOP_IP }}" \
-e REDIS_HOST="${{ secrets.TAILSCALE_LAPTOP_IP }}" \
-e VAULT_HOST="${{ secrets.TAILSCALE_LAPTOP_IP }}" \

# Then construct URLs in deployment script:
-e SPRING_DATASOURCE_URL="jdbc:postgresql://${POSTGRES_HOST}:5432/ride_hailing?currentSchema=auth" \
```

---

## Phase 5: GitHub Secrets Setup

Add to your GitHub repository secrets:

```
TAILSCALE_LAPTOP_IP = 100.64.1.5  (your laptop's Tailscale IP)
DB_USERNAME = rh_user
DB_PASSWORD = rh_pass
VAULT_TOKEN = root
ADMIN_USERNAME = admin
ADMIN_PASSWORD = SuperSecretAdmin123
```

---

## Production-Grade Benefits

### ✅ What Makes This Production-Grade

1. **Zero-Trust Security**
   - All traffic encrypted (WireGuard)
   - No exposed ports to internet
   - MagicDNS for easy hostname resolution

2. **High Availability**
   - Auto-reconnects on network changes
   - Direct peer-to-peer when possible
   - Relay fallback if needed

3. **Cloud-Agnostic**
   - Works across any cloud (AWS, GCP, Azure)
   - Works with on-prem servers
   - Same config everywhere

4. **Easy Migration Path (Phase 2)**
   ```bash
   # Today (Phase 1)
   DB_HOST=100.64.1.5  # Laptop Tailscale IP
   
   # Tomorrow (Phase 2) - Just change env var!
   DB_HOST=rido-db.xxxxx.rds.amazonaws.com  # AWS RDS
   
   # NO CODE CHANGES! 🎉
   ```

---

## Advanced: Tailscale MagicDNS (Optional)

Enable MagicDNS in Tailscale admin panel, then use **hostnames** instead of IPs:

```yaml
# Instead of:
POSTGRES_HOST=100.64.1.5

# Use:
POSTGRES_HOST=my-laptop.tailnet-name.ts.net
```

**Benefits:**
- IP changes don't break config
- More readable
- Auto-updated DNS

---

## Troubleshooting

### Issue: Can't connect from EC2 to laptop

**Check Tailscale status:**
```bash
# On both laptop and EC2
tailscale status
```

**Ping test:**
```bash
# From EC2
ping 100.64.1.5  # Your laptop IP
```

**Check firewall:**
```bash
# On laptop (Windows)
Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*Tailscale*"}
```

### Issue: WSL2 services not accessible

**Port forwarding script** (run on Windows):
```powershell
# save as forward-wsl-ports.ps1
$WSL_IP = (wsl hostname -I).Trim()
write-host "WSL IP: $WSL_IP"

@(5432, 6379, 8200, 9092) | ForEach-Object {
    $port = $_
    netsh interface portproxy delete v4tov4 listenport=$port listenaddress=0.0.0.0
    netsh interface portproxy add v4tov4 listenport=$port listenaddress=0.0.0.0 connectport=$port connectaddress=$WSL_IP
    Write-Host "Forwarded port $port"
}
```

Run on every Windows restart:
```powershell
.\forward-wsl-ports.ps1
```

---

## Cost Comparison

| Setup | Cost/month |
|-------|------------|
| **Phase 1 (Tailscale + Local Infra)** | **$0** |
| - EC2 t3.micro (free tier) | $0 |
| - Tailscale (free tier, up to 3 users) | $0 |
| - Local Postgres/Redis/Vault | $0 |
| **Phase 2 (AWS Managed)** | **~$50-100** |
| - RDS db.t3.micro | ~$15 |
| - ElastiCache cache.t3.micro | ~$13 |
| - MSK (cheapest) | ~$75 |

---

## Quick Start Commands

```bash
# 1. On Laptop (for both Windows AND wsl)
# Install Tailscale (Windows): https://tailscale.com/download/windows
# Get your IP
tailscale ip -4

# Start local services
cd infra
docker compose up -d postgres redis vault

# 2. On EC2
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
tailscale ip -4

# Test connectivity
telnet <LAPTOP_IP> 5432

# 3. Update GitHub Secrets
# TAILSCALE_LAPTOP_IP = <your laptop IP>

# 4. Deploy
git push origin main
```

---

## Next Steps

1. ✅ Install Tailscale on laptop and EC2
2. ✅ Test connectivity
3. ✅ Update GitHub Actions workflow with Tailscale IPs
4. ✅ Deploy Auth service
5. 🎯 Repeat for other services (Gateway, Profile, etc.)

**Then later (Phase 2):**
- Migrate Postgres → RDS (1 env var change)
- Migrate Redis → ElastiCache (1 env var change)
- Migrate Kafka → MSK (1 env var change)

**Zero code changes!** 🚀
