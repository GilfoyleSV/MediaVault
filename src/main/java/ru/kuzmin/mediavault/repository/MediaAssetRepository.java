package ru.kuzmin.mediavault.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.kuzmin.mediavault.entity.MediaAsset;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID>{

}
