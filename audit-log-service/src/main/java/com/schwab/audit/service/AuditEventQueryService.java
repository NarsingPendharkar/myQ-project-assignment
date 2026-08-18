package com.schwab.audit.service;

import com.schwab.audit.dto.request.AuditEventFilterRequest;
import com.schwab.audit.dto.response.AuditEventResponse;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for advanced filtering and querying of audit events.
 * Supports complex filter criteria combinations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditEventQueryService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Executes an advanced filtered query with multiple criteria.
     * 
     * @param filter the filter request with multiple optional criteria
     * @return page of matching events
     */
    public Page<AuditEventResponse> executeFilteredQuery(AuditEventFilterRequest filter) {
        log.debug("Executing filtered query with criteria: eventType={}, actor={}, resource={}, archived={}", 
                 filter.getEventType(), filter.getActorId(), filter.getResourceType(), filter.getArchived());

        // Build pageable with sorting
        Sort.Direction direction = "ASC".equalsIgnoreCase(filter.getSortDirection()) 
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, filter.getSortBy());
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        // Apply filters in order of specificity
        if (filter.getEventType() != null && filter.getActorId() != null && 
            filter.getResourceType() != null && filter.getResourceId() != null) {
            // Most specific: all four criteria
            return auditEventRepository.findByEventTypeAndActorIdAndResourceTypeAndResourceId(
                    filter.getEventType(), filter.getActorId(), 
                    filter.getResourceType(), filter.getResourceId(), pageable)
                .map(this::mapToResponse);
        }

        if (filter.getResourceType() != null && filter.getResourceId() != null) {
            // By resource with optional event type filter
            var events = auditEventRepository.findByResourceTypeAndResourceId(
                    filter.getResourceType(), filter.getResourceId(), pageable);
            if (filter.getEventType() != null) {
                var filtered = events.stream()
                        .filter(e -> e.getEventType().equals(filter.getEventType()))
                        .collect(Collectors.toList());
                return new org.springframework.data.domain.PageImpl<>(
                        filtered.stream().map(this::mapToResponse).collect(Collectors.toList()),
                        pageable, events.getTotalElements());
            }
            return events.map(this::mapToResponse);
        }

        if (filter.getActorId() != null) {
            // By actor
            return auditEventRepository.findByActorId(filter.getActorId(), pageable)
                .map(this::mapToResponse);
        }

        if (filter.getEventType() != null) {
            // By event type
            return auditEventRepository.findByEventType(filter.getEventType(), pageable)
                .map(this::mapToResponse);
        }

        if (filter.getStartTime() != null && filter.getEndTime() != null) {
            // By timestamp range
            return auditEventRepository.findByTimestampRange(
                    filter.getStartTime(), filter.getEndTime(), pageable)
                .map(this::mapToResponse);
        }

        if (filter.getArchived() != null) {
            // By archive status
            return filter.getArchived() 
                ? auditEventRepository.findByArchivedTrue(pageable)
                : auditEventRepository.findByArchivedFalse(pageable);
        }

        // No filters - return all
        return auditEventRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Maps AuditEvent entity to response DTO.
     */
    private AuditEventResponse mapToResponse(AuditEvent event) {
        return AuditEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .actorId(event.getActorId())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId())
                .payload(event.getPayload())
                .timestamp(event.getTimestamp())
                .createdAt(event.getCreatedAt())
                .archivedAt(event.getArchivedAt())
                .contentHash(event.getContentHash())
                .previousHash(event.getPreviousHash())
                .chainPosition(event.getChainPosition())
                .archived(event.getArchived())
                .build();
    }
}
