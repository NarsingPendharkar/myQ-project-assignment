# 4. Development Plan — Audit Log Service

**Objective:** Implement a tamper-evident audit log service with three scenarios using AI-assisted engineering practices over 2–3 days.

**Duration:** 2–3 days  
**Primary Focus:** Scenario A (core); Scenario B (extension); Scenario C (compliance/ambiguity)

---

## Phase 1: Foundation (Scenario A — Core Service)

### Commit 1: Project Setup and Dependencies
**Objective:** Initialize Spring Boot project with Maven, Java 21, and core dependencies.

**Tasks:**
- Create Maven project structure
- Add Spring Boot 3.x parent POM
- Add dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation
- Add test dependencies: spring-boot-starter-test, junit-5, mockito, testcontainers
- Add utility dependencies: lombok, springdoc-openapi (Swagger)
- Add H2 JDBC driver and Flyway H2 support
- Configure Maven properties: Java 21 source/target, project encoding

**Files Created:**
- pom.xml (updated)
- src/main/java/com/schwab/audit/AuditLogServiceApplication.java

**Deliverable:** Buildable Maven project; `mvn clean install` succeeds.

---

### Commit 2: Database Configuration and Schema
**Objective:** Configure H2 database and create audit events schema (V1 migration).

**Tasks:**
- Create application.properties with H2 datasource configuration
- Create application-dev.properties for development (H2 in-memory for quick testing)
- Create application-test.properties for automated testing (H2)
- Create V1__initial_schema.sql Flyway migration:
  - audit_events table (id, eventType, actorId, resourceType, resourceId, payload, timestamp, contentHash, previousHash, chainPosition, archived, archivedAt, redactionMetadata, createdAt, updatedAt)
  - users table (id, username, passwordHash, role, createdAt)
  - Indexes: chain_position (unique), actor_id, (resource_type, resource_id), event_type, timestamp, archived
  - Constraints: content_hash UNIQUE, chain_position UNIQUE

**Files Created:**
- src/main/resources/application.properties
- src/main/resources/application-dev.properties
- src/main/resources/application-test.properties
- src/main/resources/db/migration/V1__initial_schema.sql
- src/main/java/com/schwab/audit/config/JpaConfig.java

**Deliverable:** Database schema created; migrations run successfully.

---

### Commit 3: Security Foundation (JWT + Authentication)
**Objective:** Implement Spring Security with JWT-based stateless authentication.

**Tasks:**
- Create SecurityConfig (Spring Security configuration):
  - Disable CSRF (stateless JWT)
  - Configure authentication providers (UserDetailsService)
  - Configure authorization (path patterns, roles)
  - Add JwtAuthenticationFilter
  - CORS restricted to localhost:8080 (dev)
- Create JwtService (token generation/validation):
  - Issue JWT with user id, role, expiry (24 hours)
  - Validate JWT, extract claims
  - Handle expired/invalid tokens
- Create CustomUserDetailsService (load users from database)
- Create JwtAuthenticationFilter (intercept all requests, validate JWT)
- Create User entity and UserRepository
- Create AuthController with login endpoint:
  - POST /api/v1/auth/login (username/password → JWT token)
- Add PasswordEncoder bean (BCrypt, strength 12)

**Files Created:**
- src/main/java/com/schwab/audit/config/SecurityConfig.java
- src/main/java/com/schwab/audit/config/JwtConfig.java
- src/main/java/com/schwab/audit/security/JwtService.java
- src/main/java/com/schwab/audit/security/JwtAuthenticationFilter.java
- src/main/java/com/schwab/audit/security/CustomUserDetailsService.java
- src/main/java/com/schwab/audit/entity/User.java
- src/main/java/com/schwab/audit/entity/enums/UserRole.java
- src/main/java/com/schwab/audit/repository/UserRepository.java
- src/main/java/com/schwab/audit/controller/AuthController.java
- src/main/java/com/schwab/audit/dto/request/LoginRequest.java
- src/main/java/com/schwab/audit/dto/response/LoginResponse.java
- src/test/java/com/schwab/audit/security/JwtServiceTest.java

**Deliverable:** Users can authenticate via POST /api/v1/auth/login and receive JWT token.

---

### Commit 4: Core Entities and DTOs
**Objective:** Define JPA entities and request/response DTOs for audit events.

