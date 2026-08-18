# 3. Implementation Details — Audit Log Service

*This document is maintained throughout development. As each scenario is implemented, add details below.*

---

## Status Summary

| Scenario | Status | Completion |
|----------|--------|-----------|
| SCN-A: Core Audit Log Service | NOT STARTED | 0% |
| SCN-B: Retention and Redaction | NOT STARTED | 0% |
| SCN-C: Compliance Reporting | NOT STARTED | 0% |

---

## SCN-001: Core Audit Log Service (Scenario A)

### Scenario Overview
Implement the foundational audit log system with write, query, and chain verification capabilities.

### Requirements Covered
- REQ-001 through REQ-024

### Use Cases Implemented
- UC-001: Create Audit Event
- UC-002: Query Events
- UC-003: Verify Chain Integrity

---

### Task A1: Write API — Create Audit Event

**Requirement:** REQ-004 to REQ-011

**Implementation Status:** NOT STARTED

**Classes Involved:**
- Controller: `AuditEventController.createEvent()`
- Service: `AuditEventService.createEvent()`
- Repository: `AuditEventRepository.save()`
- Entity: `AuditEvent`
- DTOs: `CreateAuditEventRequest`, `AuditEventResponse`
- Validation: `EventTypeValidator`
- Security: JWT authentication required

**API:**
```
POST /api/v1/audit/events
Authorization: Bearer <JWT>
Content-Type: application/json

Request: CreateAuditEventRequest
Response: 201 Created, AuditEventResponse
```

**Database Changes:**
- Create `audit_events` table with fields: id, eventType, actorId, resourceType, resourceId, payload, timestamp, contentHash, previousHash, chainPosition, archived, archivedAt, redactionMetadata, createdAt, updatedAt
- Indexes: chain_position (unique), actor_id, resource_type+resource_id, event_type, timestamp, archived

**Validation:**
- V-001 to V-009 (eventType format, field lengths, JSON validity)
- Business rule: Compute SHA-256 hashes, retrieve previous record

**Security:**
- @PreAuthorize("hasRole('AUDIT_WRITER')")
- Validate JWT token
- Do not log payload or timestamp

**Exception Handling:**
- 400: Validation errors (eventType format, missing fields)
- 401: Missing/invalid JWT
- 403: Insufficient role
- 500: Hash computation failure, database error

**Unit Tests:**
- `shouldCreateEventWhenRequestIsValid()`
- `shouldReturn400WhenEventTypeIsInvalid()`
- `shouldReturn400WhenRequiredFieldIsMissing()`
- `shouldReturn401WhenJwtIsMissing()`
- `shouldReturn403WhenUserLacksAuditWriterRole()`
- `shouldComputeContentHashFromEventFields()`
- `shouldRetrievePreviousHashFromLatestRecord()`
- `shouldIncrementChainPositionSequentially()`

**Integration Tests:**
- Test full workflow: POST → validate → hash → persist → return
- Test with invalid JSON payload
- Test timestamp handling (client-supplied vs. server-assigned)

**Expected Changes:**
- New files: 12+ classes (controller, service, entity, DTOs, validators, exceptions)
- Modified files: pom.xml (add Spring Data JPA, PostgreSQL driver)
- Database: V1__initial_schema.sql (migration)

---

### Task A2: Query API — Retrieve Events

**Requirement:** REQ-012 to REQ-017

**Implementation Status:** NOT STARTED

**Classes Involved:**
- Controller: `AuditEventController.queryEvents()`
- Service: `AuditEventService.queryEvents()`
- Repository: `AuditEventRepository.findAll()` + Specifications
- Mapper: Dynamic query construction

**API:**
```
GET /api/v1/audit/events?actorId=...&resourceType=...&resourceId=...&eventType=...&from=...&to=...&page=0&size=20

Response: 200 OK, PaginatedResponse<AuditEventResponse>
```

**Database Changes:**
- None (uses existing audit_events table)
- Verify indexes: chain_position, actor_id, (resource_type, resource_id), event_type, timestamp

**Validation:**
- V-010, V-011, V-012 (pagination ranges, time ranges)
- All filters optional, AND logic when multiple provided

**Security:**
- @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
- Do not expose internal IDs in response

**Exception Handling:**
- 400: Invalid page/size/date range
- 401: Missing JWT
- 403: Insufficient role

**Unit Tests:**
- `shouldReturnEventsFilteredByActorId()`
- `shouldReturnEventsFilteredByResourceTypeAndId()`
- `shouldReturnEventsInTimeRange()`
- `shouldReturnEventsFilteredByEventType()`
- `shouldReturnPaginatedResults()`
- `shouldDefaultToPage0Size20()`
- `shouldEnforceMaxSizeLimit()`
- `shouldSortByChainPositionAscending()`

**Integration Tests:**
- Create multiple events, query with various filter combinations
- Verify pagination works correctly
- Verify sorting preserves chain order

