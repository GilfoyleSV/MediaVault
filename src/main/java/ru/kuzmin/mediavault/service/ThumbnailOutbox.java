package ru.kuzmin.mediavault.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.kuzmin.mediavault.entity.MediaAsset;
import ru.kuzmin.mediavault.model.ThumbnailStatus;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Profile("media-processing")
public class ThumbnailOutbox {
    public static final String TOPIC = "media.ready.v1";
    static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(ThumbnailOutbox.class);
    private static final int BATCH_SIZE = 16;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;

    public ThumbnailOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka,
                           TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(MediaAsset asset) {
        String mimeType = asset.getMimeType();
        if (asset.getSizeBytes() <= MAX_INPUT_BYTES
                && ("image/jpeg".equalsIgnoreCase(mimeType) || "image/png".equalsIgnoreCase(mimeType))) {
            asset.setThumbnailStatus(ThumbnailStatus.PENDING);
            jdbc.sql("INSERT INTO media_outbox (media_id) VALUES (?)").param(asset.getId()).update();
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void publishBatch() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            try {
                if (!Boolean.TRUE.equals(transactions.execute(status -> publishNext()))) {
                    return;
                }
            } catch (RuntimeException failure) {
                log.warn("Could not publish media outbox event. The batch will retry.", failure);
                return;
            }
        }
    }

    private boolean publishNext() {
        var pending = jdbc.sql("""
                SELECT media_id FROM media_outbox
                ORDER BY created_at, media_id
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """).query(UUID.class).optional();
        if (pending.isEmpty()) {
            return false;
        }

        UUID mediaId = pending.get();
        try {
            String id = mediaId.toString();
            kafka.send(TOPIC, id, id).get(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Outbox publisher interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka did not acknowledge media event " + mediaId, e);
        }

        jdbc.sql("DELETE FROM media_outbox WHERE media_id = ?").param(mediaId).update();
        return true;
    }
}