**Tasks:**
- Create AuditEvent JPA entity:
  - All fields: id, eventType, actorId, resourceType, resourceId, payload (JSONB), timestamp, contentHash, previousHash, chainPosition, archived, archivedAt, redactionMetadata, createdAt, updatedAt
  - Indexes defined via @Index annotations
  - Constraints: @Column(unique=true) on content_hash and chain_position
  - Immutability: No setters for hashes, chainPosition (after creation)
- Create enums: EventType, ResourceType, UserRole, ViolationType, ArchivePolicy
- Create DTOs:
  - CreateAuditEventRequest (eventType, actorId, resourceType, resourceId, payload, timestamp)
  - AuditEventResponse (all fields + display-level redaction)
  - ChainVerificationResponse (isIntact, firstInconsistency, violationType, etc.)
  - PaginatedResponse<T> (content, pagination metadata)
  - ApiResponse<T> (success wrapper)
  - ErrorResponse (error wrapper)
- Create AuditableEntity abstract base class (createdAt, updatedAt)
- Create Mappers:
  - AuditEventMapper (entity ↔ response DTO with redaction masking)
  - ChainVerificationMapper

**Files Created:**
- src/main/java/com/schwab/audit/entity/AuditEvent.java
- src/main/java/com/schwab/audit/entity/AuditableEntity.java
- src/main/java/com/schwab/audit/entity/enums/EventType.java
- src/main/java/com/schwab/audit/entity/enums/ResourceType.java
- src/main/java/com/schwab/audit/entity/enums/ViolationType.java
- src/main/java/com/schwab/audit/entity/enums/ArchivePolicy.java
- src/main/java/com/schwab/audit/dto/request/CreateAuditEventRequest.java
- src/main/java/com/schwab/audit/dto/response/AuditEventResponse.java
- src/main/java/com/schwab/audit/dto/response/ChainVerificationResponse.java
- src/main/java/com/schwab/audit/dto/response/PaginatedResponse.java
- src/main/java/com/schwab/audit/dto/response/ApiResponse.java
- src/main/java/com/schwab/audit/dto/response/ErrorResponse.java
- src/main/java/com/schwab/audit/mapper/AuditEventMapper.java
- src/main/java/com/schwab/audit/mapper/ChainVerificationMapper.java

**Deliverable:** Entities and DTOs compile; mapper logic verified.

---

### Commit 5: Write API (Create Audit Event)
**Objective:** Implement POST /api/v1/audit/events endpoint to create and persist audit events with hash chaining.

**Tasks:**
- Create HashingService:
  - `computeSha256(content)`: Generic SHA-256 computation
  - `computeEventHash(event)`: Event-specific hash (eventType || actorId || resourceType || resourceId || payload || timestamp)
  - `verifyHash(stored, computed)`: Comparison
- Create AuditEventService:
  - `createEvent(request)`: 
    1. Validate request (via Bean Validation)
    2. If timestamp not provided, assign server's now()
    3. Retrieve latest record (for previousHash)
    4. Compute contentHash from event fields
    5. Compute previousHash from latest record (or GENESIS_HASH if first)
    6. Assign chainPosition = max + 1
    7. Persist to database
    8. Return created event
  - Private helper methods for hash computation and chain position assignment
- Create AuditEventRepository (extends JpaRepository):
  - `findLatestRecord()`: Retrieve record with max chainPosition
  - `save(event)`: Standard JPA persist
- Create AuditEventController:
  - `POST /api/v1/audit/events`
  - @PreAuthorize("hasRole('AUDIT_WRITER')")
  - Validate CreateAuditEventRequest
  - Call AuditEventService.createEvent()
  - Return 201 Created with AuditEventResponse
- Create exception classes:
  - AuditException (base)
  - InvalidEventException
  - HashComputationException
- Create validation classes:
  - EventTypeValidator (alphanumeric + underscore, 1-100 chars)
  - PayloadValidator (valid JSON)
  - DateRangeValidator (from <= to)
- Create GlobalExceptionHandler:
  - Handle MethodArgumentNotValidException → 400
  - Handle InvalidEventException → 400
  - Handle HashComputationException → 500
  - Handle generic Exception → 500

**Files Created:**
- src/main/java/com/schwab/audit/service/HashingService.java
- src/main/java/com/schwab/audit/service/AuditEventService.java
- src/main/java/com/schwab/audit/repository/AuditEventRepository.java
- src/main/java/com/schwab/audit/controller/AuditEventController.java
- src/main/java/com/schwab/audit/exception/GlobalExceptionHandler.java
- src/main/java/com/schwab/audit/exception/AuditException.java
- src/main/java/com/schwab/audit/exception/InvalidEventException.java
- src/main/java/com/schwab/audit/exception/HashComputationException.java
- src/main/java/com/schwab/audit/validation/EventTypeValidator.java
- src/main/java/com/schwab/audit/validation/PayloadValidator.java
- src/test/java/com/schwab/audit/service/HashingServiceTest.java
- src/test/java/com/schwab/audit/service/AuditEventServiceTest.java (initial tests)
- src/test/java/com/schwab/audit/util/Constants.java

