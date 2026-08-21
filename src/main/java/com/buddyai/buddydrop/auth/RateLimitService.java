package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A lightweight in-memory throttle for magic-link requests, keyed by a caller identity (email or IP).
 *
 * <p>One token bucket per key blunts email enumeration and mail-bombing. Kept in-process on purpose:
 * a single node needs no shared store, and the design can move to a distributed Bucket4j backend later
 * without changing callers. Bounded refill means idle keys naturally age out of relevance.
 */
@Service
public class RateLimitService {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final Duration window;

    public RateLimitService(AppProperties properties) {
        this.capacity = properties.getAuth().getMaxRequestsPerWindow();
        this.window = properties.getAuth().getRequestWindow();
    }

    /** @return true if the request is allowed (a token was consumed), false if the key is throttled. */
    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, window)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
