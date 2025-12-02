# Profile Service

The Profile Service is the canonical source of truth for Rider & Driver profile data, addresses, documents, and statistics.

## Features
- **Profiles**: CRUD operations for Rider and Driver profiles.
- **Addresses**: Manage saved addresses for riders.
- **Documents**: Driver document upload and verification workflow.
- **Stats**: Track driver statistics (trips, earnings, rating).
- **Events**: Publishes Kafka events for profile updates and document status changes.
- **Security**: JWT-based authentication (via Gateway) and role-based access control.

## API Endpoints

### Profile
- `GET /profile/me` - Get current user's profile.
- `PUT /profile/me` - Update profile details.
- `POST /profile/me/photo` - Get signed URL for photo upload.

### Rider Addresses
- `GET /profile/rider/addresses` - List saved addresses.
- `POST /profile/rider/addresses` - Add a new address.
- `DELETE /profile/rider/addresses/{id}` - Delete an address.

### Driver Documents
- `GET /profile/driver/documents` - List uploaded documents.
- `POST /profile/driver/documents` - Upload document metadata.

### Admin
- `POST /profile/admin/driver/{id}/approve` - Approve a driver document.
- `POST /profile/admin/driver/{id}/reject` - Reject a driver document.

## Local Development

### Prerequisites
- Java 21
- Docker & Docker Compose
- Postgres
- Redis
- Kafka

### Running Locally
1. Start infrastructure:
   ```bash
   docker-compose up -d
   ```
2. Run the service:
   ```bash
   ./gradlew :profile:bootRun
   ```

## Configuration
See `application.yml` for configuration details. Key environment variables:
- `SPRING_R2DBC_URL`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_DATA_REDIS_HOST`
