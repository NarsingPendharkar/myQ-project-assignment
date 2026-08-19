package com.schwab.audit.service;

import com.schwab.audit.entity.AuditEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Defines the single, stable representation used when hashing audit events.
 * Creation and verification must use this exact component so verification
 * detects any persisted content change.
 */
@Component
public class EventContentHasher {

    public String buildContent(String eventType, String actorId, String resourceType,
                               String resourceId, String payload, LocalDateTime timestamp) {
        return String.format("%s|%s|%s|%s|%s|%s",
                eventType,
                actorId,
                resourceType,
                resourceId,
                payload != null ? payload : "",
                timestamp != null ? timestamp.toString() : "");
    }

    public String buildContent(AuditEvent event) {
        return buildContent(event.getEventType(), event.getActorId(), event.getResourceType(),
                event.getResourceId(), event.getPayload(), event.getTimestamp());
    }
}
