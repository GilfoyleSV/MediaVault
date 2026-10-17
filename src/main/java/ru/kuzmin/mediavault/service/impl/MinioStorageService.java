package ru.kuzmin.mediavault.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.kuzmin.mediavault.dto.PresignedUrlDto;
import ru.kuzmin.mediavault.exception.StorageException;
import ru.kuzmin.mediavault.service.StorageService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public MinioStorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public PresignedUrlDto generatePresignedUploadUrl(String bucket, String key, String mimeType, Duration duration) {
        try {
            log.debug("Generating presigned PUT URL for bucket={}, key={}, mimeType={}, duration={}",
                    bucket, key, mimeType, duration);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(mimeType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            return new PresignedUrlDto(presignedRequest.url().toString(), presignedRequest.expiration());
        } catch (Exception e) {
            log.error("Failed to generate presigned PUT URL for key={}: {}", key, e.getMessage(), e);
            throw new StorageException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    @Override
    public PresignedUrlDto generatePresignedDownloadUrl(String bucket, String key, Duration duration) {
        try {
            log.debug("Generating presigned GET URL for bucket={}, key={}, duration={}",
                    bucket, key, duration);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            return new PresignedUrlDto(presignedRequest.url().toString(), presignedRequest.expiration());
        } catch (Exception e) {
            log.error("Failed to generate presigned GET URL for key={}: {}", key, e.getMessage(), e);
            throw new StorageException("Failed to generate presigned access URL: " + e.getMessage(), e);
        }
    }

    @Override
    public HeadObjectResponse headObject(String bucket, String key) {
        try {
            log.debug("Executing HeadObject for bucket={}, key={}", bucket, key);
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (NoSuchKeyException e) {
            log.warn("Object not found in storage: bucket={}, key={}", bucket, key);
            throw new StorageException("Object not found in MinIO storage: " + key, e);
        } catch (Exception e) {
            log.error("Error executing HeadObject for bucket={}, key={}: {}", bucket, key, e.getMessage(), e);
            throw new StorageException("Failed to fetch object metadata from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking existence for bucket={}, key={}: {}", bucket, key, e.getMessage(), e);
            throw new StorageException("Failed to check object existence in MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            log.info("Deleting object from storage: bucket={}, key={}", bucket, key);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete object bucket={}, key={}: {}", bucket, key, e.getMessage(), e);
            throw new StorageException("Failed to delete object from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket '{}' already exists in MinIO", bucket);
        } catch (NoSuchBucketException e) {
            log.info("Bucket '{}' does not exist in MinIO. Creating...", bucket);
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Bucket '{}' created successfully", bucket);
            } catch (Exception ex) {
                log.error("Failed to create bucket '{}': {}", bucket, ex.getMessage(), ex);
                throw new StorageException("Failed to create MinIO bucket: " + ex.getMessage(), ex);
            }
        } catch (Exception e) {
            log.warn("Could not verify bucket '{}' via headBucket: {}. Attempting create...", bucket, e.getMessage());
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Bucket '{}' created successfully", bucket);
            } catch (Exception ex) {
                log.info("Bucket '{}' creation skipped or already existing: {}", bucket, ex.getMessage());
            }
        }
    }

    @Override
    public void configureCors(String bucket) {
        try {
            log.info("Configuring CORS policy for bucket '{}'", bucket);
            CORSRule corsRule = CORSRule.builder()
                    .allowedMethods("PUT", "GET", "HEAD", "POST", "DELETE")
                    .allowedOrigins("*")
                    .allowedHeaders("*")
                    .exposeHeaders("ETag", "Content-Length", "Content-Range")
                    .maxAgeSeconds(3600)
                    .build();

            CORSConfiguration corsConfiguration = CORSConfiguration.builder()
                    .corsRules(corsRule)
                    .build();

            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucket)
                    .corsConfiguration(corsConfiguration)
                    .build());
            log.info("CORS configuration applied to bucket '{}'", bucket);
        } catch (Exception e) {
            log.warn("Could not configure CORS for bucket '{}': {}", bucket, e.getMessage());
        }
    }
}
