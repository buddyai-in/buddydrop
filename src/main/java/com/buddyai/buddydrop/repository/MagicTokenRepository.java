package com.buddyai.buddydrop.repository;

import com.buddyai.buddydrop.domain.MagicToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MagicTokenRepository extends JpaRepository<MagicToken, UUID> {

    Optional<MagicToken> findByTokenHash(String tokenHash);

    /** Housekeeping: drop tokens that are consumed or long past expiry. */
    @Modifying
    @Query("delete from MagicToken t where t.consumedAt is not null or t.expiresAt < :cutoff")
    int deleteConsumedOrExpiredBefore(@Param("cutoff") Instant cutoff);
}
