package ru.kuzmin.mediavault.service;

import ru.kuzmin.mediavault.dto.PresignedUrlDto;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Duration;

public interface StorageService {
    PresignedUrlDto generatePresignedUploadUrl(String bucket, String key, String mimeType,
                                               long size, String checksumBase64, Duration duration);
    PresignedUrlDto generatePresignedDownloadUrl(String bucket, String key, Duration duration);
    HeadObjectResponse headObject(String bucket, String key, boolean includeChecksum);
    byte[] readObject(String bucket, String key, int maxBytes);
    void putThumbnail(String bucket, String key, byte[] jpeg);
    void deleteObject(String bucket, String key);
    void ensureBucketExists(String bucket);
}
