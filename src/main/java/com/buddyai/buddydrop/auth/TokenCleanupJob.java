package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.repository.MagicTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Periodically purges consumed and long-expired magic tokens so the table doesn't grow unbounded. */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final MagicTokenRepository tokens;

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void purge() {
        int removed = tokens.deleteConsumedOrExpiredBefore(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} spent magic tokens", removed);
        }
    }
}
