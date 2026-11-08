package ru.kuzmin.mediavault.service.impl;

import org.springframework.stereotype.Service;
import ru.kuzmin.mediavault.dto.PresignedUrlDto;
import ru.kuzmin.mediavault.exception.InvalidMediaStateException;
import ru.kuzmin.mediavault.exception.InvalidFileSizeException;
import ru.kuzmin.mediavault.exception.StorageException;
import ru.kuzmin.mediavault.service.StorageService;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MinioStorageService implements StorageService {
    private final S3Client s3;
    private final S3Presigner presigner;

    public MinioStorageService(S3Client s3, S3Presigner presigner) {
        this.s3 = s3;
        this.presigner = presigner;
    }

    @Override
    public PresignedUrlDto generatePresignedUploadUrl(String bucket, String key, String mimeType,
                                                       long size, String checksumBase64, Duration duration) {
        try {
            var request = presigner.presignPutObject(spec -> spec.signatureDuration(duration)
                    .putObjectRequest(put -> put.bucket(bucket).key(key)
                            .contentType(mimeType).contentLength(size).ifNoneMatch("*")
                            .checksumSHA256(checksumBase64)));
            Map<String, String> headers = request.signedHeaders().entrySet().stream()
                    .filter(header -> !header.getKey().equalsIgnoreCase("host"))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, header -> String.join(",", header.getValue())));
            return new PresignedUrlDto(request.url().toString(), request.expiration(), headers);
        } catch (SdkException e) {
            throw new StorageException("Could not sign upload request", e);
        }
    }

    @Override
    public PresignedUrlDto generatePresignedDownloadUrl(String bucket, String key, Duration duration) {
        try {
            var request = presigner.presignGetObject(spec -> spec.signatureDuration(duration)
                    .getObjectRequest(get -> get.bucket(bucket).key(key)));
            return new PresignedUrlDto(request.url().toString(), request.expiration(), Map.of());
        } catch (SdkException e) {
            throw new StorageException("Could not sign download request", e);
        }
    }

    @Override
    public HeadObjectResponse headObject(String bucket, String key, boolean includeChecksum) {
        try {
            return s3.headObject(head -> head.bucket(bucket).key(key)
                    .checksumMode(includeChecksum ? ChecksumMode.ENABLED : null));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new InvalidMediaStateException("Upload the object before calling complete");
            }
            throw new StorageException("Could not read object metadata", e);
        } catch (SdkException e) {
            throw new StorageException("Could not read object metadata", e);
        }
    }

    @Override
    public byte[] readObject(String bucket, String key, int maxBytes) {
        try (var object = s3.getObject(get -> get.bucket(bucket).key(key))) {
            byte[] content = object.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) {
                object.abort();
                throw new InvalidFileSizeException("Object exceeds the thumbnail input limit");
            }
            return content;
        } catch (SdkException | IOException e) {
            throw new StorageException("Could not read thumbnail source", e);
        }
    }

    @Override
    public void putThumbnail(String bucket, String key, byte[] jpeg) {
        try {
            s3.putObject(put -> put.bucket(bucket).key(key).contentType("image/jpeg"), RequestBody.fromBytes(jpeg));
        } catch (SdkException e) {
            throw new StorageException("Could not store thumbnail", e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            s3.deleteObject(delete -> delete.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new StorageException("Could not delete object", e);
        }
    }

    @Override
    public void ensureBucketExists(String bucket) {
        try {
            s3.headBucket(head -> head.bucket(bucket));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            try {
                s3.createBucket(create -> create.bucket(bucket));
            } catch (BucketAlreadyOwnedByYouException race) {
                s3.headBucket(head -> head.bucket(bucket));
            }
        }
    }
}
