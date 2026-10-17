package ru.kuzmin.mediavault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UploadRequest(
        @NotBlank(message = "Filename must not be blank")
        String filename,

        @NotBlank(message = "MIME type must not be blank")
        String mimeType,

        @NotNull(message = "Size in bytes must be provided")
        @Positive(message = "Size in bytes must be greater than zero")
        Long sizeBytes
) {}
