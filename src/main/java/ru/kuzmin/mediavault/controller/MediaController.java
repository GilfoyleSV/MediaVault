package ru.kuzmin.mediavault.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.kuzmin.mediavault.dto.AccessUrlResponse;
import ru.kuzmin.mediavault.dto.CompleteUploadRequest;
import ru.kuzmin.mediavault.dto.MediaResponse;
import ru.kuzmin.mediavault.dto.UploadRequest;
import ru.kuzmin.mediavault.dto.UploadResponse;
import ru.kuzmin.mediavault.service.MediaService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * Инициализация загрузки файла напрямую в MinIO через Presigned PUT URL.
     */
    @PostMapping(path = {"/uploads", "/upload"})
    public ResponseEntity<UploadResponse> initiateUpload(
            @Valid @RequestBody UploadRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID ownerId) {

        UploadResponse response = mediaService.initiateUpload(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Подтверждение завершения загрузки клиентом и верификация объекта в MinIO.
     */
    @PostMapping("/{mediaId}/complete")
    public ResponseEntity<MediaResponse> completeUpload(
            @PathVariable UUID mediaId,
            @RequestBody(required = false) CompleteUploadRequest request) {

        MediaResponse response = mediaService.completeUpload(mediaId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Получение Presigned GET URL для доступа, скачивания или потокового воспроизведения медиа.
     */
    @GetMapping("/{mediaId}/access-url")
    public ResponseEntity<AccessUrlResponse> getAccessUrl(@PathVariable UUID mediaId) {
        AccessUrlResponse response = mediaService.getAccessUrl(mediaId);
        return ResponseEntity.ok(response);
    }
}
