package ru.kuzmin.mediavault.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UploadResponse(
        UUID mediaId,
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> uploadHeaders
) {}