**Expected Changes:**
- New files: AuditEventSpecifications.java, custom query logic
- Modified files: AuditEventController, AuditEventService, AuditEventRepository

---

### Task A3: Chain Verification Endpoint

**Requirement:** REQ-018 to REQ-024

**Implementation Status:** NOT STARTED

**Classes Involved:**
- Controller: `ChainVerificationController.verifyChain()`
- Service: `ChainVerificationService.verifyChain()`
- Repository: `AuditEventRepository.findCompleteChain()`
- Entity: `ChainVerification` (result object)
- DTO: `ChainVerificationResponse`

**API:**
```
GET /api/v1/audit/verify

Response: 200 OK, ChainVerificationResponse
{
  "isIntact": true/false,
  "firstInconsistency": null | chain_position,
  "violationType": null | "CONTENT_HASH_MISMATCH" | "PREVIOUS_HASH_MISMATCH" | "MISSING_RECORD",
  "violationDetails": null | "...",
  "totalRecords": count,
  "verifiedAt": timestamp
}
```

**Database Changes:**
- None
- Optimization: Consider caching verification state (optional, Scenario B enhancement)

**Algorithm:**
1. Load all records ordered by chain_position
2. For each record:
   - Compute contentHash from fields
   - Compare with stored contentHash
   - Verify previous.contentHash == current.previousHash
   - Check for gaps in chain_position
3. First mismatch: return violation details
4. If all match: return isIntact=true

**Security:**
- @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")

**Exception Handling:**
- 401: Missing JWT
- 403: Insufficient role
- 500: Database/hashing failure

**Unit Tests:**
- `shouldReturnIntactWhenChainIsValid()`
- `shouldDetectContentHashMismatch()`
- `shouldDetectPreviousHashMismatch()`
- `shouldDetectMissingRecords()`
- `shouldReturnFirstInconsistency()`
- `shouldHandleEmptyChain()`
- `shouldHandleGenesisRecord()`

**Integration Tests:**
- Create multiple events, verify integrity
- Manually tamper with database record (update hash), verify detection
- Verify performance (1000+ records)

**Expected Changes:**
- New files: ChainVerificationService.java, ChainVerificationController.java
- Modified files: Repository (add findCompleteChain query)

---

## Commit Plan for Scenario A

### Commit A1: Project setup + initial pom.xml
```
git commit -m "feat: initialize Spring Boot project with dependencies"
```

### Commit A2: Database configuration + migrations
```
git commit -m "feat: configure PostgreSQL and create audit_events schema"
```

### Commit A3: Security foundation (JWT + authentication)
```
git commit -m "feat: implement JWT authentication and authorization"
```

### Commit A4: Core entities and DTOs
```
git commit -m "feat: define AuditEvent entity and request/response DTOs"
```

### Commit A5: Write API (create event)
```
git commit -m "feat: implement Write API (POST /audit/events)"
```

### Commit A6: Query API (retrieve events with filtering)
```
git commit -m "feat: implement Query API (GET /audit/events with filters)"
```

### Commit A7: Chain verification endpoint
```
git commit -m "feat: implement Chain Verification endpoint (GET /audit/verify)"
```

### Commit A8: Comprehensive unit tests for Scenario A
```
git commit -m "test: add unit tests for audit service and hashing logic"
```

### Commit A9: Integration tests for all Scenario A APIs
```
git commit -m "test: add integration tests for Write, Query, Verify endpoints"
```

### Commit A10: Exception handling and global error handling
```
git commit -m "feat: implement global exception handler and custom exceptions"
```

### Commit A11: OpenAPI/Swagger documentation for Scenario A
```
git commit -m "docs: add OpenAPI specification for Scenario A endpoints"
```

---

## SCN-002: Retention and Redaction (Scenario B)

### Scenario Overview
Extend Scenario A with record retention policies and structured field redaction while maintaining hash chain integrity.

### Requirements Covered
- REQ-025 through REQ-038

### Use Cases Implemented (TBD)
- UC-004: Redact Sensitive Fields
- UC-005: Archive Old Records
- UC-006: Export Compliance Bundle

**Status:** NOT STARTED

**Implementation Plan:**
- Add `archived`, `archivedAt`, `redactionMetadata` fields to AuditEvent
- Implement redaction logic preserving hash chain
- Implement archive/soft-delete with retention windows
- Implement export bundle with verification metadata
- Extend verification to handle archived records correctly

---

## SCN-003: Compliance Reporting (Scenario C)

### Scenario Overview
Address the ambiguous compliance requirement by implementing compliance-specific reporting and enforcement.

### Requirements Covered
- REQ-039 through REQ-044

### Use Cases Implemented (TBD)
- UC-007: Generate Compliance Report

**Status:** NOT STARTED