**Deliverable:** POST /api/v1/audit/events successfully creates events with SHA-256 hash chain.

---

### Commit 6: Query API (Retrieve Events with Filtering)
**Objective:** Implement GET /api/v1/audit/events endpoint with dynamic filtering, pagination, and sorting.

**Tasks:**
- Create AuditEventSpecifications:
  - `byActorId(actorId)`: Specification for filtering by actor
  - `byResourceType(type)`: Specification for filtering by resource type
  - `byResourceId(resourceId)`: Specification for filtering by resource id
  - `byEventType(eventType)`: Specification for filtering by event type
  - `inDateRange(from, to)`: Specification for time range filtering
  - Static composition: `Specification.where(...).and(...)`
- Extend AuditEventRepository:
  - Extend JpaSpecificationExecutor<AuditEvent> for dynamic query support
  - Add custom method if needed: `findByComplexCriteria(...)`
- Update AuditEventService:
  - `queryEvents(filter)`: Build Specification from filter parameters, execute query with pagination/sorting
- Update AuditEventController:
  - `GET /api/v1/audit/events?actorId=...&resourceType=...&resourceId=...&eventType=...&from=...&to=...&page=0&size=20&sort=...`
  - @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
  - Parse query parameters, validate ranges
  - Call AuditEventService.queryEvents()
  - Return 200 with PaginatedResponse
- Add exception handling:
  - 400: Invalid page/size/date range
  - 401: Missing JWT
  - 403: Insufficient role

**Files Created/Modified:**
- src/main/java/com/schwab/audit/repository/AuditEventSpecifications.java (new)
- src/main/java/com/schwab/audit/service/AuditEventService.java (modify)
- src/main/java/com/schwab/audit/controller/AuditEventController.java (modify)
- src/test/java/com/schwab/audit/service/AuditEventServiceTest.java (add query tests)

**Deliverable:** GET /api/v1/audit/events filters and paginates correctly.

---

### Commit 7: Chain Verification Endpoint
**Objective:** Implement GET /api/v1/audit/verify endpoint to detect tampering via hash chain validation.

**Tasks:**
- Create ChainVerificationService:
  - `verifyChain()`:
    1. Load all records ordered by chain_position
    2. For each record:
       - Compute contentHash from fields
       - Verify computed hash matches stored contentHash
       - Verify current.previousHash == previous.contentHash
       - Check for gaps in chain_position
    3. On first mismatch: Record violation
    4. Return ChainVerificationResponse (isIntact, firstInconsistency, type, details)
  - Helper methods: `computeAndVerifyHash()`, `detectMissing Records()`, `detectViolationType()`
- Extend AuditEventRepository:
  - `findCompleteChain()`: SELECT all records ordered by chain_position
- Create ChainVerificationController:
  - `GET /api/v1/audit/verify`
  - @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
  - Call ChainVerificationService.verifyChain()
  - Return 200 with ChainVerificationResponse
- Create exception: ChainBrokenException (for internal tracking, but return 200 with violation details)
- Add integration test: Create events, verify chain, manually tamper with database, re-verify

**Files Created/Modified:**
- src/main/java/com/schwab/audit/service/ChainVerificationService.java (new)
- src/main/java/com/schwab/audit/controller/ChainVerificationController.java (new)
- src/main/java/com/schwab/audit/exception/ChainBrokenException.java (new)
- src/main/java/com/schwab/audit/repository/AuditEventRepository.java (modify: add findCompleteChain)
- src/test/java/com/schwab/audit/service/ChainVerificationServiceTest.java (new)

**Deliverable:** GET /api/v1/audit/verify detects chain integrity violations.

---

### Commit 8: Unit Tests for Scenario A
**Objective:** Comprehensive unit tests for service layer (AuditEventService, ChainVerificationService, HashingService).

**Tasks:**
- Write unit tests for HashingService:
  - `shouldComputeSha256Correctly()`
  - `shouldProduceConsistentHash()`
  - `shouldDetectHashMismatch()`
