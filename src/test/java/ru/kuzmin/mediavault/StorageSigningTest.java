package ru.kuzmin.mediavault;

import org.junit.jupiter.api.Test;
import ru.kuzmin.mediavault.config.MinioConfig;
import ru.kuzmin.mediavault.config.MinioProperties;
import ru.kuzmin.mediavault.service.impl.MinioStorageService;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StorageSigningTest {
    @Test
    void signsAnUploadWithAWritePreconditionAndPublicEndpoint() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://minio_service:9000");
        properties.setPublicEndpoint("http://localhost:9000");
        try (var signer = new MinioConfig(properties).s3Presigner()) {
            MinioStorageService storage = new MinioStorageService(null, signer);
            var upload = storage.generatePresignedUploadUrl("media-bucket", "audio/example", "audio/mpeg",
                    42L, null, Duration.ofMinutes(15));

            assertThat(URI.create(upload.url()).getHost()).isEqualTo("localhost");
            assertThat(upload.headers())
                    .containsEntry("if-none-match", "*")
                    .containsEntry("content-type", "audio/mpeg")
                    .containsEntry("content-length", "42")
                    .doesNotContainKey("host");
        }
    }
}
