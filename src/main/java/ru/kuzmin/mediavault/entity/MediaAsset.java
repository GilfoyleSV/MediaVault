package ru.kuzmin.mediavault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.model.MediaType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
@Getter
@Setter
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID ownerId;

    private String storageBucket;

    @Column(unique = true)
    private String storageKey;

    private String originalFilename;

    private String mimeType;

    private Long sizeBytes;

    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MediaStatus mediaStatus;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public MediaAsset() {
    }

    public MediaAsset(UUID id, UUID ownerId, String storageBucket, String storageKey,
                      String originalFilename, String mimeType, Long sizeBytes,
                      String checksumSha256, MediaType mediaType, MediaStatus mediaStatus,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.storageBucket = storageBucket;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.mediaType = mediaType;
        this.mediaStatus = mediaStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
