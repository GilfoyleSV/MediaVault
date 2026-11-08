package ru.kuzmin.mediavault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record Credentials(
        @NotNull @Pattern(regexp = "[a-z0-9_]{3,40}") String username,
        @NotNull @Pattern(regexp = "[ -~]{12,72}")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password
) {}
