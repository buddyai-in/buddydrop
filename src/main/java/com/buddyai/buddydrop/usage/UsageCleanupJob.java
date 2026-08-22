package com.buddyai.buddydrop.usage;

import com.buddyai.buddydrop.repository.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purges usage events older than the widest limit window (30 days, plus a small buffer) so the
 * {@code usage_event} table stays bounded. Events past that age can never affect a rolling count.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UsageCleanupJob {

    private static final Duration RETENTION = Duration.ofDays(31);

    private final UsageEventRepository events;

    @Scheduled(fixedDelayString = "PT24H")
    @Transactional
    public void purge() {
        int removed = events.deleteByCreatedAtBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.debug("Purged {} expired usage events", removed);
        }
    }
}
