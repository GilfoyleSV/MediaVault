package ru.kuzmin.mediavault.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import ru.kuzmin.mediavault.dto.AccessUrlResponse;
import ru.kuzmin.mediavault.dto.CompleteUploadRequest;
import ru.kuzmin.mediavault.dto.MediaResponse;
import ru.kuzmin.mediavault.dto.ThumbnailUrlResponse;
import ru.kuzmin.mediavault.dto.UploadRequest;
import ru.kuzmin.mediavault.dto.UploadResponse;

import java.util.UUID;

public interface MediaService {
    UploadResponse initiateUpload(UploadRequest request, UUID ownerId);
    MediaResponse completeUpload(UUID mediaId, UUID ownerId, CompleteUploadRequest request);
    AccessUrlResponse getAccessUrl(UUID mediaId, UUID ownerId);
    ThumbnailUrlResponse getThumbnailUrl(UUID mediaId, UUID ownerId);
    MediaResponse getMedia(UUID mediaId, UUID ownerId);
    Slice<MediaResponse> listMedia(UUID ownerId, Pageable page);
    void deleteMedia(UUID mediaId, UUID ownerId);
}