**Implementation Plan:**
- Implement resource type tagging (ACCOUNT vs. other)
- Implement compliance-specific query endpoint
- Implement 7-year retention enforcement
- Implement compliance report generation
- Implement audit-ready export with regulatory metadata

---

## Test Coverage Summary

### Scenario A (Target: 80%+ coverage)

**Unit Tests (Service Layer):**
- AuditEventServiceTest: 8+ tests
- ChainVerificationServiceTest: 6+ tests
- HashingServiceTest: 4+ tests
- Security/JWT: 3+ tests

**Integration Tests (API Layer):**
- AuditEventControllerIntegrationTest: 10+ tests
- ChainVerificationIntegrationTest: 5+ tests

**Total Expected:** 40+ test cases

### Scenario B (Target: 75%+ coverage)

**Unit Tests:**
- RedactionServiceTest: 5+ tests
- ArchiveServiceTest: 4+ tests
- ExportServiceTest: 3+ tests

**Integration Tests:**
- RedactionIntegrationTest: 5+ tests
- ExportIntegrationTest: 4+ tests
- ArchiveIntegrationTest: 3+ tests

### Scenario C (Target: 70%+ coverage)

**Unit Tests:**
- ComplianceReportServiceTest: 4+ tests

**Integration Tests:**
- ComplianceIntegrationTest: 5+ tests

---

## Known Limitations (To Be Updated)

| Limitation | Scope | Mitigation | Future Enhancement |
|-----------|-------|-----------|-------------------|
| Synchronous verification (O(n)) | SCN-A | Acceptable for 1M+ records | Batch verification, caching (SCN-B) |
| No distributed tracing | All | Not required for MVP | Add Spring Cloud Sleuth |
| Single-server deployment | All | Works for < 5000 events/sec | Consider sharding by resource_id |
| No API rate limiting | All | Implement later | Add Spring Cloud Gateway |
| No audit log rotation/archival job | All | Manual for MVP | Implement scheduled archival task |

---

## Traceability Matrix

| Requirement | Scenario | API Endpoint | Service Method | Status |
|-------------|----------|-------------|-----------------|--------|
| REQ-001 | SCN-A | POST /audit/events | createEvent() | NOT STARTED |
| REQ-004 | SCN-A | POST /audit/events | createEvent() | NOT STARTED |
| REQ-012 | SCN-A | GET /audit/events | queryEvents() | NOT STARTED |
| REQ-018 | SCN-A | GET /audit/verify | verifyChain() | NOT STARTED |
| REQ-025 | SCN-B | (implicit archival) | archiveOldRecords() | NOT STARTED |
| REQ-029 | SCN-B | PATCH /audit/events/{id}/redact | redactFields() | NOT STARTED |
| REQ-035 | SCN-B | POST /audit/export | exportByResourceId() | NOT STARTED |
| REQ-039 | SCN-C | GET /compliance/report | generateReport() | NOT STARTED |

---

## Architecture Decisions (To Be Validated)

| Decision | Rationale | Risks | Validation |
|----------|-----------|-------|-----------|
| Append-only API (no update/delete) | Tamper-evidence requirement | Users may expect update capability | Document clearly; demonstrate on live defense |
| SHA-256 for hashing | Industry standard, cryptographically strong | None (unless quantum computing emerges) | Validate hash correctness in tests |
| Redaction via payload masking + metadata | Preserves chain while allowing redaction | Metadata size grows | Monitor metadata bloat |
| PostgreSQL JSONB for payload | Schema-less storage, queryable | Slower than VARCHAR | Benchmark before production |
| Synchronous verification walk | Simplicity, no eventual consistency | O(n) performance | Acceptable for MVP; optimize in SCN-B |

---

## Performance Considerations

| Metric | Target | Implementation |
|--------|--------|-----------------|
| Write latency (p95) | < 100ms | Connection pooling, batch hashing |
| Throughput | 1000+ events/sec | Index on chain_position, payload JSONB |
| Query latency (filtered) | < 500ms (p95) | Composite indexes on (resource_type, resource_id) |
| Verification latency | < 2s (1M records) | Sequential scan; consider async for large datasets |

---

## Security Checklist (To Be Validated)

| Check | Status | Evidence |
|-------|--------|----------|
| No passwords in logs | TBD | Code review of LoggingUtils |
| No JWT tokens in logs | TBD | Code review of SecurityFilter |
| All endpoints require JWT | TBD | @PreAuthorize on all controllers except /auth/login |
| Role-based authorization enforced | TBD | Method-level @PreAuthorize |
| Password hashed with BCrypt (strength 12) | TBD | PasswordEncoder configuration |
| Input validation on all endpoints | TBD | @Valid on all DTOs |
| No SQL injection (JPA parameterized queries) | TBD | Repository layer review |
| CORS restricted | TBD | SecurityConfig |
| HTTPS enforced (production) | TBD | application-prod.properties |