- Write unit tests for AuditEventService:
  - `shouldCreateEventWhenRequestIsValid()`
  - `shouldReturn400WhenEventTypeIsInvalid()`
  - `shouldReturn400WhenRequiredFieldIsMissing()`
  - `shouldComputeContentHashFromEventFields()`
  - `shouldRetrievePreviousHashFromLatestRecord()`
  - `shouldAssignSequentialChainPosition()`
  - `shouldAssignGenesisHashForFirstEvent()`
  - `shouldReturnPaginatedResultsWhenQuerying()`
  - `shouldFilterByActorId()`
  - `shouldFilterByResourceType()`
  - `shouldFilterByTimeRange()`
  - Mock repository and hashing service
- Write unit tests for ChainVerificationService:
  - `shouldReturnIntactWhenChainIsValid()`
  - `shouldDetectContentHashMismatch()`
  - `shouldDetectPreviousHashMismatch()`
  - `shouldDetectMissingRecords()`
  - `shouldReturnFirstInconsistency()`
  - Mock repository

**Files Created:**
- src/test/java/com/schwab/audit/service/AuditEventServiceTest.java (comprehensive)
- src/test/java/com/schwab/audit/service/ChainVerificationServiceTest.java (comprehensive)
- src/test/java/com/schwab/audit/service/HashingServiceTest.java (comprehensive)
- src/test/testdata/TestDataFactory.java (helper for creating test events)

**Deliverable:** 30+ passing unit tests; target 80%+ code coverage for service layer.

---

### Commit 9: Integration Tests for Scenario A
**Objective:** End-to-end integration tests for all Scenario A APIs.

**Tasks:**
- Write integration tests for AuditEventController:
  - `shouldCreateEventWhenAuthenticatedWithAuditWriterRole()`
  - `shouldReturn401WhenJwtIsMissing()`
  - `shouldReturn403WhenUserLacksAuditWriterRole()`
  - `shouldReturn400WhenPayloadIsInvalid()`
  - `shouldReturnEventWithHashesAndChainPosition()`
  - `shouldQueryEventsByActorId()`
  - `shouldQueryEventsByResourceType()`
  - `shouldQueryEventsByTimeRange()`
  - `shouldPaginateResults()`
  - Use MockMvc to test HTTP layer
  - Use @SpringBootTest with H2 in-memory database
- Write integration tests for ChainVerificationController:
  - `shouldVerifyIntactChain()`
  - `shouldDetectTamperingWhenRecordIsModified()`
  - Use database fixture to create events
- Test full workflow:
  1. Create 10 events
  2. Verify chain (isIntact = true)
  3. Manually modify event's contentHash in database
  4. Verify chain again (isIntact = false, identify violation)

**Files Created:**
- src/test/java/com/schwab/audit/integration/AuditEventControllerIntegrationTest.java
- src/test/java/com/schwab/audit/integration/ChainVerificationIntegrationTest.java
- src/test/fixture/DatabaseFixture.java

**Deliverable:** 20+ passing integration tests; end-to-end workflows verified.

---

### Commit 10: Exception Handling and Error Responses
**Objective:** Centralized exception handling with structured error responses.

**Tasks:**
- Update GlobalExceptionHandler:
  - Handle MethodArgumentNotValidException (400, field errors)
  - Handle InvalidEventException (400)
  - Handle ChainBrokenException (200 with violation details, or 409?)
  - Handle ResourceNotFoundException (404)
  - Handle UnauthorizedException (401)
  - Handle ForbiddenException (403)
  - Handle ConflictException (409)
  - Handle HashComputationException (500)
  - Handle Exception (500, generic)
- Create exception classes:
  - ResourceNotFoundException
  - UnauthorizedException
  - ForbiddenException
  - ConflictException
  - All should log safely (no secrets)
- Update ErrorResponse DTO:
  - Include: success, message, code, timestamp, path
  - Format consistently across all error types

**Files Created/Modified:**
- src/main/java/com/schwab/audit/exception/GlobalExceptionHandler.java (enhanced)
- src/main/java/com/schwab/audit/exception/ResourceNotFoundException.java (new)
- src/main/java/com/schwab/audit/exception/UnauthorizedException.java (new)
- src/main/java/com/schwab/audit/exception/ForbiddenException.java (new)
- src/main/java/com/schwab/audit/exception/ConflictException.java (new)

**Deliverable:** All error scenarios return structured ErrorResponse; 4xx/5xx responses tested.

---

### Commit 11: OpenAPI/Swagger Documentation
**Objective:** Add OpenAPI 3.0 specification and Swagger UI for Scenario A endpoints.

