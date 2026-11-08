package ru.kuzmin.mediavault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.kuzmin.mediavault.model.MediaStatus;
import ru.kuzmin.mediavault.model.MediaType;
import ru.kuzmin.mediavault.model.ThumbnailStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
@Getter
@Setter
public class MediaAsset {

    @Id
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
    private Instant cleanupAt;

    @Enumerated(EnumType.STRING)
    private ThumbnailStatus thumbnailStatus;

    public String thumbnailKey() {
        return "thumbnails/" + id + ".jpg";
    }
}
