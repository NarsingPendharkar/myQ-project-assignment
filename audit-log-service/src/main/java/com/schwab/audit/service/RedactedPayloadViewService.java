package com.schwab.audit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.entity.AuditEventRedaction;
import com.schwab.audit.repository.AuditEventRedactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Applies redactions to output copies only; the stored, hashed event remains unchanged. */
@Service
@RequiredArgsConstructor
public class RedactedPayloadViewService {
    private final AuditEventRedactionRepository redactionRepository;
    private final ObjectMapper objectMapper;
    public String payloadForView(AuditEvent event) {
        if (event.getPayload() == null || event.getId() == null) return event.getPayload();
        List<AuditEventRedaction> redactions = redactionRepository.findAllByAuditEventId(event.getId());
        if (redactions.isEmpty()) return event.getPayload();
        try {
            JsonNode root = objectMapper.readTree(event.getPayload());
            if (!(root instanceof ObjectNode)) return event.getPayload();
            for (AuditEventRedaction redaction : redactions) {
                for (String path : objectMapper.readValue(redaction.getRedactedFields(), new TypeReference<List<String>>() {})) mask(root, path.split("\\."), 0);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) { return "{\"redactionStatus\":\"UNAVAILABLE\"}"; }
    }

    public boolean isRedacted(AuditEvent event) {
        return event.getId() != null && redactionRepository.existsByAuditEventId(event.getId());
    }
    private void mask(JsonNode node, String[] parts, int index) {
        if (!(node instanceof ObjectNode object) || index >= parts.length) return;
        if (index == parts.length - 1 && object.has(parts[index])) object.put(parts[index], "***REDACTED***");
        else mask(object.get(parts[index]), parts, index + 1);
    }
}