**Tasks:**
- Add springdoc-openapi dependency (already in pom.xml)
- Create OpenApiConfig bean:
  - Define info (title, description, version)
  - Define servers
  - Define security scheme (JWT Bearer)
- Annotate controllers:
  - @Tag on controller classes
  - @Operation on methods
  - @RequestBody, @Parameter on method parameters
  - @ApiResponse on methods for different status codes
  - Add examples to DTOs
- Create or update openapi.yaml:
  - Document all Scenario A endpoints
  - Include request/response examples
  - Document validation errors
  - Document authorization requirements
- Test Swagger UI at http://localhost:8080/swagger-ui.html

**Files Created/Modified:**
- src/main/java/com/schwab/audit/config/OpenApiConfig.java (new)
- src/main/java/com/schwab/audit/controller/AuditEventController.java (add annotations)
- src/main/java/com/schwab/audit/controller/ChainVerificationController.java (add annotations)
- src/main/resources/openapi.yaml (new or updated)

**Deliverable:** Swagger UI displays all Scenario A endpoints with proper documentation.

---

## Phase 2: Extension (Scenario B — Retention and Redaction)

### Commit 12: Database Schema Extension (Archive + Redaction Metadata)
**Objective:** Extend schema to support archival and redaction tracking.

**Tasks:**
- Create V2__add_archive_fields.sql:
  - Add columns to audit_events: archived (boolean, default false), archivedAt (timestamp nullable)
  - Add index on archived for retention queries
- Create V3__add_redaction_metadata.sql:
  - Add column to audit_events: redactionMetadata (JSONB nullable)
  - Redaction metadata structure: { "redactedFields": [...], "redactionTime": "...", "redactionActor": "...", "reason": "..." }
- Update JpaConfig to run all migrations

**Files Created/Modified:**
- src/main/resources/db/migration/V2__add_archive_fields.sql (new)
- src/main/resources/db/migration/V3__add_redaction_metadata.sql (new)

**Deliverable:** Database migrations run; new columns present in audit_events table.

---

### Commit 13: Redaction Service Implementation
**Objective:** Implement field-level redaction while preserving hash chain integrity.

**Tasks:**
- Create RedactionService:
  - `redactFields(eventId, fieldsToRedact, reason)`:
    1. Retrieve event by id
    2. Validate redaction request (prevent re-redaction)
    3. Parse payload JSON
    4. Mark specified fields as [REDACTED]
    5. Create redaction metadata
    6. Update event (payload + redactionMetadata + updatedAt)
    7. Persist
    8. Create audit event of type "FIELD_REDACTED" to log the redaction action
  - `applyRedactionMask(event)`: Show [REDACTED] in payload for API responses
  - Helper: `validateRedactionRequest()`, `maskPayload()`, `createRedactionMetadata()`
- Update AuditEventMapper:
  - When converting entity to response, apply redaction mask via applyRedactionMask()
- Create RedactionController:
  - `PATCH /api/v1/audit/events/{id}/redact`
  - @PreAuthorize("hasRole('ADMIN')")
  - Validate RedactFieldsRequest
  - Call RedactionService.redactFields()
  - Return 200 with redacted AuditEventResponse
- Add exception: ConflictException (for re-redaction attempts)

**Files Created/Modified:**
- src/main/java/com/schwab/audit/service/RedactionService.java (new)
- src/main/java/com/schwab/audit/controller/RedactionController.java (new)
- src/main/java/com/schwab/audit/dto/request/RedactFieldsRequest.java (new)
- src/main/java/com/schwab/audit/dto/response/RedactionMetadataResponse.java (new)
- src/main/java/com/schwab/audit/mapper/AuditEventMapper.java (modify)
- src/test/java/com/schwab/audit/service/RedactionServiceTest.java (new)

**Deliverable:** PATCH /api/v1/audit/events/{id}/redact successfully redacts fields; contentHash remains valid for chain verification.

---

### Commit 14: Archive Service and Retention Policy
**Objective:** Implement record archival based on configurable retention window.

**Tasks:**
- Create ArchiveService:
  - `archiveOldRecords()`: Scheduled method
    1. Query records older than retention window (app.audit.retention.days)
    2. Set archived=true, archivedAt=now
    3. Persist updates
    4. Create audit event of type "RECORDS_ARCHIVED" to log batch archival
  - `isArchived(event)`: Check if event is archived
- Create ArchiveJobScheduler (optional):
  - @Scheduled(cron = "0 0 2 * * *") to run archival at 2 AM daily
- Update ChainVerificationService:
  - Verify chain must include archived records
  - Archived records do not cause false positives
