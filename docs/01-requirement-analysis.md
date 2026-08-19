# 1. Requirement Analysis — Audit Log Service

## 1.1 Project Overview

**Project Name:** Tamper-Evident Audit Log Service

**Objective:** Build a production-grade system that records an append-only history of events and guarantees that past records cannot be modified or deleted without detection through cryptographic hash chaining.

**Technology Stack:**
- Java 21 LTS
- Spring Boot 3.x
- PostgreSQL
- Maven
- JUnit 5 + Mockito
- Spring Security + JWT
- OpenAPI/Swagger

**Scope:** Three scenarios with increasing complexity (Scenario A: Core, Scenario B: Extension, Scenario C: Ambiguous/Compliance)

---

## 1.2 Business Actors

| Actor | Description | Role |
|-------|-------------|------|
| System Administrator | Manages the audit log service | Configure, monitor, verify chain integrity |
| Audit Logger | Application that creates audit events | Write events via Write API |
| Auditor/Compliance Officer | Reviews audit logs for compliance | Query events, verify chain integrity |
| Event Consumer | External system reading audit data | Query and verify exported bundles |

---

## 1.3 Business Entities

### Core Entities

#### AuditEvent (REQ-001)
An immutable record of something that occurred in the system.

**Fields:**
| Field | Type | Required | Constraints | Purpose |
|-------|------|----------|-------------|---------|
| id | UUID/Long | Yes | Unique, primary key | Event identifier |
| eventType | String | Yes | Max 100 chars, not null | What happened (USER_LOGIN, RECORD_UPDATED, PERMISSION_GRANTED, etc.) |
| actorId | String | Yes | Not null, indexable | Who/what caused event |
| resourceType | String | Yes | Not null, indexable | Type of affected resource (e.g., ACCOUNT, USER, DOCUMENT) |
| resourceId | String | Yes | Not null, indexable | Specific resource affected |
| payload | JSON | Yes | Not null | Event-specific structured details |
| timestamp | LocalDateTime | Yes | Not null, indexable | When event occurred (caller-supplied or server-assigned - see ASSUMPTION-001) |
| contentHash | String | Yes | SHA-256 hex, immutable | Hash of event content (eventType + actorId + resourceType + resourceId + payload + timestamp) |
| previousHash | String | Yes | SHA-256 hex, immutable | Hash of previous record or genesis value for first record |
| chainPosition | Long | Yes | Sequential, immutable, indexed | Position in the chain (1, 2, 3, ...) |
| archived | Boolean | No | Default false | Soft-delete flag for retention policy (Scenario B) |
| archivedAt | LocalDateTime | No | Nullable | When record was archived (Scenario B) |
| redactionMetadata | JSON | No | Nullable | Tracks which fields were redacted and hash implications (Scenario B) |
| createdAt | LocalDateTime | Yes | Auto-generated | Record creation in database |
| updatedAt | LocalDateTime | No | Auto-generated | Last update (for redaction only) |

**Business Rules (REQ-002):**
- Records are **append-only**: No update or delete operations exposed via API
- Each record must include a hash of the previous record (forming a chain)
- First record (chainPosition = 1) has `previousHash = GENESIS_VALUE`
- contentHash is computed from: `SHA-256(eventType || actorId || resourceType || resourceId || payload || timestamp)`
- Modifying any past record invalidates its hash and all subsequent hashes
- Archived records remain in chain (Scenario B)

#### ChainVerification (REQ-003)
Result of verifying the hash chain integrity.

**Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| isIntact | Boolean | Whether entire chain is valid |
| firstInconsistency | Long | chainPosition of first broken record (null if intact) |
| violationType | Enum | Type of violation (CONTENT_HASH_MISMATCH, PREVIOUS_HASH_MISMATCH, MISSING_RECORD) |
| violationDetails | String | Human-readable description |
| totalRecords | Long | Total records in chain (including archived) |
| verifiedAt | LocalDateTime | When verification was performed |

---

## 1.4 Functional Requirements

### Scenario A: Core Audit Log Service (SCN-001)

#### FR-A1: Write API — Create Audit Event

**Endpoint:** `POST /api/v1/audit/events`

