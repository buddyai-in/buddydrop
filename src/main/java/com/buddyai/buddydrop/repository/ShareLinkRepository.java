package com.buddyai.buddydrop.repository;

import com.buddyai.buddydrop.domain.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHash(String tokenHash);

    Optional<ShareLink> findByFileId(UUID fileId);

    List<ShareLink> findByFileIdIn(Collection<UUID> fileIds);

    void deleteByFileId(UUID fileId);
}