- Update AuditEventRepository:
  - Add query to find records by age for archival

**Files Created/Modified:**
- src/main/java/com/schwab/audit/service/ArchiveService.java (new)
- src/main/java/com/schwab/audit/config/ArchiveSchedulerConfig.java (new, if using scheduler)
- src/main/java/com/schwab/audit/service/ChainVerificationService.java (modify)
- src/main/java/com/schwab/audit/repository/AuditEventRepository.java (modify)
- src/main/resources/application.properties (add app.audit.retention.* properties)
- src/test/java/com/schwab/audit/service/ArchiveServiceTest.java (new)

**Deliverable:** Old records are archived without breaking chain; chain verification handles archived correctly.

---

### Commit 15: Bulk Export Service
**Objective:** Export records as self-contained, verifiable bundle.

**Tasks:**
- Create ExportService:
  - `exportByResourceId(resourceId)`:
    1. Query all records with given resourceId (including archived)
    2. Compute bundle metadata: genesisHash, finalHash, recordCount, exportTime
    3. Include chain metadata for verification
    4. Serialize to JSON bundle
    5. Return as downloadable attachment
  - `exportByActorId(actorId)`: Similar logic by actorId
  - `verifyBundle(bundle)`: Helper to verify bundle integrity
- Create ExportController:
  - `POST /api/v1/audit/export`
  - @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
  - Validate ExportRequest
  - Call ExportService.exportByResourceId() or exportByActorId()
  - Return 200 with attachment (application/json, Content-Disposition: attachment)
- Create ExportBundleResponse DTO:
  - records: [events]
  - metadata: { genesisHash, finalHash, recordCount, exportTime }

**Files Created/Modified:**
- src/main/java/com/schwab/audit/service/ExportService.java (new)
- src/main/java/com/schwab/audit/controller/ExportController.java (new)
- src/main/java/com/schwab/audit/dto/request/ExportRequest.java (new)
- src/main/java/com/schwab/audit/dto/response/ExportBundleResponse.java (new)
- src/test/java/com/schwab/audit/service/ExportServiceTest.java (new)

**Deliverable:** Bundles are exported with verifiable metadata; recipients can validate integrity.

---

### Commit 16: Integration Tests for Scenario B
**Objective:** End-to-end tests for redaction, archival, and export.

**Tasks:**
- Write integration tests for RedactionController:
  - `shouldRedactFieldsWhenAuthorizedAsAdmin()`
  - `shouldReturn403WhenUserIsNotAdmin()`
  - `shouldPreventReRedactionOfSameField()`
  - `shouldMaintainChainIntegrityAfterRedaction()`
- Write integration tests for ArchiveService:
  - `shouldArchiveOldRecordsBasedOnRetentionWindow()`
  - `shouldCreateAuditEventForBatchArchival()`
  - `shouldNotBreakChainWhenRecordsAreArchived()`
- Write integration tests for ExportService:
  - `shouldExportRecordsByResourceId()`
  - `shouldExportRecordsByActorId()`
  - `shouldIncludeBundleMetadata()`
  - `shouldAllowVerificationOfExportedBundle()`

**Files Created:**
- src/test/java/com/schwab/audit/integration/RedactionIntegrationTest.java (new)
- src/test/java/com/schwab/audit/integration/ArchiveIntegrationTest.java (new)
- src/test/java/com/schwab/audit/integration/ExportIntegrationTest.java (new)

**Deliverable:** 15+ integration tests for Scenario B workflows.

---

### Commit 17: Documentation and Tests for Scenario B
**Objective:** Update documentation and add comprehensive tests for Scenario B.

**Tasks:**
- Update /docs/03-implementation-details.md:
  - Mark Scenario B tasks as completed
  - Document redaction strategy and metadata structure
  - Document archival and retention policy
  - Document export bundle structure
- Add Swagger annotations to Scenario B controllers
- Ensure test coverage ≥ 75% for Scenario B service layer

**Files Modified:**
- docs/03-implementation-details.md

**Deliverable:** Documentation reflects Scenario B implementation; tests confirm functionality.

---

## Phase 3: Ambiguous Requirement (Scenario C — Compliance Reporting)

### Commit 18: Clarify Compliance Requirement
**Objective:** Document interpretation of vague requirement and design compliance-specific features.

**Tasks:**
- Create /docs/COMPLIANCE_CLARIFICATION.md:
  - Document original requirement: "Regulators need to be able to audit access to client account data"
  - List clarifications: What is "access"? What is "client account data"? What reports needed?
  - Document assumptions (see ASSUMPTION-007 in requirement analysis)
  - Propose implementation: ResourceType=ACCOUNT tagging, compliance-specific query API
