package ru.kuzmin.mediavault.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.kuzmin.mediavault.entity.MediaAsset;
import ru.kuzmin.mediavault.model.MediaStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByIdAndOwnerIdAndMediaStatusNot(UUID id, UUID ownerId, MediaStatus excluded);

    Slice<MediaAsset> findAllByOwnerIdAndMediaStatusNot(UUID ownerId, MediaStatus excluded, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from MediaAsset asset where asset.id = :id and asset.ownerId = :ownerId")
    Optional<MediaAsset> lockOwned(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from MediaAsset asset where asset.id = :id")
    Optional<MediaAsset> lockById(UUID id);

    @Query(value = """
            SELECT * FROM media_asset
            WHERE cleanup_at <= :now AND status <> 'READY'
            ORDER BY cleanup_at, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<MediaAsset> lockNextCleanup(Instant now);
}
