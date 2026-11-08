package ru.kuzmin.mediavault.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kuzmin.mediavault.dto.AccessUrlResponse;
import ru.kuzmin.mediavault.dto.CompleteUploadRequest;
import ru.kuzmin.mediavault.dto.MediaResponse;
import ru.kuzmin.mediavault.dto.ThumbnailUrlResponse;
import ru.kuzmin.mediavault.dto.UploadRequest;
import ru.kuzmin.mediavault.dto.UploadResponse;
import ru.kuzmin.mediavault.service.MediaService;
import ru.kuzmin.mediavault.service.UploadRateLimiter;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {
    private final MediaService mediaService;
    private final Optional<UploadRateLimiter> uploadRateLimiter;

    public MediaController(MediaService mediaService, Optional<UploadRateLimiter> uploadRateLimiter) {
        this.mediaService = mediaService;
        this.uploadRateLimiter = uploadRateLimiter;
    }

    @PostMapping({"/uploads", "/upload"})
    public ResponseEntity<UploadResponse> initiateUpload(@Valid @RequestBody UploadRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = owner(jwt);
        uploadRateLimiter.ifPresent(limiter -> limiter.acquire(ownerId));
        UploadResponse response = mediaService.initiateUpload(request, ownerId);
        return ResponseEntity.created(URI.create("/api/v1/media/" + response.mediaId()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/{mediaId}/complete")
    public MediaResponse completeUpload(@PathVariable UUID mediaId, @AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody(required = false) CompleteUploadRequest request) {
        return mediaService.completeUpload(mediaId, owner(jwt), request);
    }

    @GetMapping("/{mediaId}/access-url")
    public ResponseEntity<AccessUrlResponse> getAccessUrl(@PathVariable UUID mediaId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mediaService.getAccessUrl(mediaId, owner(jwt)));
    }

    @GetMapping("/{mediaId}")
    public MediaResponse getMedia(@PathVariable UUID mediaId, @AuthenticationPrincipal Jwt jwt) {
        return mediaService.getMedia(mediaId, owner(jwt));
    }

    @GetMapping("/{mediaId}/thumbnail-url")
    public ResponseEntity<ThumbnailUrlResponse> getThumbnailUrl(@PathVariable UUID mediaId,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mediaService.getThumbnailUrl(mediaId, owner(jwt)));
    }

    @GetMapping
    public MediaPage listMedia(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                               @AuthenticationPrincipal Jwt jwt) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Slice<MediaResponse> result = mediaService.listMedia(owner(jwt), request);
        return new MediaPage(result.getContent(), page, size, result.hasNext());
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable UUID mediaId, @AuthenticationPrincipal Jwt jwt) {
        mediaService.deleteMedia(mediaId, owner(jwt));
    }

    private static UUID owner(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record MediaPage(List<MediaResponse> items, int page, int size, boolean hasNext) {}
}