**Request Body:**
```json
{
  "eventType": "USER_LOGIN",
  "actorId": "user123",
  "resourceType": "USER_SESSION",
  "resourceId": "session456",
  "payload": {
    "ipAddress": "192.168.1.1",
    "browser": "Chrome",
    "loginMethod": "oauth"
  },
  "timestamp": "2026-08-18T10:30:00Z"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Event recorded successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "USER_LOGIN",
    "actorId": "user123",
    "resourceType": "USER_SESSION",
    "resourceId": "session456",
    "payload": {...},
    "timestamp": "2026-08-18T10:30:00Z",
    "contentHash": "abc123...",
    "previousHash": "def456...",
    "chainPosition": 42,
    "createdAt": "2026-08-18T10:30:01Z"
  },
  "timestamp": "2026-08-18T10:30:01Z"
}
```

**Requirements:**
- REQ-004: Accept event record with all specified fields
- REQ-005: Generate contentHash from event fields
- REQ-006: Retrieve and store previousHash from preceding record
- REQ-007: Assign sequential chainPosition
- REQ-008: Persist event atomically
- REQ-009: Return created event with all hashes

**Validation:**
- eventType: required, max 100 chars, alphanumeric + underscore
- actorId: required, max 255 chars
- resourceType: required, max 100 chars
- resourceId: required, max 255 chars
- payload: required, valid JSON
- timestamp: optional (see ASSUMPTION-001)

**Security (REQ-010):**
- ASSUMPTION-002: API should be protected (role TBD)
- No authentication specified in PDF — will implement basic Spring Security

**Exception Handling (REQ-011):**
- 400 Bad Request: validation fails
- 401 Unauthorized: not authenticated
- 403 Forbidden: insufficient permissions
- 500 Internal Server Error: database/hashing failure

---

#### FR-A2: Query API — Retrieve Events with Filtering

**Endpoint:** `GET /api/v1/audit/events`

**Query Parameters:**
| Parameter | Type | Required | Purpose |
|-----------|------|----------|---------|
| actorId | String | No | Filter by actor |
| resourceType | String | No | Filter by resource type |
| resourceId | String | No | Filter by specific resource |
| eventType | String | No | Filter by event type |
| from | ISO-8601 DateTime | No | Start of time range (inclusive) |
| to | ISO-8601 DateTime | No | End of time range (inclusive) |
| page | Integer | No | Page number (0-indexed, default 0) |
| size | Integer | No | Page size (default 20, max 100) |
| sort | String | No | Sort field (chainPosition, timestamp; default chainPosition asc) |

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Events retrieved successfully",
  "data": {
    "content": [
      {
        "id": "...",
        "eventType": "USER_LOGIN",
        ...
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 450,
      "totalPages": 23
    }
  },
  "timestamp": "2026-08-18T10:30:01Z"
}
```

**Requirements:**
- REQ-012: Filter by any combination of actorId, resourceType, resourceId, eventType
- REQ-013: Support time range filtering (from/to)
- REQ-014: Support pagination (page/size with reasonable limits)
- REQ-015: Support sorting (chainPosition, timestamp)
- REQ-016: Return paginated, filtered results

**Implementation Notes:**
- All filters are optional (AND logic when multiple provided)
- Time range: inclusive on both ends
- Default sort by chainPosition ascending (preserves chain order)
- Archived records included by default (filter TBD in Scenario B)

**Security (REQ-017):**
- ASSUMPTION-003: Query API should be readable by auditors (role TBD)

---

#### FR-A3: Chain Verification Endpoint

**Endpoint:** `GET /api/v1/audit/verify`

**Implementation decision:** The current service exposes verification as `POST /api/v1/audit/events/verify-chain`, protected for `AUDITOR` or `ADMIN`. This documentation retains the original requirement endpoint for traceability; the implemented endpoint is documented in `audit-log-service/API-REFERENCE.md` and Swagger.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Chain verification completed",
  "data": {
    "isIntact": true,
    "firstInconsistency": null,
    "violationType": null,
    "violationDetails": null,
    "totalRecords": 450,
    "verifiedAt": "2026-08-18T10:30:01Z"
  },
  "timestamp": "2026-08-18T10:30:01Z"
}
```

**When Chain is Broken (200 OK - verification succeeded, chain is broken):**
```json
{
  "success": true,
  "message": "Chain verification completed",
  "data": {
    "isIntact": false,
    "firstInconsistency": 42,
    "violationType": "CONTENT_HASH_MISMATCH",
    "violationDetails": "Record 42's contentHash does not match computed hash. Computed: abc123..., Stored: def456...",
    "totalRecords": 450,
    "verifiedAt": "2026-08-18T10:30:01Z"
  },
  "timestamp": "2026-08-18T10:30:01Z"
}
```

