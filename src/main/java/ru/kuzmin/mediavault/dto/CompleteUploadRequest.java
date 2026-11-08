package ru.kuzmin.mediavault.dto;

import jakarta.validation.constraints.Pattern;

public record CompleteUploadRequest(
        @Pattern(regexp = "[0-9a-fA-F]{64}")
        String checksumSha256
) {}
