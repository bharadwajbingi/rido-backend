# Postman API Testing

## How to Import the Collection

1. Open Postman
2. Click **Import** button (top left)
3. Select the file: `Rido-Auth-API.postman_collection.json`
4. Click **Import**

## Collection Overview

This collection includes **5 main categories**:

### 1. Authentication
- **Register User** - Create a new user account
- **Login** - Get access & refresh tokens (auto-saves to variables)
- **Refresh Token** - Rotate tokens (auto-updates variables)
- **Logout** - Invalidate current session

### 2. Security Context Testing ⭐
- **Test Security Context (via Gateway)** - Verify JWT → header → SecurityContext flow
  - ✅ Tests userId propagation
  - ✅ Tests roles propagation with `ROLE_` prefix
  - ✅ Auto-saves user ID for subsequent requests

### 3. User Information
- **Get User Info (/me)** - Fetch current user details
- **Check Username Availability** - Verify if username is available

### 4. Session Management
- **List Active Sessions** - View all active refresh tokens
- **Revoke All Sessions** - Logout from all devices

### 5. JWKS & Keys
- **Get JWKS** - Retrieve JSON Web Key Set
- **Get Well-Known JWKS** - Standard OIDC discovery endpoint

## Usage Instructions

### Quick Start (Recommended Order)

1. **Register User** → Creates account
2. **Login** → Gets tokens (auto-saved)
3. **Test Security Context** → ✅ **PRIMARY TEST** - Verifies the entire flow
4. Explore other endpoints as needed

### Environment Variables

The collection automatically manages these variables:

| Variable | Description | Auto-Updated |
|----------|-------------|--------------|
| `base_url` | Gateway URL (default: `http://localhost:8080`) | No |
| `auth_url` | Auth service URL (default: `http://localhost:8081`) | No |
| `access_token` | JWT access token | ✅ Yes (on Login/Refresh) |
| `refresh_token` | Refresh token | ✅ Yes (on Login/Refresh) |
| `user_id` | User UUID | ✅ Yes (on Security Context test) |

### Automated Tests

All requests include automated tests that verify:
- ✅ Status codes (200, 401, etc.)
- ✅ Response structure
- ✅ Required fields present
- ✅ Token auto-extraction and saving

### Viewing Test Results

After running a request:
1. Click the **Test Results** tab at the bottom
2. Green checkmarks (✅) = tests passed
3. Red X (❌) = tests failed with details

## Security Context Testing

The **Test Security Context** request is the **most important** one. It verifies:

1. **JWT Validation in Gateway** ✅
   - Correct issuer (`rido-auth-service`)
   - Correct audience (`rido-api`)
   - Valid signature (RS256)

2. **Header Propagation** ✅
   - Gateway extracts userId and roles from JWT
   - Gateway injects `X-User-ID` and `X-User-Roles` headers

3. **SecurityContext Population in Auth** ✅
   - Auth service reads lowercase headers (`x-user-id`, `x-user-roles`)
   - SecurityContextFilter creates Authentication object
   - Roles get `ROLE_` prefix automatically

4. **Role-Based Access Control** ✅
   - `@PreAuthorize("hasRole('USER')")` works correctly
   - Response contains userId and roles

**Expected Response:**
```json
{
  "userId": "d06979e1-178d-4071-a549-b6e22899e1fe",
  "roles": [
    {
      "authority": "ROLE_USER"
    }
  ]
}
```

## Troubleshooting

### "Connection refused" errors
- Ensure services are running: `docker compose up -d`
- Wait ~10 seconds for services to fully start

### "401 Unauthorized" on secure endpoints
- Run **Login** request first to get fresh tokens
- Tokens expire after configured TTL (check logs)

### "Token validation failed"
- Ensure Gateway can reach Auth service for JWKS
- Check Gateway logs: `docker compose logs gateway`

### Tests failing
- Check response in the **Body** tab
- Review error details in **Test Results** tab
- Verify variable values in collection variables (click collection → Variables tab)

## Advanced Usage

### Running All Requests Sequentially
1. Click on the collection name
2. Click **Run** button (top right)
3. Select all requests
4. Click **Run Rido Auth Service API**

### Exporting Test Results
1. Run collection (as above)
2. Click **Export Results** button
3. Save as JSON or HTML

## Notes

- All requests use collection variables for easy environment switching
- Tests automatically extract and save tokens, no manual copying needed
- JWKS endpoints are public (no auth required)
- Session management requires `X-User-ID` header (set from Security Context test)
