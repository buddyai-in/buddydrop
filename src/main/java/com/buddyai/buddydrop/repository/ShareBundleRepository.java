package com.buddyai.buddydrop.repository;

import com.buddyai.buddydrop.domain.ShareBundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShareBundleRepository extends JpaRepository<ShareBundle, UUID> {
    Optional<ShareBundle> findByTokenHash(String tokenHash);
}
