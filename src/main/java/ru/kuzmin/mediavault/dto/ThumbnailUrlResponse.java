package ru.kuzmin.mediavault.dto;

import java.time.Instant;
import java.util.UUID;

public record ThumbnailUrlResponse(UUID mediaId, String downloadUrl, Instant expiresAt) {}
