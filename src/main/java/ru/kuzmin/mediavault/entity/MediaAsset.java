package ru.kuzmin.mediavault.entity;

import java.time.Instant;
import java.util.UUID;

import ru.kuzmin.mediavault.model.MediaType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.kuzmin.mediavault.model.MediaStatus;

@Entity
@Table(name="media_asset")
@NoArgsConstructor 
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
    @Column(name="status")
    private MediaStatus mediaStatus;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp 
    private Instant updatedAt;
}
