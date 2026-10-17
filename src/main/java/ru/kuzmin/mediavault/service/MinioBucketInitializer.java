package ru.kuzmin.mediavault.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.kuzmin.mediavault.config.MinioProperties;

@Slf4j
@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private final StorageService storageService;
    private final MinioProperties minioProperties;

    public MinioBucketInitializer(StorageService storageService, MinioProperties minioProperties) {
        this.storageService = storageService;
        this.minioProperties = minioProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = minioProperties.getBucket();
        log.info("Initializing MinIO bucket and CORS rules for: {}", bucket);
        try {
            storageService.ensureBucketExists(bucket);
            storageService.configureCors(bucket);
            log.info("MinIO bucket '{}' initialization completed", bucket);
        } catch (Exception e) {
            log.warn("MinIO bucket initialization failed (MinIO might be starting or unavailable): {}", e.getMessage());
        }
    }
}
