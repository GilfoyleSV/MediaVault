package ru.kuzmin.mediavault.service;

import ru.kuzmin.mediavault.dto.PresignedUrlDto;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Duration;

public interface StorageService {

    /**
     * Генерирует Presigned PUT URL для прямой загрузки файла клиентом в MinIO.
     */
    PresignedUrlDto generatePresignedUploadUrl(String bucket, String key, String mimeType, Duration duration);

    /**
     * Генерирует Presigned GET URL для скачивания или потокового воспроизведения медиа.
     */
    PresignedUrlDto generatePresignedDownloadUrl(String bucket, String key, Duration duration);

    /**
     * Получает метаданные объекта из MinIO (HeadObject).
     */
    HeadObjectResponse headObject(String bucket, String key);

    /**
     * Проверяет наличие объекта в MinIO.
     */
    boolean objectExists(String bucket, String key);

    /**
     * Удаляет объект из бакета MinIO.
     */
    void deleteObject(String bucket, String key);

    /**
     * Проверяет существование бакета и создает его, если он отсутствует.
     */
    void ensureBucketExists(String bucket);

    /**
     * Применяет CORS-политику для бакета (разрешение PUT, GET, HEAD, * headers).
     */
    void configureCors(String bucket);
}
