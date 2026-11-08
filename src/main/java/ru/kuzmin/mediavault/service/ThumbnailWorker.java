package ru.kuzmin.mediavault.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.kuzmin.mediavault.exception.InvalidFileSizeException;
import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.model.ThumbnailStatus;
import ru.kuzmin.mediavault.repository.MediaAssetRepository;

import java.io.IOException;
import java.util.UUID;

@Component
@Profile("media-processing")
public class ThumbnailWorker {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailWorker.class);
    private final MediaAssetRepository assets;
    private final StorageService storage;

    public ThumbnailWorker(MediaAssetRepository assets, StorageService storage) {
        this.assets = assets;
        this.storage = storage;
    }

    @KafkaListener(topics = ThumbnailOutbox.TOPIC, groupId = "mediavault-thumbnails")
    @Transactional
    public void createThumbnail(String value) {
        UUID mediaId = UUID.fromString(value);
        var pending = assets.lockById(mediaId)
                .filter(asset -> asset.getMediaStatus() == MediaStatus.READY
                        && asset.getThumbnailStatus() == ThumbnailStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        var asset = pending.get();
        byte[] jpeg;
        try {
            byte[] source = storage.readObject(asset.getStorageBucket(), asset.getStorageKey(),
                    ThumbnailOutbox.MAX_INPUT_BYTES);
            jpeg = ThumbnailImage.create(source, asset.getMimeType());
        } catch (InvalidFileSizeException | IOException invalidImage) {
            asset.setThumbnailStatus(ThumbnailStatus.FAILED);
            log.info("Could not decode image for media {}: {}", mediaId, invalidImage.getMessage());
            return;
        }

        storage.putThumbnail(asset.getStorageBucket(), asset.thumbnailKey(), jpeg);
        asset.setThumbnailStatus(ThumbnailStatus.READY);
    }
}