- Identify compliance-relevant event types: READ_ACCOUNT, UPDATE_ACCOUNT, DELETE_ACCOUNT, GRANT_ACCESS, REVOKE_ACCESS
- Define compliance report structure:
  - Time period (from/to)
  - Actors involved
  - Resource accessed
  - Event types
  - Event details
  - Tamper-evidence verification

**Files Created:**
- docs/COMPLIANCE_CLARIFICATION.md

**Deliverable:** Compliance requirement clarified and documented.

---

### Commit 19: Compliance Report Service
**Objective:** Implement compliance-specific reporting and filtering.

**Tasks:**
- Create ComplianceReportService:
  - `generateAccountAccessReport(filter)`:
    1. Query events with resourceType=ACCOUNT
    2. Filter by actorId (optional), eventType (optional), time range (required)
    3. Group by resource and actor
    4. Generate statistics: totalAccess, readCount, updateCount, deleteCount, etc.
    5. Return ComplianceReportResponse
  - Filter validators: Ensure time range is within 7-year retention window
  - Helper: `computeComplianceStatistics()`, `formatForRegulators()`
- Create ComplianceController:
  - `GET /api/v1/compliance/account-access-report`
  - @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
  - Accept filters: actorId, from, to, eventTypes
  - Call ComplianceReportService.generateAccountAccessReport()
  - Return 200 with ComplianceReportResponse
- Create ComplianceReportResponse DTO:
  - reportTitle, period, records, statistics
  - Include verification metadata (can chain be verified?)
- Create compliance-specific query endpoint:
  - `GET /api/v1/compliance/records`
  - Return compliance-tagged events

**Files Created/Modified:**
- src/main/java/com/schwab/audit/service/ComplianceReportService.java (new)
- src/main/java/com/schwab/audit/controller/ComplianceController.java (new)
- src/main/java/com/schwab/audit/dto/request/ComplianceReportRequest.java (new)
- src/main/java/com/schwab/audit/dto/response/ComplianceReportResponse.java (new)
- src/test/java/com/schwab/audit/service/ComplianceReportServiceTest.java (new)

**Deliverable:** Compliance reports can be generated and filtered by time/actor/event type.

---

### Commit 20: Compliance Export with Regulatory Metadata
**Objective:** Export compliance reports with tamper-evidence certificates and regulatory metadata.

**Tasks:**
- Extend ExportService:
  - `exportComplianceReport(filter)`:
    1. Generate compliance report
    2. Verify chain integrity
    3. Include verification certificate
    4. Include regulatory metadata (retention period, archival policy, redaction policy)
    5. Package as signed bundle
    6. Return as downloadable attachment
  - `signBundle(bundle, signingKey)`: Optional: add signature for regulatory audit
- Create compliance export DTO:
  - report: ComplianceReportResponse
  - verificationCertificate: { isIntact, totalRecords, verificationTime }
  - regulatoryMetadata: { retentionPolicy, archivalPolicy, redactionPolicy }
- Create ComplianceExportController endpoint (or extend ComplianceController):
  - `POST /api/v1/compliance/export-report`
  - Accept filters, return signed bundle

**Files Created/Modified:**
- src/main/java/com/schwab/audit/service/ExportService.java (modify)
- src/main/java/com/schwab/audit/dto/response/ComplianceExportResponse.java (new)
- src/test/java/com/schwab/audit/service/ExportServiceTest.java (modify)

**Deliverable:** Compliance bundles include verification certificates and regulatory metadata.

---

### Commit 21: Integration Tests for Scenario C
**Objective:** End-to-end tests for compliance reporting and export.

**Tasks:**
- Write integration tests for ComplianceController:
  - `shouldGenerateAccountAccessReportFiltered()`
  - `shouldEnforceRetentionWindow()`
  - `shouldIncludeVerificationMetadata()`
  - `shouldExportComplianceReportWithSignature()`
- Create test data: Mixed events, some tagged as ACCOUNT resources
- Verify compliance reports match expectations

**Files Created:**
- src/test/java/com/schwab/audit/integration/ComplianceIntegrationTest.java (new)

**Deliverable:** 5+ integration tests for compliance workflows.

---

### Commit 22: Documentation for Scenario C
**Objective:** Update documentation with Scenario C implementation details.

