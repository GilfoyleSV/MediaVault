package ru.kuzmin.mediavault.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kuzmin.mediavault.config.MinioProperties;
import ru.kuzmin.mediavault.dto.AccessUrlResponse;
import ru.kuzmin.mediavault.dto.CompleteUploadRequest;
import ru.kuzmin.mediavault.dto.MediaResponse;
import ru.kuzmin.mediavault.dto.PresignedUrlDto;
import ru.kuzmin.mediavault.dto.UploadRequest;
import ru.kuzmin.mediavault.dto.UploadResponse;
import ru.kuzmin.mediavault.entity.MediaAsset;
import ru.kuzmin.mediavault.exception.InvalidFileSizeException;
import ru.kuzmin.mediavault.exception.InvalidMediaStateException;
import ru.kuzmin.mediavault.exception.MediaNotFoundException;
import ru.kuzmin.mediavault.exception.UnsupportedMediaTypeException;
import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.model.MediaType;
import ru.kuzmin.mediavault.repository.MediaAssetRepository;
import ru.kuzmin.mediavault.service.MediaService;
import ru.kuzmin.mediavault.service.StorageService;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private static final UUID DEFAULT_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;
    private final MinioProperties minioProperties;

    public MediaServiceImpl(MediaAssetRepository mediaAssetRepository,
                            StorageService storageService,
                            MinioProperties minioProperties) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.storageService = storageService;
        this.minioProperties = minioProperties;
    }

    @Override
    @Transactional
    public UploadResponse initiateUpload(UploadRequest request, UUID ownerId) {
        MediaType mediaType = resolveMediaType(request.mimeType());
        UUID mediaId = UUID.randomUUID();
        UUID effectiveOwnerId = (ownerId != null) ? ownerId : DEFAULT_OWNER_ID;

        String storageKey = generateStorageKey(mediaType, mediaId, request.filename());
        String bucket = minioProperties.getBucket();

        log.info("Initiating upload for file='{}', mimeType='{}', size={} bytes, storageKey='{}'",
                request.filename(), request.mimeType(), request.sizeBytes(), storageKey);

        MediaAsset asset = new MediaAsset();
        asset.setId(mediaId);
        asset.setOwnerId(effectiveOwnerId);
        asset.setStorageBucket(bucket);
        asset.setStorageKey(storageKey);
        asset.setOriginalFilename(request.filename());
        asset.setMimeType(request.mimeType());
        asset.setSizeBytes(request.sizeBytes());
        asset.setMediaType(mediaType);
        asset.setMediaStatus(MediaStatus.PENDING_UPLOAD);

        mediaAssetRepository.save(asset);

        PresignedUrlDto presignedUrl = storageService.generatePresignedUploadUrl(
                bucket,
                storageKey,
                request.mimeType(),
                minioProperties.getUploadExpiration()
        );

        return new UploadResponse(mediaId, presignedUrl.url(), presignedUrl.expiresAt());
    }

    @Override
    @Transactional
    public MediaResponse completeUpload(UUID mediaId, CompleteUploadRequest request) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media asset not found with ID: " + mediaId));

        if (asset.getMediaStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new InvalidMediaStateException("Cannot complete upload. Expected status PENDING_UPLOAD, but was: "
                    + asset.getMediaStatus());
        }

        log.info("Verifying upload in storage for mediaId='{}', storageKey='{}'", mediaId, asset.getStorageKey());

        HeadObjectResponse headObject = storageService.headObject(asset.getStorageBucket(), asset.getStorageKey());
        long actualSize = headObject.contentLength();

        if (!Objects.equals(actualSize, asset.getSizeBytes())) {
            log.error("File size mismatch for mediaId={}: expected={} bytes, actual={} bytes",
                    mediaId, asset.getSizeBytes(), actualSize);
            asset.setMediaStatus(MediaStatus.FAILED);
            mediaAssetRepository.save(asset);
            throw new InvalidFileSizeException(String.format(
                    "Uploaded file size (%d bytes) does not match declared size (%d bytes)",
                    actualSize, asset.getSizeBytes()
            ));
        }

        if (request != null && request.checksumSha256() != null && !request.checksumSha256().isBlank()) {
            asset.setChecksumSha256(request.checksumSha256());
        }

        asset.setMediaStatus(MediaStatus.READY);
        MediaAsset savedAsset = mediaAssetRepository.save(asset);

        log.info("Media upload completed successfully for mediaId='{}'", mediaId);

        return new MediaResponse(
                savedAsset.getId(),
                savedAsset.getMediaStatus(),
                savedAsset.getOriginalFilename(),
                savedAsset.getMediaType(),
                savedAsset.getMimeType(),
                savedAsset.getSizeBytes(),
                savedAsset.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AccessUrlResponse getAccessUrl(UUID mediaId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media asset not found with ID: " + mediaId));

        if (asset.getMediaStatus() != MediaStatus.READY) {
            throw new InvalidMediaStateException("Media is not ready for streaming or download. Current status: "
                    + asset.getMediaStatus());
        }

        PresignedUrlDto presignedUrl = storageService.generatePresignedDownloadUrl(
                asset.getStorageBucket(),
                asset.getStorageKey(),
                minioProperties.getDownloadExpiration()
        );

        return new AccessUrlResponse(
                asset.getId(),
                presignedUrl.url(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                presignedUrl.expiresAt()
        );
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new UnsupportedMediaTypeException("MIME type must not be empty");
        }
        String normalized = mimeType.trim().toLowerCase();
        if (normalized.startsWith("image/")) {
            return MediaType.IMAGE;
        } else if (normalized.startsWith("audio/")) {
            return MediaType.AUDIO;
        } else if (normalized.startsWith("video/")) {
            return MediaType.VIDEO;
        } else {
            throw new UnsupportedMediaTypeException("Unsupported MIME type: '" + mimeType
                    + "'. Supported media types are images (image/*), audio (audio/*), and video (video/*).");
        }
    }

    private String generateStorageKey(MediaType mediaType, UUID mediaId, String originalFilename) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        String month = String.format("%02d", now.getMonthValue());
        String extension = extractExtension(originalFilename);

        return String.format("%s/%d/%s/%s%s",
                mediaType.name().toLowerCase(),
                year,
                month,
                mediaId,
                extension
        );
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return filename.substring(lastDotIndex);
    }
}
