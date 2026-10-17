package ru.kuzmin.mediavault.dto;

import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.model.MediaType;

import java.time.Instant;
import java.util.UUID;

public record MediaResponse(
        UUID mediaId,
        MediaStatus status,
        String originalFilename,
        MediaType mediaType,
        String mimeType,
        Long sizeBytes,
        Instant createdAt
) {}