**Tasks:**
- Update /docs/03-implementation-details.md:
  - Mark Scenario C tasks as completed
  - Document compliance requirement interpretation
  - Document compliance report structure
  - Document export and verification certificates
- Add Swagger annotations to ComplianceController
- Update /docs/COMPLIANCE_CLARIFICATION.md with final implementation details

**Files Modified:**
- docs/03-implementation-details.md
- docs/COMPLIANCE_CLARIFICATION.md

**Deliverable:** Scenario C implementation fully documented.

---

## Phase 4: Finalization and Review

### Commit 23: Final Testing and Quality Assurance
**Objective:** Ensure all tests pass, coverage is sufficient, and no regressions.

**Tasks:**
- Run full test suite: `mvn clean test`
- Check test coverage:
  - Target ≥ 80% for service layer (Scenario A + B + C)
  - Target ≥ 70% for controller layer
  - Target ≥ 60% overall
- Run integration tests with testcontainers
- Verify all endpoints via Swagger UI
- Manual smoke tests:
  1. Create event, query, verify chain
  2. Redact field, verify chain unchanged
  3. Archive old records, verify no false positives
  4. Export bundle, verify independently
  5. Generate compliance report, export with certificate
- Performance check:
  - Create 1000 events, measure latency
  - Verify chain (should be < 2s)
  - Query with complex filters (should be < 500ms)
- Code quality:
  - Run findbugs or spotbugs
  - Check for unused imports, dead code
  - Verify no hardcoded secrets in code

**Deliverable:** All tests passing, no regressions, quality gates met.

---

### Commit 24: Final Documentation and Cleanup
**Objective:** Complete all documentation and prepare for submission.

**Tasks:**
- Create /docs/05-final-review.md:
  - Requirements completed (checklist)
  - Scenarios completed (checklist)
  - API list with status codes
  - Security summary (authentication, authorization, validation)
  - Test summary (unit, integration, coverage)
  - Known limitations
  - Assumptions and trade-offs
  - Recommended future improvements
- Create README.md:
  - Project overview
  - Setup instructions
  - Running the application
  - Running tests
  - API documentation link (Swagger UI)
- Create ATTESTATION.md:
  - Full name, email
  - Assignment title
  - Dates started/submitted
  - Attestation statement
- Verify no commented-out code
- Verify all classes have proper JavaDoc
- Final commit with all documentation

**Files Created:**
- docs/05-final-review.md (new)
- README.md (new)
- ATTESTATION.md (new)

**Deliverable:** All documentation complete; repository ready for submission.

---

## Summary of Commits

| Commit # | Title | Scenario | Files | Status |
|----------|-------|----------|-------|--------|
| 1 | Project setup and dependencies | Foundation | 2 | NOT STARTED |
| 2 | Database configuration and schema | A | 4 | NOT STARTED |
| 3 | Security foundation (JWT + auth) | A | 10 | NOT STARTED |
| 4 | Core entities and DTOs | A | 14 | NOT STARTED |
| 5 | Write API (create event) | A | 12 | NOT STARTED |
| 6 | Query API (retrieve events) | A | 3 | NOT STARTED |
| 7 | Chain verification endpoint | A | 5 | NOT STARTED |
| 8 | Unit tests for Scenario A | A | 5 | NOT STARTED |
| 9 | Integration tests for Scenario A | A | 3 | NOT STARTED |
| 10 | Exception handling | A | 6 | NOT STARTED |
| 11 | OpenAPI/Swagger documentation | A | 3 | NOT STARTED |
| 12 | Database schema extension (archive) | B | 2 | NOT STARTED |
| 13 | Redaction service | B | 6 | NOT STARTED |
| 14 | Archive service | B | 5 | NOT STARTED |
| 15 | Bulk export service | B | 4 | NOT STARTED |
| 16 | Integration tests for Scenario B | B | 3 | NOT STARTED |
| 17 | Documentation for Scenario B | B | 1 | NOT STARTED |
| 18 | Clarify compliance requirement | C | 1 | NOT STARTED |
| 19 | Compliance report service | C | 5 | NOT STARTED |
| 20 | Compliance export with metadata | C | 3 | NOT STARTED |
| 21 | Integration tests for Scenario C | C | 1 | NOT STARTED |
| 22 | Documentation for Scenario C | C | 2 | NOT STARTED |
| 23 | Final testing and QA | All | - | NOT STARTED |
| 24 | Final documentation and cleanup | All | 3 | NOT STARTED |

**Total Estimated:**
- ~110 source files
- ~80 test files
- ~15 documentation/config files
- ~24 commits (logical, reviewable)

