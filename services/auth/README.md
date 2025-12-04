# Rido Auth Service

The central authentication and authorization service for the Rido platform.

## Features

- **User Management**: Registration, Login, Profile
- **Token Management**: JWT Access & Refresh Tokens, Blacklisting
- **Security**: Rate Limiting, Account Lockout, Key Rotation
- **Admin**: Internal admin management, Bootstrap admin
- **Observability**: Prometheus metrics, OpenTelemetry tracing

## Running Locally

### Standalone
```bash
./gradlew :services:auth:bootRun
```

### Docker
```bash
cd ../../infra
docker-compose up -d auth
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Service port | `8443` |
| `POSTGRES_HOST` | Database host | `localhost` |
| `REDIS_HOST` | Redis host | `localhost` |
| `JWT_SECRET` | HS256 Secret Key | (Required) |
| `JWT_EXPIRATION_MS` | Access token TTL | `900000` (15m) |
| `JWT_REFRESH_TTL` | Refresh token TTL | `86400000` (24h) |

## API Documentation

See [openapi/auth.yaml](../../openapi/auth.yaml) for the full API specification.
