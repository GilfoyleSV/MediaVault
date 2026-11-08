package ru.kuzmin.mediavault.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kuzmin.mediavault.config.MinioProperties;
import ru.kuzmin.mediavault.dto.AccessUrlResponse;
import ru.kuzmin.mediavault.dto.CompleteUploadRequest;
import ru.kuzmin.mediavault.dto.MediaResponse;
import ru.kuzmin.mediavault.dto.PresignedUrlDto;
import ru.kuzmin.mediavault.dto.ThumbnailUrlResponse;
import ru.kuzmin.mediavault.dto.UploadRequest;
import ru.kuzmin.mediavault.dto.UploadResponse;
import ru.kuzmin.mediavault.entity.MediaAsset;
import ru.kuzmin.mediavault.exception.InvalidFileSizeException;
import ru.kuzmin.mediavault.exception.InvalidMediaStateException;
import ru.kuzmin.mediavault.exception.MediaNotFoundException;
import ru.kuzmin.mediavault.exception.UnsupportedMediaTypeException;
import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.model.MediaType;
import ru.kuzmin.mediavault.model.ThumbnailStatus;
import ru.kuzmin.mediavault.repository.MediaAssetRepository;
import ru.kuzmin.mediavault.service.MediaService;
import ru.kuzmin.mediavault.service.StorageService;
import ru.kuzmin.mediavault.service.ThumbnailOutbox;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaServiceImpl implements MediaService {
    private static final Duration UPLOAD_CLEANUP_GRACE = Duration.ofHours(24);

    private final MediaAssetRepository assets;
    private final StorageService storage;
    private final MinioProperties properties;
    private final long maxSize;
    private final Optional<ThumbnailOutbox> thumbnailOutbox;

    public MediaServiceImpl(MediaAssetRepository assets, StorageService storage, MinioProperties properties,
                            Optional<ThumbnailOutbox> thumbnailOutbox,
                            @Value("${media.max-size-bytes}") long maxSize) {
        this.assets = assets;
        this.storage = storage;
        this.properties = properties;
        this.maxSize = maxSize;
        this.thumbnailOutbox = thumbnailOutbox;
    }

    @Override
    @Transactional
    public UploadResponse initiateUpload(UploadRequest request, UUID ownerId) {
        if (request.sizeBytes() > maxSize) {
            throw new InvalidFileSizeException("Maximum upload size is " + maxSize + " bytes");
        }

        String mimeType = request.mimeType().toLowerCase(Locale.ROOT);
        MediaType mediaType = resolveMediaType(mimeType);
        UUID mediaId = UUID.randomUUID();
        String key = mediaType.name().toLowerCase(Locale.ROOT) + "/" + mediaId;
        String checksum = normalizeChecksum(request.checksumSha256());
        PresignedUrlDto url = storage.generatePresignedUploadUrl(properties.getBucket(), key,
                mimeType, request.sizeBytes(), toBase64(checksum), properties.getUploadExpiration());

        MediaAsset asset = new MediaAsset();
        asset.setId(mediaId);
        asset.setOwnerId(ownerId);
        asset.setStorageBucket(properties.getBucket());
        asset.setStorageKey(key);
        asset.setOriginalFilename(request.filename());
        asset.setMimeType(mimeType);
        asset.setSizeBytes(request.sizeBytes());
        asset.setChecksumSha256(checksum);
        asset.setMediaType(mediaType);
        asset.setMediaStatus(MediaStatus.PENDING_UPLOAD);
        asset.setCleanupAt(url.expiresAt().plus(UPLOAD_CLEANUP_GRACE));
        assets.save(asset);
        return new UploadResponse(mediaId, url.url(), url.expiresAt(), url.headers());
    }

    @Override
    @Transactional(noRollbackFor = {InvalidFileSizeException.class, InvalidMediaStateException.class})
    public MediaResponse completeUpload(UUID mediaId, UUID ownerId, CompleteUploadRequest request) {
        MediaAsset asset = lockOwned(mediaId, ownerId);
        if (asset.getMediaStatus() == MediaStatus.DELETED) {
            throw notFound(mediaId);
        }
        String checksum = request == null ? null : normalizeChecksum(request.checksumSha256());
        String declaredChecksum = asset.getChecksumSha256();
        if (checksum != null && declaredChecksum != null && !checksum.equalsIgnoreCase(declaredChecksum)) {
            throw new InvalidMediaStateException("Checksum does not match the declared value");
        }
        if (asset.getMediaStatus() == MediaStatus.READY) {
            if (checksum != null && declaredChecksum == null) {
                throw new InvalidMediaStateException("No checksum was verified for this upload");
            }
            return toResponse(asset);
        }
        if (asset.getMediaStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new InvalidMediaStateException("Only pending uploads can be completed");
        }

        String expectedChecksum = declaredChecksum != null ? declaredChecksum : checksum;
        HeadObjectResponse object = storage.headObject(asset.getStorageBucket(), asset.getStorageKey(),
                expectedChecksum != null);
        if (!Objects.equals(object.contentLength(), asset.getSizeBytes())) {
            asset.setMediaStatus(MediaStatus.FAILED);
            throw new InvalidFileSizeException("Uploaded size differs from the declared size");
        }
        if (!asset.getMimeType().equalsIgnoreCase(object.contentType())) {
            asset.setMediaStatus(MediaStatus.FAILED);
            throw new InvalidMediaStateException("Uploaded Content-Type differs from the declared type");
        }
        if (expectedChecksum != null && !Objects.equals(toBase64(expectedChecksum), object.checksumSHA256())) {
            if (declaredChecksum != null) {
                asset.setMediaStatus(MediaStatus.FAILED);
            }
            throw new InvalidMediaStateException("Storage did not report the expected SHA-256 checksum");
        }
        asset.setChecksumSha256(expectedChecksum);
        asset.setMediaStatus(MediaStatus.READY);
        thumbnailOutbox.ifPresent(outbox -> outbox.enqueue(asset));
        return toResponse(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessUrlResponse getAccessUrl(UUID mediaId, UUID ownerId) {
        MediaAsset asset = findOwned(mediaId, ownerId);
        if (asset.getMediaStatus() != MediaStatus.READY) {
            throw new InvalidMediaStateException("Only ready media can be downloaded");
        }
        PresignedUrlDto url = storage.generatePresignedDownloadUrl(asset.getStorageBucket(),
                asset.getStorageKey(), properties.getDownloadExpiration());
        return new AccessUrlResponse(mediaId, url.url(), asset.getMimeType(), asset.getSizeBytes(), url.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public ThumbnailUrlResponse getThumbnailUrl(UUID mediaId, UUID ownerId) {
        MediaAsset asset = findOwned(mediaId, ownerId);
        if (asset.getThumbnailStatus() == null) {
            throw new MediaNotFoundException("Thumbnail not found for media: " + mediaId);
        }
        if (asset.getThumbnailStatus() != ThumbnailStatus.READY) {
            throw new InvalidMediaStateException("Thumbnail status: " + asset.getThumbnailStatus());
        }
        PresignedUrlDto url = storage.generatePresignedDownloadUrl(asset.getStorageBucket(),
                asset.thumbnailKey(), properties.getDownloadExpiration());
        return new ThumbnailUrlResponse(mediaId, url.url(), url.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public MediaResponse getMedia(UUID mediaId, UUID ownerId) {
        return toResponse(findOwned(mediaId, ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<MediaResponse> listMedia(UUID ownerId, Pageable page) {
        return assets.findAllByOwnerIdAndMediaStatusNot(ownerId, MediaStatus.DELETED, page)
                .map(MediaServiceImpl::toResponse);
    }

    @Override
    @Transactional
    public void deleteMedia(UUID mediaId, UUID ownerId) {
        MediaAsset asset = lockOwned(mediaId, ownerId);
        if (asset.getMediaStatus() == MediaStatus.DELETED) {
            return;
        }
        // A failed upload may already have been cleaned up.
        if (asset.getCleanupAt() == null && asset.getMediaStatus() == MediaStatus.READY) {
            asset.setCleanupAt(Instant.now());
        }
        asset.setMediaStatus(MediaStatus.DELETED);
    }

    private MediaAsset findOwned(UUID id, UUID ownerId) {
        return assets.findByIdAndOwnerIdAndMediaStatusNot(id, ownerId, MediaStatus.DELETED)
                .orElseThrow(() -> notFound(id));
    }

    private MediaAsset lockOwned(UUID id, UUID ownerId) {
        return assets.lockOwned(id, ownerId).orElseThrow(() -> notFound(id));
    }

    private static MediaNotFoundException notFound(UUID id) {
        return new MediaNotFoundException("Media asset not found: " + id);
    }

    private static MediaResponse toResponse(MediaAsset asset) {
        return new MediaResponse(asset.getId(), asset.getMediaStatus(), asset.getOriginalFilename(),
                asset.getMediaType(), asset.getMimeType(), asset.getSizeBytes(), asset.getCreatedAt(),
                asset.getThumbnailStatus());
    }

    private static MediaType resolveMediaType(String mimeType) {
        return switch (mimeType.substring(0, mimeType.indexOf('/'))) {
            case "image" -> MediaType.IMAGE;
            case "audio" -> MediaType.AUDIO;
            case "video" -> MediaType.VIDEO;
            default -> throw new UnsupportedMediaTypeException("Supported media types are image, audio and video");
        };
    }

    private static String normalizeChecksum(String hex) {
        return hex == null ? null : hex.toLowerCase(Locale.ROOT);
    }

    private static String toBase64(String hex) {
        return hex == null ? null : Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hex));
    }
}
