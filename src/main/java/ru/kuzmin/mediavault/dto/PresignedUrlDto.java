package ru.kuzmin.mediavault.dto;

import java.time.Instant;

public record PresignedUrlDto(
        String url,
        Instant expiresAt
) {}
