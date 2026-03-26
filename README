# Local Infrastructure Setup

## 🚀 Start Services

```bash
docker compose -f infra/docker-compose.yml up -d
```

## 📊 Check Status

```bash
docker compose -f infra/docker-compose.yml ps
```

All services should show **healthy**.

---

## 🔌 Services & Ports

| Service  | Port |
| -------- | ---- |
| Postgres | 5432 |
| Redis    | 6379 |
| Redpanda | 9092 |
| MinIO    | 9000 |
| Vault    | 8200 |

---

## 📦 Persistence

Persistent data is stored for:

- Postgres
- Redpanda
- MinIO

Redis is ephemeral.

---

## ⚙️ Required vs Optional

**Required:**

- Postgres
- Redpanda

**Optional:**

- Redis
- MinIO
- Vault

---

## 🧪 Fresh Start (Clean)

```bash
docker compose -f infra/docker-compose.yml down -v
docker system prune -af --volumes
```

---

## 🛠 Troubleshooting

### Check logs

```bash
docker compose -f infra/docker-compose.yml logs -f
```

### Common issues

- Port already in use → change ports in `.env`
- Service unhealthy → check logs
- Vault issues → ensure dev mode is enabled

---

## ✅ Done Criteria

- All services start with one command
- All services show `healthy`
- No manual setup required
