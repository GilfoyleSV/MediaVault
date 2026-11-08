package ru.kuzmin.mediavault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UploadRequest(
        @NotBlank(message = "Filename must not be blank")
        @Size(max = 255)
        @Pattern(regexp = "[^\\p{Cntrl}]+")
        String filename,

        @NotBlank(message = "MIME type must not be blank")
        @Size(max = 100)
        @Pattern(regexp = "(?i)[a-z]+/[a-z0-9][a-z0-9.+-]*")
        String mimeType,

        @NotNull(message = "Size in bytes must be provided")
        @Positive(message = "Size in bytes must be greater than zero")
        Long sizeBytes,

        @Pattern(regexp = "[0-9a-fA-F]{64}")
        String checksumSha256
) {}
