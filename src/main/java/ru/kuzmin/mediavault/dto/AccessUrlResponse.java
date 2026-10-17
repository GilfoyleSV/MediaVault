package ru.kuzmin.mediavault.dto;

import java.time.Instant;
import java.util.UUID;

public record AccessUrlResponse(
        UUID mediaId,
        String downloadUrl,
        String mimeType,
        Long sizeBytes,
        Instant expiresAt
) {}
