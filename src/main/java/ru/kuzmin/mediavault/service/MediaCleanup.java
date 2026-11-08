package ru.kuzmin.mediavault.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.kuzmin.mediavault.entity.MediaAsset;
import ru.kuzmin.mediavault.exception.StorageException;
import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.repository.MediaAssetRepository;

import java.time.Duration;
import java.time.Instant;

@Component
public class MediaCleanup {
    private static final Logger log = LoggerFactory.getLogger(MediaCleanup.class);
    private static final int BATCH_SIZE = 25;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final MediaAssetRepository assets;
    private final StorageService storage;
    private final TransactionTemplate transactions;

    public MediaCleanup(MediaAssetRepository assets, StorageService storage, TransactionTemplate transactions) {
        this.assets = assets;
        this.storage = storage;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${media.cleanup-delay-ms}")
    public void runBatch() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            boolean processed = Boolean.TRUE.equals(transactions.execute(status -> assets.lockNextCleanup(Instant.now())
                    .map(this::clean)
                    .orElse(false)));
            if (!processed) {
                break;
            }
        }
    }

    private boolean clean(MediaAsset asset) {
        try {
            storage.deleteObject(asset.getStorageBucket(), asset.getStorageKey());
            if (asset.getThumbnailStatus() != null) {
                storage.deleteObject(asset.getStorageBucket(), asset.thumbnailKey());
            }
            if (asset.getMediaStatus() == MediaStatus.PENDING_UPLOAD) {
                asset.setMediaStatus(MediaStatus.FAILED);
            }
            asset.setCleanupAt(null);
        } catch (StorageException failure) {
            asset.setCleanupAt(Instant.now().plus(RETRY_DELAY));
            log.warn("Storage cleanup failed for media {}, will retry", asset.getId(), failure);
        }
        return true;
    }
}
