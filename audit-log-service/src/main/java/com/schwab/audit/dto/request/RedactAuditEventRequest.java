package com.schwab.audit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Request for a view-level payload redaction. The caller identity comes from authentication. */
@Data
public class RedactAuditEventRequest {
    @NotEmpty(message = "At least one field is required")
    private List<@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Invalid field path") String> fields;

    @NotBlank(message = "Redaction reason is required")
    @Size(max = 255)
    private String reason;
}
