# Audit Log Service - API Reference Guide

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
All endpoints except `/auth/login` require JWT token in the `Authorization` header:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Roles & Permissions

| Endpoint | AUDIT_WRITER | AUDITOR | ADMIN |
|----------|:---:|:---:|:---:|
| POST /auth/login | ✓ | ✓ | ✓ |
| POST /audit/events | ✓ | ✗ | ✓ |
| GET /audit/events | ✗ | ✓ | ✓ |
| GET /audit/events/{id} | ✗ | ✓ | ✓ |
| POST /audit/events/verify-chain | ✗ | ✓ | ✓ |
| POST /compliance/* | ✗ | ✗ | ✓ |

## Authentication Endpoints

### POST /auth/login
Authenticate user and receive JWT token.

**Request:**
```json
{
  "username": "string",
  "password": "string"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "username": "user123",
  "role": "AUDITOR"
}
```

**Error Responses:**
- 400: Missing or empty username/password
- 401: Invalid credentials

---

## Audit Event Endpoints

### POST /audit/events
Create a new audit event with automatic hash chain calculation.

**Request:**
```json
{
  "eventType": "USER_LOGIN",
  "actorId": "user@example.com",
  "resourceType": "USER_SESSION",
  "resourceId": "session-uuid",
  "payload": "{\"ip\": \"192.168.1.1\", \"device\": \"Chrome\"}",
  "timestamp": "2024-01-15T10:30:00"
}
```

**Response (201):**
```json
{
  "id": 123,
  "eventType": "USER_LOGIN",
  "actorId": "user@example.com",
  "resourceType": "USER_SESSION",
  "resourceId": "session-uuid",
  "payload": "{\"ip\": \"192.168.1.1\", \"device\": \"Chrome\"}",
  "timestamp": "2024-01-15T10:30:00",
  "createdAt": "2024-01-15T10:30:00.123",
  "contentHash": "a7f3e4c2b9d1f5e8a3c6b9e1d4f7a2c5b8e1d4f7a2c5b8e1d4f7a2c5b8e1d4f",
  "previousHash": "GENESIS_HASH",
  "chainPosition": 1,
  "archived": false
}
```

**Error Responses:**
- 400: Validation error (missing required fields)
- 401: Unauthorized
- 403: Insufficient permissions
- 409: Duplicate event content

---

### GET /audit/events
List all audit events with pagination.

**Query Parameters:**
- `page` (int, default: 0) - Page number (0-indexed)
- `size` (int, default: 20) - Page size (max: 100)

**Response (200):**
```json
{
  "content": [
    {
      "id": 123,
      "eventType": "USER_LOGIN",
      ...
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "size": 20,
  "number": 0,
  "sort": {...},
  "numberOfElements": 20,
  "first": true,
  "empty": false
}
```

---

### GET /audit/events/{id}
Retrieve a specific audit event with full details.

**Path Parameters:**
- `id` (Long) - Event ID

**Response (200):**
```json
{
  "id": 123,
  "eventType": "USER_LOGIN",
  "actorId": "user@example.com",
  "resourceType": "USER_SESSION",
  "resourceId": "session-uuid",
  "payload": "{\"ip\": \"192.168.1.1\"}",
  "timestamp": "2024-01-15T10:30:00",
  "createdAt": "2024-01-15T10:30:00.123",
  "updatedAt": "2024-01-15T10:30:00.123",
  "archivedAt": null,
  "contentHash": "a7f3e4c2b9d1f5e8a3c6b9e1d4f7a2c5b8e1d4f7a2c5b8e1d4f7a2c5b8e1d4f",
  "previousHash": "GENESIS_HASH",
  "chainPosition": 1,
  "archived": false,
  "redactionMetadata": null,
  "isGenesis": true,
  "isRedacted": false
}
```

**Error Responses:**
- 401: Unauthorized
- 403: Forbidden
- 404: Event not found

---

### GET /audit/events/search/by-resource
Search events by resource type and ID.

**Query Parameters:**
- `resourceType` (String, required) - Type of resource (e.g., "ACCOUNT")
- `resourceId` (String, required) - Resource identifier
- `page` (int, default: 0)
- `size` (int, default: 20)

**Example:**
```
GET /audit/events/search/by-resource?resourceType=ACCOUNT&resourceId=ACC123&page=0&size=20
```

**Response:** Paginated list of events matching criteria

---

### GET /audit/events/search/by-actor
Search events by actor (user) ID.

**Query Parameters:**
- `actorId` (String, required) - Actor/user identifier
- `page` (int, default: 0)
- `size` (int, default: 20)

---

### GET /audit/events/search/by-type
Search events by event type.

**Query Parameters:**
- `eventType` (String, required) - Event type (e.g., "USER_LOGIN", "RECORD_UPDATED")
- `page` (int, default: 0)
- `size` (int, default: 20)

---

### GET /audit/events/search/by-time-range
Search events within a timestamp range.

**Query Parameters:**
- `startTime` (DateTime, required) - ISO format: `2024-01-15T00:00:00`
- `endTime` (DateTime, required) - ISO format: `2024-01-31T23:59:59`
- `page` (int, default: 0)
- `size` (int, default: 20)

---

### POST /audit/events/verify-chain
Verify the integrity of the complete audit event chain.

**Response (200):**
```json
{
  "valid": true,
  "message": "Chain integrity verified",
  "totalEvents": 150
}
```

---

### POST /audit/events/{id}/verify
Verify a specific event's integrity in the chain.

**Path Parameters:**
- `id` (Long) - Event ID to verify

**Response (200):**
```json
{
  "valid": true,
  "message": "Event integrity verified",
  "eventId": 123
}
```

---

### POST /audit/events/{id}/archive
Archive an audit event (immutable operation).

**Path Parameters:**
- `id` (Long) - Event ID to archive

**Response (200):**
```json
{
  "id": 123,
  "eventType": "USER_LOGIN",
  ...,
  "archived": true,
  "archivedAt": "2024-01-15T10:35:00"
}
```

**Error Responses:**
- 404: Event not found
- 409: Event already archived

---

### GET /audit/events/stats/summary
Get audit log statistics.

**Response (200):**
```json
{
  "totalEvents": 150
}
```

---

## Compliance Endpoints (ADMIN Only)

### GET /compliance/reports/compliance
Generate compliance report for a date range.

**Query Parameters:**
- `startDate` (DateTime, required) - ISO format
- `endDate` (DateTime, required) - ISO format

**Response (200):**
```json
{
  "reportGenerated": "2024-01-15T10:35:00",
  "periodStart": "2024-01-01T00:00:00",
  "periodEnd": "2024-01-31T23:59:59",
  "totalEvents": 150,
  "eventTypeDistribution": {
    "USER_LOGIN": 50,
    "RECORD_UPDATED": 80,
    "USER_LOGOUT": 20
  },
  "actorActivity": {
    "user@example.com": 45,
    "admin@example.com": 105
  },
  "archivedEvents": 30
}
```

---

### GET /compliance/reports/user-audit-trail
Generate user activity audit trail.

**Query Parameters:**
- `actorId` (String, required) - User identifier
- `days` (int, default: 30) - Number of days to look back

**Response (200):**
```json
{
  "actorId": "user@example.com",
  "periodDays": 30,
  "totalActions": 45,
  "events": [...]
}
```

---

### GET /compliance/reports/resource-audit-trail
Generate resource change history.

**Query Parameters:**
- `resourceType` (String, required) - Resource type
- `resourceId` (String, required) - Resource identifier

**Response (200):**
```json
{
  "resourceType": "ACCOUNT",
  "resourceId": "ACC123",
  "totalChanges": 15,
  "lastModified": "2024-01-15T10:30:00",
  "events": [...]
}
```

---

### POST /compliance/check
Perform compliance status check.

**Response (200):**
```json
{
  "checkDate": "2024-01-15T10:35:00",
  "totalEvents": 150,
  "archivedPercentage": "20.0%",
  "isCompliant": true,
  "status": "COMPLIANT"
}
```

---

### POST /compliance/retention/apply
Apply retention policy and archive expired events.

**Query Parameters:**
- `retentionDays` (int, default: 365) - Days to retain before archiving

**Response (200):**
```json
{
  "message": "Retention policy applied",
  "archivedCount": "25"
}
```

---

### POST /compliance/redact/{eventId}
Redact sensitive fields from an event.

**Path Parameters:**
- `eventId` (Long) - Event ID to redact

**Request:**
```json
{
  "fields": ["email", "phone", "ssn"],
  "reason": "GDPR request - Data subject right to erasure",
  "redactedBy": "admin@example.com"
}
```

**Response (200):**
```json
{
  "message": "Event redacted successfully"
}
```

---

### GET /compliance/export/json
Export all audit events as JSON file.

**Response (200):**
- Content-Type: application/json
- Content-Disposition: attachment; filename=audit-events.json

**Body:** JSON array of all events

---

### GET /compliance/export/csv
Export all audit events as CSV file.

**Response (200):**
- Content-Type: text/csv
- Content-Disposition: attachment; filename=audit-events.csv

**Body:**
```
ID,Event Type,Actor ID,Resource Type,Resource ID,Timestamp,Chain Position,Archived
1,USER_LOGIN,user@example.com,USER_SESSION,session-1,2024-01-15T10:30:00,1,false
2,RECORD_UPDATED,admin@example.com,ACCOUNT,ACC123,2024-01-15T10:35:00,2,false
...
```

---

### GET /compliance/reports/audit
Generate audit report with statistics.

**Response (200):**
- Content-Type: text/plain

**Body:**
```
=== AUDIT LOG REPORT ===
Generated: 2024-01-15T10:35:00

Total Events: 150
Archived Events: 30 (20.0%)
Active Events: 120 (80.0%)
```

---

## Error Response Format

All error responses follow this standard format:

```json
{
  "status": 400,
  "message": "Validation failed",
  "error": "Bad Request",
  "timestamp": "2024-01-15T10:35:00",
  "fieldErrors": {
    "eventType": "Event type is required",
    "actorId": "Actor ID is required"
  }
}
```

Common HTTP Status Codes:
- `200` - Success
- `201` - Resource created
- `400` - Bad request (validation error)
- `401` - Unauthorized (missing or invalid token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not found
- `409` - Conflict (duplicate event)
- `500` - Internal server error

---

## Rate Limiting & Best Practices

- **Pagination**: Always use pagination for list endpoints, max size 100
- **Batch Operations**: Export endpoints process all events (consider scheduling)
- **Error Handling**: Implement exponential backoff for retries
- **Token Refresh**: JWT tokens expire after 24 hours
- **Resource Limits**: Database has 6 strategic indexes for performance

---

## Testing the API

### Using cURL
```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"auditor","password":"password"}' | jq -r '.token')

# Create event
curl -X POST http://localhost:8080/api/v1/audit/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "TEST_EVENT",
    "actorId": "test_user",
    "resourceType": "TEST",
    "resourceId": "123"
  }'
```

### Using Postman
1. Import the OpenAPI specification from `/swagger-ui.html`
2. Create a Bearer Token collection variable
3. Use `{{token}}` in Authorization header

### Using Swagger UI
Navigate to `http://localhost:8080/swagger-ui.html` for interactive API documentation.

