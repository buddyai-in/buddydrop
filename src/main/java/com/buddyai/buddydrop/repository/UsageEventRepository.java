package com.buddyai.buddydrop.repository;

import com.buddyai.buddydrop.domain.UsageEvent;
import com.buddyai.buddydrop.domain.UsageKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    /** Number of events of a kind for a user since {@code after} — the count for one rolling window. */
    long countByUserIdAndKindAndCreatedAtAfter(UUID userId, UsageKind kind, Instant after);

    /** Housekeeping: drop events older than the widest limit window. */
    @Modifying
    @Query("delete from UsageEvent e where e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
