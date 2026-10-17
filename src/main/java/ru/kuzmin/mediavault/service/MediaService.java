package ru.kuzmin.mediavault.service;

import ru.kuzmin.mediavault.dto.AccessUrlResponse;
import ru.kuzmin.mediavault.dto.CompleteUploadRequest;
import ru.kuzmin.mediavault.dto.MediaResponse;
import ru.kuzmin.mediavault.dto.UploadRequest;
import ru.kuzmin.mediavault.dto.UploadResponse;

import java.util.UUID;

public interface MediaService {

    /**
     * Инициализирует загрузку медиа-файла:
     * - Валидирует MIME-тип (IMAGE, AUDIO, VIDEO)
     * - Создает запись MediaAsset со статусом PENDING_UPLOAD
     * - Генерирует Presigned PUT URL в MinIO
     */
    UploadResponse initiateUpload(UploadRequest request, UUID ownerId);

    /**
     * Завершает процесс загрузки:
     * - Проверяет наличие объекта в MinIO (HeadObject)
     * - Валидирует фактический размер файла
     * - Переводит статус в READY
     */
    MediaResponse completeUpload(UUID mediaId, CompleteUploadRequest request);

    /**
     * Генерирует Presigned GET URL для скачивания или стриминга готового файла.
     */
    AccessUrlResponse getAccessUrl(UUID mediaId);
}
