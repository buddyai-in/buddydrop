package com.buddyai.buddydrop.repository;

import com.buddyai.buddydrop.domain.FileStatus;
import com.buddyai.buddydrop.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, FileStatus status);

    Optional<StoredFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Total bytes counted against a user's quota (READY files only). */
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f " +
            "where f.ownerId = :ownerId and f.status = com.buddyai.buddydrop.domain.FileStatus.READY")
    long sumSizeByOwner(@Param("ownerId") UUID ownerId);
}