**Requirements:**
- REQ-018: Walk entire hash chain from genesis to latest
- REQ-019: Verify each record's contentHash is valid
- REQ-020: Verify each record's previousHash matches previous record's contentHash
- REQ-021: Detect and report first inconsistency
- REQ-022: Report violation type and details
- REQ-023: Return full chain status

**Violation Types:**
1. CONTENT_HASH_MISMATCH: Record's stored contentHash does not match computed hash
2. PREVIOUS_HASH_MISMATCH: Record's previousHash does not match previous record's contentHash
3. MISSING_RECORD: Gap in chain positions
4. GENESIS_VIOLATION: First record doesn't have expected genesis hash

**Performance Consideration:**
- REQ-024: Verification is O(n) — process all records sequentially
- ASSUMPTION-004: Initial implementation reads all records (consider caching/optimization for Scenario B)

---

### Scenario B: Extension — Retention and Redaction (SCN-002)

#### FR-B1: Retention Policy

**Requirement (REQ-025):**
- Records older than configurable window should be archivable or soft-deletable
- Archived records remain in chain (not physically deleted)
- Chain verification must handle archived records correctly
- No false positive breaks for legitimately archived records

**Configuration:**
- `audit.retention.days`: How many days to retain active records (default: 365)
- `audit.retention.policy`: KEEP | ARCHIVE | SOFT_DELETE (default: ARCHIVE)

**Implementation:**
- Archived flag and archivedAt timestamp on AuditEvent
- Query API optional filter: `includeArchived=true|false` (default: true)
- Chain verification includes archived records

**Requirements:**
- REQ-026: Support configurable retention window
- REQ-027: Support archival without breaking chain
- REQ-028: Archive operation is audit-logged itself

---

#### FR-B2: Structured Redaction

**Requirement (REQ-029):**
- Redact sensitive fields in payload (e.g., account numbers, SSN) without breaking hash chain
- Genuine engineering problem: original hash covers original value; removing value invalidates hash

**Solution Approach (ASSUMPTION-005):**
- Store redactionMetadata JSON tracking:
  - Which fields were redacted
  - Original hash of redacted value (preserved for chain verification)
  - Timestamp of redaction
  - Actor who performed redaction
- contentHash remains valid (computed from original content)
- Displayed payload shows [REDACTED] but chain remains intact

**Endpoint:** `PATCH /api/v1/audit/events/{id}/redact`

**Request:**
```json
{
  "fieldsToRedact": ["payload.ssn", "payload.accountNumber"],
  "reason": "PII redaction per GDPR"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Fields redacted successfully",
  "data": {
    "id": "...",
    "payload": {
      "ssn": "[REDACTED]",
      "accountNumber": "[REDACTED]",
      "otherField": "value"
    },
    "contentHash": "abc123...",
    "redactionMetadata": {
      "redactedFields": ["payload.ssn", "payload.accountNumber"],
      "redactionTime": "2026-08-18T10:30:01Z",
      "redactionActor": "admin123",
      "reason": "PII redaction per GDPR"
    }
  }
}
```

**Requirements:**
- REQ-030: Support field-level redaction
- REQ-031: Preserve contentHash (chain validity)
- REQ-032: Track redaction metadata
- REQ-033: Prevent re-redaction of already redacted fields
- REQ-034: API returns redacted view; original preserved internally

**Security:**
- ASSUMPTION-006: Only ADMIN role can redact (TBD)

---

#### FR-B3: Bulk Export

**Endpoint:** `POST /api/v1/audit/export`

**Request:**
```json
{
  "resourceId": "resource123",
  "actorId": null,
  "format": "JSON_BUNDLE"
}
```

**Response (200 OK - attachment):**
- Self-contained JSON bundle with:
  - All events for given resourceId or actorId
  - Chain metadata (genesis hash, final hash, record count)
  - Verification instructions
  - Timestamps for temporal integrity

**Requirements:**
- REQ-035: Export all records for given resourceId or actorId
- REQ-036: Bundle includes chain metadata
- REQ-037: Recipient can independently verify bundle integrity
- REQ-038: Verify no records were added/removed/modified during export

---

### Scenario C: Ambiguous — Compliance Reporting (SCN-003)

**Requirement Statement (REQ-039):**
> "Regulators need to be able to audit access to client account data."

