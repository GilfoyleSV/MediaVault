package ru.kuzmin.mediavault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * Внутренний URL для обращений бэкенда к MinIO (например, в Docker: http://minio_service:9000).
     */
    private String endpoint = "http://localhost:9000";

    /**
     * Публичный URL для клиентов (браузеров), используемый при генерации Presigned URL.
     */
    private String publicEndpoint = "http://localhost:9000";

    /**
     * Ключ доступа MinIO.
     */
    private String accessKey = "minioadmin";

    /**
     * Секретный ключ MinIO.
     */
    private String secretKey = "minioadmin";

    /**
     * Основной бакет для медиа-файлов.
     */
    private String bucket = "media-bucket";

    /**
     * AWS регион (для MinIO обычно us-east-1).
     */
    private String region = "us-east-1";

    /**
     * Время жизни ссылки на загрузку (Presigned PUT URL).
     */
    private Duration uploadExpiration = Duration.ofMinutes(15);

    /**
     * Время жизни ссылки на скачивание/стриминг (Presigned GET URL).
     */
    private Duration downloadExpiration = Duration.ofMinutes(60);
}
