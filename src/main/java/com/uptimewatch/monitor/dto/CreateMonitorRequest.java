package com.uptimewatch.monitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMonitorRequest(
        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "must be an http:// or https:// URL without spaces")
        String url,

        /* Optional group on the status page; defaults to "Other". */
        @Size(max = 60)
        String category,

        /* Optional; defaults to 60. A 30 s floor keeps us polite to the sites we watch. */
        @Min(30) @Max(3600)
        Integer intervalSeconds
) {
}