**Clarifications Required:**

| # | Question | Impact | Current Assumption |
|---|----------|--------|-------------------|
| C1 | What does "audit access" mean? WHO is accessing? What resource? | API design, filtering | Assume: log every read/write of account data via separate audit events |
| C2 | What is "client account data"? | Schema, data classification | Assume: records with resourceType=ACCOUNT and eventTypes like READ_ACCOUNT, UPDATE_ACCOUNT |
| C3 | What compliance framework? (SOX, GDPR, PCI-DSS, etc.) | Retention, redaction, reporting | Assume: industry-standard 7-year retention |
| C4 | What reports needed? (access logs, summary, exception reports?) | Endpoints to build | Assume: expose filtered query API; let regulators run custom queries |
| C5 | How often audited? Real-time? Periodic? | Performance, batch processing | Assume: on-demand query via API |
| C6 | Who can access compliance reports? (role/auth) | Security, RBAC | Assume: AUDITOR and ADMIN roles |
| C7 | Export format for regulators? (JSON, CSV, signed, encrypted?) | Export enhancement | Assume: JSON bundle with chain verification |

**Implemented Approach (ASSUMPTION-007):**
- Use core Scenario A + Scenario B infrastructure
- Tagging: Records with resourceType=ACCOUNT tagged as "compliance-relevant"
- Query endpoint: Allow filtering by resourceType=ACCOUNT, eventType=READ_ACCOUNT|UPDATE_ACCOUNT|DELETE_ACCOUNT
- Compliance Report Endpoint: `GET /api/v1/compliance/account-access-report?actorId=...&from=...&to=...`
- Export: Compliance-ready bundle with signatures/verification metadata
- Retention: Enforce 7-year minimum retention on ACCOUNT records

**Requirements:**
- REQ-040: Filter/query account access events
- REQ-041: Compliance-ready reporting endpoint
- REQ-042: Export with regulatory metadata
- REQ-043: Tamper-evidence certificate for regulators
- REQ-044: Retention enforcement for compliance records

---

## 1.5 Non-Functional Requirements

| NFR | Description | Importance |
|-----|-------------|-----------|
| NFR-001 | **Performance**: Write latency < 100ms (p95) | High |
| NFR-002 | **Throughput**: Support 1000+ events/sec | High |
| NFR-003 | **Reliability**: 99.9% uptime | High |
| NFR-004 | **Tamper-Evidence**: Cryptographic strength (SHA-256) | Critical |
| NFR-005 | **Auditability**: All operations logged | High |
| NFR-006 | **Security**: OWASP Top 10 compliance | High |
| NFR-007 | **Scalability**: Support millions of records | High |
| NFR-008 | **Data Integrity**: ACID transactions | Critical |
| NFR-009 | **Compliance**: 7-year retention capability | High |
| NFR-010 | **Documentation**: OpenAPI/Swagger | Medium |
| NFR-011 | **Monitoring**: Logging, metrics, alerting hooks | Medium |

---

## 1.6 Business Rules

| BR | Rule | Affected API |
|----|------|-------------|
| BR-001 | Events are immutable once created | POST /audit/events |
| BR-002 | Only append operations allowed (no update/delete) | Write API |
| BR-003 | Every record links to previous record via hash | POST /audit/events |
| BR-004 | Hash chain breaks are detectable | GET /audit/verify |
| BR-005 | Archived records do not break chain | Retention Policy |
| BR-006 | Redaction does not invalidate chain | Redaction API |
| BR-007 | First record has genesis hash | Chain initialization |
| BR-008 | ChainPosition is sequential (no gaps) | Verification |
| BR-009 | Timestamp is immutable after creation | Data model |
| BR-010 | Archived records cannot be un-archived | Retention policy |

---

## 1.7 Validation Rules

| Validation | Rule | Field |
|-----------|------|-------|
| V-001 | Not blank, max 100 chars, alphanumeric + underscore | eventType |
| V-002 | Not blank, max 255 chars | actorId |
| V-003 | Not blank, max 100 chars | resourceType |
| V-004 | Not blank, max 255 chars | resourceId |
| V-005 | Valid JSON, not null | payload |
| V-006 | Optional; if provided, valid ISO-8601 datetime | timestamp |
| V-007 | Valid SHA-256 hex (64 chars) | contentHash |
| V-008 | Valid SHA-256 hex or GENESIS_VALUE | previousHash |
| V-009 | Positive integer, sequential | chainPosition |
| V-010 | Page number >= 0 | page parameter |
| V-011 | 1 <= size <= 100 | size parameter |
| V-012 | Valid ISO-8601 range (from <= to) | from/to parameters |

