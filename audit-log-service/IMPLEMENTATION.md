# Tamper-Evident Audit Log Service - Implementation Documentation

## Overview

This is a tamper-evident audit log service implementing append-only event logging with SHA-256 hash-chain verification. The implementation covers the planned Scenario A, B, and C capabilities; the Git history is the source of truth for commit provenance.

## Architecture

### Technology Stack
- **Language**: Java 21 LTS
- **Framework**: Spring Boot 3.3.1
- **Database**: PostgreSQL (production) / H2 (dev/test)
- **Authentication**: JWT (HS256)
- **Migration**: Flyway
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Documentation**: OpenAPI/Swagger

### Design Pattern
- **Layered Architecture**: Controller → Service → Repository → Entity
- **Security Model**: Role-Based Access Control (RBAC) with three roles
- **Cryptography**: SHA-256 hash chain with tamper detection
- **Database**: Append-only with no update/delete operations

## Core Features

### Scenario A: Core Audit Log (Commits 1-11)
✅ **Completed**
- Append-only event logging with chain verification
- JWT-based authentication with role RBAC
- SHA-256 hash chain for tamper detection
- Pagination and flexible querying
- Event archival (immutable operation)

**Key Entities:**
- `User` - Authentication with BCrypt
- `AuditEvent` - Core audit log entry with hash chain (15 fields)
- `UserRole` - AUDIT_WRITER, AUDITOR, ADMIN

**Key Services:**
- `AuditEventService` - Event CRUD and queries
- `ChainVerificationService` - Hash chain verification
- `AuditEventQueryService` - Advanced filtering
- `JwtService` - Token generation and validation

**REST Endpoints:**
- `POST /api/v1/auth/login` - User authentication
- `POST /api/v1/auth/register` - Create a user (ADMIN only)
- `POST /api/v1/audit/events` - Create event
- `GET /api/v1/audit/events` - List with pagination
- `GET /api/v1/audit/events/{id}` - Event details
- `GET /api/v1/audit/events/search/*` - Advanced search
- `POST /api/v1/audit/events/verify-chain` - Chain verification
- `POST /api/v1/audit/events/{id}/verify` - Event verification

### Scenario B: Extended Features (Commits 12-17)
✅ **Completed**
- Retention policies with automatic archival
- Field-level redaction of sensitive data
- Event export in JSON/CSV formats
- Audit reporting

**New Entities:**
- `RetentionPolicy` - Retention rules with automatic scheduling
- `AuditEventRedaction` - Redaction history and metadata

**New Services:**
- `RetentionPolicyService` - Scheduled archival (@Scheduled cron)
- `RedactionService` - Field-level masking with history
- `ExportService` - JSON, CSV, and report generation

**Compliance Endpoints:**
- `POST /api/v1/compliance/retention/apply` - Apply retention
- `POST /api/v1/compliance/redact/{eventId}` - Redact fields
- `GET /api/v1/compliance/export/json` - Export as JSON
- `GET /api/v1/compliance/export/csv` - Export as CSV
- `GET /api/v1/compliance/reports/audit` - Generate report

### Scenario C: Compliance Reporting (Commits 18-24)
✅ **Completed**
- Compliance reports with date ranges
- User activity audit trails
- Resource change history
- Compliance status checks

**Services:**
- `ComplianceReportingService` - Reports and trails

**Additional Endpoints:**
- `GET /api/v1/compliance/reports/compliance` - Compliance report
- `GET /api/v1/compliance/reports/user-audit-trail` - User activity
- `GET /api/v1/compliance/reports/resource-audit-trail` - Resource history
- `POST /api/v1/compliance/check` - Compliance check

## Database Schema

### Core Tables
- **users**: Authentication and authorization
- **audit_events**: Main audit log (15 columns + indexes)
- **retention_policies**: Retention rules
- **audit_event_redactions**: Redaction history

### Key Indexes (6 performance indexes on audit_events)
1. `idx_audit_events_chain_position` - Unique chain ordering
2. `idx_audit_events_actor_id` - Actor queries
3. `idx_audit_events_resource_type_id` - Resource queries
4. `idx_audit_events_event_type` - Event type filtering
5. `idx_audit_events_timestamp` - Time-based queries
6. `idx_audit_events_archived` - Archive status filtering

## Security

### Authentication
- JWT tokens (HS256) with 24-hour expiry
- `JWT_SECRET` must be provided and contain at least 32 bytes; the application does not use a production fallback secret
- `JwtAuthenticationFilter` intercepts all requests
- `CustomUserDetailsService` loads users from database
- A first administrator can be created only when no ADMIN exists and both `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` are set. The password is BCrypt-hashed before persistence.
- Login throttling limits a username to five failed attempts per 15-minute window (per application instance).

### Authorization
Three roles with specific permissions:
- **AUDIT_WRITER**: Can create events (`POST /audit/events`)
- **AUDITOR**: Can read/verify events (all `GET /audit/*`)
- **ADMIN**: Full access including redaction, export, compliance
- `POST /api/v1/auth/register` is ADMIN-only; anonymous users cannot self-register or choose an ADMIN role.

### Cryptography
- SHA-256 hashing for content integrity
- Chain verification: previousHash → contentHash continuity
- Duplicate event detection via content hash uniqueness
- GENESIS_HASH constant for first event

## Testing

### Test Coverage
- **Unit Tests**: 40+ tests for services and entities
- **Integration Tests**: 30+ tests for repositories and APIs
- **Controller Tests**: 20+ tests for REST endpoints
- **Latest local verification**: 144 tests passed, 0 failures, 0 errors

### Test Profiles
- `application-test.properties` - H2 in-memory with test configuration
- Testcontainers for PostgreSQL (optional)
- @Transactional rollback for database isolation

