package com.buddyai.buddydrop.usage;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.domain.UsageEvent;
import com.buddyai.buddydrop.domain.UsageKind;
import com.buddyai.buddydrop.exception.RateLimitExceededException;
import com.buddyai.buddydrop.repository.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enforces per-user rate limits on uploads and downloads across rolling hour/day/month windows,
 * counting persisted {@link UsageEvent}s. Persistence (not an in-memory bucket) is what makes the
 * monthly window meaningful — counts survive restarts and are shared across nodes.
 *
 * <p>The single entry point {@link #recordAction} checks every window <em>then</em> records the
 * action, all in one transaction: if any window is exhausted it throws {@link RateLimitExceededException}
 * and nothing is recorded (and the caller's surrounding work rolls back with it).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsageLimitService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration MONTH = Duration.ofDays(30);

    private final UsageEventRepository events;
    private final AppProperties properties;

    /**
     * Check all windows for {@code userId}/{@code kind}; if all pass, record the action.
     *
     * @throws RateLimitExceededException if any hour/day/month limit is already reached
     */
    @Transactional
    public void recordAction(UUID userId, UsageKind kind) {
        AppProperties.Quota quota = quotaFor(kind);
        Instant now = Instant.now();

        enforceWindow(userId, kind, HOUR, quota.getHourly(), "hour", now);
        enforceWindow(userId, kind, DAY, quota.getDaily(), "day", now);
        enforceWindow(userId, kind, MONTH, quota.getMonthly(), "month", now);

        events.save(UsageEvent.builder().userId(userId).kind(kind).createdAt(now).build());
    }

    private void enforceWindow(UUID userId, UsageKind kind, Duration window, int limit,
                               String label, Instant now) {
        if (limit <= 0) {
            return; // 0 or negative disables this window
        }
        long used = events.countByUserIdAndKindAndCreatedAtAfter(userId, kind, now.minus(window));
        if (used >= limit) {
            throw new RateLimitExceededException(
                    "%s limit reached (%d per %s). Please try again later."
                            .formatted(actionLabel(kind), limit, label));
        }
    }

    /** Current usage against each window for both actions — drives the profile page's usage table. */
    @Transactional(readOnly = true)
    public List<UsageWindowStat> snapshot(UUID userId) {
        Instant now = Instant.now();
        AppProperties.Quota up = properties.getLimits().getUpload();
        AppProperties.Quota down = properties.getLimits().getDownload();
        return List.of(
                stat(userId, UsageKind.UPLOAD, "Uploads", "hour", HOUR, up.getHourly(), now),
                stat(userId, UsageKind.UPLOAD, "Uploads", "day", DAY, up.getDaily(), now),
                stat(userId, UsageKind.UPLOAD, "Uploads", "month", MONTH, up.getMonthly(), now),
                stat(userId, UsageKind.DOWNLOAD, "Downloads", "hour", HOUR, down.getHourly(), now),
                stat(userId, UsageKind.DOWNLOAD, "Downloads", "day", DAY, down.getDaily(), now),
                stat(userId, UsageKind.DOWNLOAD, "Downloads", "month", MONTH, down.getMonthly(), now));
    }

    private UsageWindowStat stat(UUID userId, UsageKind kind, String action, String window,
                                 Duration duration, int limit, Instant now) {
        long used = events.countByUserIdAndKindAndCreatedAtAfter(userId, kind, now.minus(duration));
        return new UsageWindowStat(action, window, used, limit);
    }

    private AppProperties.Quota quotaFor(UsageKind kind) {
        return kind == UsageKind.UPLOAD
                ? properties.getLimits().getUpload()
                : properties.getLimits().getDownload();
    }

    private static String actionLabel(UsageKind kind) {
        return kind == UsageKind.UPLOAD ? "Upload" : "Download";
    }
}