---

## 1.8 Error Scenarios

| Scenario | HTTP Status | Error Code | Message |
|----------|-------------|-----------|---------|
| Invalid eventType format | 400 | INVALID_EVENT_TYPE | "eventType must be alphanumeric + underscore, max 100 chars" |
| Missing required field | 400 | MISSING_REQUIRED_FIELD | "Field {field} is required" |
| Invalid JSON payload | 400 | INVALID_JSON | "Payload must be valid JSON" |
| Invalid timestamp format | 400 | INVALID_TIMESTAMP | "Timestamp must be ISO-8601 format" |
| Unauthenticated request | 401 | UNAUTHORIZED | "Authentication required" |
| Insufficient permissions | 403 | FORBIDDEN | "User does not have permission for this operation" |
| Resource not found | 404 | RESOURCE_NOT_FOUND | "Audit event not found: {id}" |
| Database constraint violation | 409 | CONSTRAINT_VIOLATION | "Duplicate or invalid data" |
| Hash computation failure | 500 | HASH_COMPUTATION_ERROR | "Error computing event hash" |
| Database failure | 500 | DATABASE_ERROR | "Database error occurred" |
| Unexpected exception | 500 | INTERNAL_ERROR | "An unexpected error occurred" |

---

## 1.9 Use Cases

### UC-001: Create Audit Event
**Actor:** Audit Logger
**Flow:**
1. Send POST /audit/events with event data
2. Validate event fields
3. Compute contentHash from fields
4. Retrieve previousHash from latest record
5. Assign chainPosition = max(previous) + 1
6. Persist to database
7. Return event with hashes

---

### UC-002: Query Events
**Actor:** Auditor, Compliance Officer
**Flow:**
1. Send GET /audit/events with optional filters
2. Parse and validate parameters
3. Build database query with filters
4. Execute pagination query
5. Return filtered, paginated results

---

### UC-003: Verify Chain Integrity
**Actor:** Auditor, Compliance Officer
**Flow:**
1. Send GET /audit/verify
2. Load all records (with archived)
3. Iterate from first record:
   - Compute contentHash for current record
   - Verify computed hash matches stored contentHash
   - Verify current.previousHash == previous.contentHash
4. Report first inconsistency or "intact"
5. Return verification result

---

### UC-004: Redact Sensitive Fields (Scenario B)
**Actor:** Administrator
**Flow:**
1. Send PATCH /audit/events/{id}/redact with fields to redact
2. Retrieve event record
3. Mark specified payload fields as [REDACTED]
4. Store redactionMetadata (original hash preserved)
5. Update archived timestamp
6. Return redacted view

---

### UC-005: Archive Old Records (Scenario B)
**Actor:** System (scheduled job)
**Flow:**
1. Query records older than retention window
2. Set archived=true, archivedAt=now
3. Persist changes
4. Create audit event for archival action

---

### UC-006: Export Compliance Bundle (Scenario B)
**Actor:** Auditor
**Flow:**
1. Send POST /audit/export with resourceId or actorId
2. Query all matching records
3. Compute bundle metadata (genesis, final hash, count)
4. Package as self-contained JSON
5. Return as downloadable bundle

---

### UC-007: Generate Compliance Report (Scenario C)
**Actor:** Auditor, Regulator
**Flow:**
1. Send GET /compliance/account-access-report with filters
2. Query records with resourceType=ACCOUNT
3. Filter by eventType (READ, UPDATE, DELETE, etc.)
4. Apply date range
5. Return compliance-formatted report
6. Optional: Export as signed bundle

---

## 1.10 Assumptions