## API Documentation

### OpenAPI/Swagger
- Documentation available at `/swagger-ui.html`
- Auto-generated from `@Operation` and `@ApiResponse` annotations
- Includes authentication requirements and role-based access

### Standard Response Formats
- Success: HTTP 200/201 with response DTO
- Errors: Standard JSON error response with status, message, fields
- Pagination: Page with content, totalElements, totalPages, number

## Deployment

### Environment Variables
```bash
JWT_SECRET=your-secret-key-here
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=strong-one-time-bootstrap-password
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/audit_log_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
```

### Build and Run
```bash
mvn clean package
java -jar target/audit-log-service-*.jar
```

### Docker
```bash
docker build -t audit-log-service .
docker run -e JWT_SECRET=secret -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/audit_log_db audit-log-service
```

## Maintenance

### Scheduled Tasks
- Retention policy application: Daily at 2 AM (configurable via cron)
- Event archival: Automatic for events older than retention period

### Monitoring
- SLF4J logging with DEBUG/INFO levels
- Key log points for event creation, verification, redaction
- Audit trail for compliance changes

### Performance Considerations
- Pagination with max size 100 to prevent large result sets
- 6 strategic indexes for common query patterns
- Batch processing support for bulk operations
- Connection pooling via HikariCP (10 max connections)

## Commit History (24 Commits)

### Commits 1-5: Core Implementation
1. ✅ Project Setup - Dependencies, application properties, main class
2. ✅ Database Configuration - JPA, Flyway migrations, schema
3. ✅ Security Foundation - JWT, authentication, BCrypt
4. ✅ Core Entities - AuditEvent, DTOs, repositories
5. ✅ Write/Verify APIs - Event creation, verification, archival

### Commits 6-11: Scenario A Completion
6. ✅ Extended Query Capabilities - Filter criteria, advanced queries
7. ✅ Advanced Filtering - Query service, complex criteria
8. ✅ Scenario A - Service Tests
9. ✅ Scenario A - Controller Tests
10. ✅ Scenario A - Integration Tests
11. ✅ Scenario A - Documentation

### Commits 12-17: Scenario B - Extended Features
12. ✅ Scenario B - Retention Policy Service
13. ✅ Scenario B - Redaction Service
14. ✅ Scenario B - Export Service
15. ✅ Scenario B - Controller Endpoints
16. ✅ Scenario B - Tests
17. ✅ Scenario B - Documentation

### Commits 18-24: Scenario C - Compliance & Finalization
18. ✅ Scenario C - Compliance Reporting Service
19. ✅ Scenario C - Compliance Controller
20. ✅ Scenario C - Audit Trail Service
21. ✅ Scenario C - Tests
22. ✅ Scenario C - Documentation
23. ✅ Performance Optimization & Caching
24. ✅ Final Integration & Cleanup

## Usage Examples

### Create Audit Event (AUDIT_WRITER)
```bash
curl -X POST http://localhost:8282/api/v1/audit/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_LOGIN",
    "actorId": "user123",
    "resourceType": "USER_SESSION",
    "resourceId": "session456",
    "payload": "{\"ip\": \"192.168.1.1\"}"
  }'
```

### Query Events (AUDITOR)
```bash
curl http://localhost:8282/api/v1/audit/events?page=0&size=20 \
  -H "Authorization: Bearer $TOKEN"
```

### Verify Chain (AUDITOR)
```bash
curl -X POST http://localhost:8282/api/v1/audit/events/verify-chain \
  -H "Authorization: Bearer $TOKEN"
```

### Redact Event (ADMIN)
```bash
curl -X POST http://localhost:8282/api/v1/compliance/redact/123 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fields": ["email", "phone"],
    "reason": "GDPR request"
  }'
```

### Generate Compliance Report (ADMIN)
```bash
curl "http://localhost:8282/api/v1/compliance/reports/compliance?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59" \
  -H "Authorization: Bearer $TOKEN"
```

## Troubleshooting

### Chain Verification Fails
- Check that previousHash values match previous event's contentHash
- Verify database wasn't directly modified (append-only violated)
- Use `POST /api/v1/audit/events/{id}/verify` for specific event

### JWT Token Invalid
- Ensure JWT_SECRET environment variable is set
- Verify token hasn't expired (24 hour validity)
- Check Authorization header format: "Bearer {token}"

### Retention Policy Not Applying
- Verify `@EnableScheduling` is active in main class
- Check cron configuration in RetentionPolicyService
- Review application logs for scheduling errors

## Future Enhancements

## Current integrity and data-handling controls

- Complete-chain verification recomputes every stored event content hash before checking its chain link and position.
- Redaction metadata is stored separately in `audit_event_redactions`; the original hash-protected event payload is not updated.
- API responses and JSON exports mask fields recorded for redaction with `***REDACTED***`.
- Exports/reports are bounded by `app.audit.query.max-export-size` (default 10,000); retention archives records in `app.audit.retention.batch-size` batches (default 500).
- Audit appends use a serializable transaction and a pessimistic lock on the current chain tail. A multi-node deployment should still validate this approach with the target production database.

- Implement distributed tracing (Spring Cloud Sleuth)
- Add multi-tenant support per organization
- Implement event streaming (Kafka/RabbitMQ)
- Add full-text search capability
- Implement time-series analytics
- Add webhook notifications for compliance events
- Implement asymmetric cryptography (RSA) for chain signing
- Add database encryption at rest

---

**Current verification**: `./mvnw.cmd test` completed with 144 tests passed, 0 failures, and 0 errors. Production deployment still requires a configured `JWT_SECRET` and a production database configuration.
