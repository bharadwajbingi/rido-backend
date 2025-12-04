# Profile Service Runbook

## Deployment
The service is containerized using Docker.
```bash
docker build -t profile-service -f profile/Dockerfile .
docker run -p 8082:8082 profile-service
```

## Database Migrations
Migrations are handled by Flyway. They run automatically on application startup.
- Location: `src/main/resources/db/migration`
- To add a migration: Create `V{version}__{description}.sql`

## Troubleshooting

### Cache Invalidation
If profile data is stale, you can manually evict keys from Redis:
```bash
redis-cli DEL "user_profiles::{userId}"
```

### Kafka Events
Check the logs for `ProfileEventProducer` to verify events are being published.
Topics:
- `profile.updated`
- `driver.document.uploaded`
- `driver.approved`
- `driver.rejected`

### Rotating Keys
To rotate storage keys, update the secrets in the deployment environment (e.g., Kubernetes secrets, Render env vars) and restart the service.
