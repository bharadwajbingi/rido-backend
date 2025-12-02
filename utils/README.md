# 🔧 Utils - Utility Scripts

This directory contains standalone utility scripts for the Rido backend.

## 📝 Scripts

### `vault-init.sh`
**Purpose:** Initialize HashiCorp Vault with database credentials

**What it does:**
- Waits for Vault to be ready
- Enables KV v2 secrets engine
- Writes database credentials to Vault
- Verifies stored secrets

**Usage:**
```bash
# Start Vault first
cd ../infra
docker-compose up -d vault

# Run init script
cd ../utils
bash vault-init.sh
```

**Environment:**
- `VAULT_ADDR`: http://127.0.0.1:8200
- `VAULT_TOKEN`: root (dev mode)

**Note:** Safe to run multiple times (idempotent)

---

### `test-hash.sh`
**Purpose:** Test refresh token hashing mechanism

**What it does:**
- Registers a test user
- Logs in to get refresh token
- Calculates SHA-256 hash locally
- Shows how to verify hash in database

**Usage:**
```bash
bash test-hash.sh
```

**Output:**
- Raw refresh token (UUID)
- Local SHA-256 hash
- Instructions for DB verification

**Note:** Useful for debugging token storage

---

## 🎯 When to Use

| **Script** | **Use Case** |
|------------|--------------|
| `vault-init.sh` | First-time Vault setup, reset credentials |
| `test-hash.sh` | Verify token hashing, debug storage issues |

---

## 📚 Related

- **Service Code**: `/auth`, `/gateway`
- **Infrastructure**: `/infra`
- **Tests**: `/testing-scripts`, `/postman-collections`
