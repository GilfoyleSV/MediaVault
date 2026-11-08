package ru.kuzmin.mediavault.dto;

import java.time.Instant;
import java.util.Map;

public record PresignedUrlDto(String url, Instant expiresAt, Map<String, String> headers) {}