| ID | Assumption | Rationale | Impact |
|----|-----------|-----------|--------|
| ASSUMPTION-001 | timestamp is **optional** in request; if not provided, server assigns `now()` | Allows clients to either report wall-clock time or let server use system time | Slightly simplifies client; clear semantics |
| ASSUMPTION-002 | Write API (POST /audit/events) requires authentication and AUDIT_WRITER role | REQ-010 implies security; no specific auth details provided | Design security layer with role-based gates |
| ASSUMPTION-003 | Query API (GET /audit/events) readable by AUDITOR or ADMIN roles | Reasonable assumption for audit log use case | Implement method-level authorization |
| ASSUMPTION-004 | Initial verification (GET /audit/verify) is synchronous, O(n) on all records | PDF doesn't specify async/caching | Acceptable for MVP; optimize later if needed |
| ASSUMPTION-005 | Redaction preserves original content hash; uses metdata to track redacted fields | PDF specifies redaction must not break chain; this is the engineering solution | Complex implementation; justifies Scenario B effort |
| ASSUMPTION-006 | Only ADMIN role can redact records (Scenario B) | Sensitive operation; reasonable permission model | Implement admin-only endpoint guard |
| ASSUMPTION-007 | Scenario C compliance reporting uses resourceType=ACCOUNT tagging + query API | PDF is vague; this provides clear implementation path | Builds naturally on Scenario A foundation |
| ASSUMPTION-008 | Hash algorithm: **SHA-256** (256-bit, hex-encoded) | Industry standard, cryptographically strong | Use Java MessageDigest or Spring Crypto utilities |
| ASSUMPTION-009 | Genesis hash (first record's previousHash): "GENESIS_HASH" (constant string) | Clearly marks start of chain | Simplifies verification logic |
| ASSUMPTION-010 | Pagination defaults: page=0, size=20, max size=100 | REQ-014 specified; reasonable defaults | Prevent accidental large queries |

---

## 1.11 Clarifications Required (Before Proceeding)

| # | Question | Resolution |
|---|----------|-----------|
| C1 | Should timestamp be client-supplied or server-assigned? | ASSUMPTION-001: Optional (client can provide, server assigns if missing) |
| C2 | What are the actual roles/auth requirements? | ASSUMPTION-002/003: AUDIT_WRITER for write, AUDITOR/ADMIN for read |
| C3 | Hash algorithm (SHA-256, SHA-3, etc.)? | ASSUMPTION-008: SHA-256 (standard, proven) |
| C4 | Redaction approach — how to preserve chain? | ASSUMPTION-005: Metadata-based tracking with preserved original hash |
| C5 | Performance targets for 1000+ events/sec? | NFR-002 implies high throughput; batch writes / connection pooling needed |
| C6 | Should redaction/archival create audit events? | ASSUMPTION-007: Yes, these are operations and should be logged |
| C7 | Multi-tenant? Single tenant? | Assuming single tenant (per PDF) |
| C8 | Encryption at rest? | Not specified; can add later; JWT in transit via HTTPS assumed |

---

## 1.12 Requirement-to-Scenario Mapping

### Scenario A (SCN-001): Core Audit Log Service
**Requirements:**
- REQ-001 through REQ-024
- APIs: Write, Query, Verify
- Database: AuditEvent entity with chain fields
- Security: Authentication + role-based read/write
- Tests: Unit + integration for core features

### Scenario B (SCN-002): Retention and Redaction
**Requirements:**
- REQ-025 through REQ-038
- Additional APIs: Redact, Archive, Export
- Database: Add archived, archivedAt, redactionMetadata fields
- Tests: Retention, redaction, export verification

### Scenario C (SCN-003): Compliance Reporting
**Requirements:**
- REQ-039 through REQ-044
- Additional APIs: Compliance report, enhanced export
- Database: Tagging/classification (TBD detail)
- Tests: Compliance filtering, report generation

---

## 1.13 Summary

**Core Problem:**
Build a tamper-evident append-only audit log system using cryptographic hash chaining to detect any unauthorized modification or deletion of historical records.

**Solution Approach:**
- Each event record contains:
  - Content hash (SHA-256 of event fields)
  - Previous hash (SHA-256 of preceding record)
- Chain is verified by walking from genesis to latest, validating each hash
- Three scenarios with increasing complexity (core → retention/redaction → compliance)

**Key Challenges:**
1. **Hash Chain Integrity**: Every write must compute and link hashes correctly
2. **Immutability**: API must expose no update/delete (append-only contract)
3. **Redaction Paradox**: Redact sensitive fields without invalidating hashes (Scenario B)
4. **Compliance Ambiguity**: Convert vague requirement into concrete feature (Scenario C)
5. **Performance**: 1000+ events/sec requires efficient hashing, indexing, batch handling

**Success Criteria:**
- All three scenarios implemented and tested
- Chain verification detects tampering
- Attestation and development history in Git
- Requirement traceability maintained
- Production-grade code quality

