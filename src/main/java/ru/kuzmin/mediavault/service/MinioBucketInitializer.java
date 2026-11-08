package ru.kuzmin.mediavault.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.kuzmin.mediavault.config.MinioProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

@Component("storage")
public class MinioBucketInitializer implements ApplicationRunner, HealthIndicator {
    private final StorageService storage;
    private final S3Client s3;
    private final MinioProperties properties;

    public MinioBucketInitializer(StorageService storage, S3Client s3, MinioProperties properties) {
        this.storage = storage;
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        storage.ensureBucketExists(properties.getBucket());
    }

    @Override
    public Health health() {
        try {
            s3.headBucket(head -> head.bucket(properties.getBucket()));
            return Health.up().build();
        } catch (SdkException e) {
            return Health.down().build();
        }
    }
}
