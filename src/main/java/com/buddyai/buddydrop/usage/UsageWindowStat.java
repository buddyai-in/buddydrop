package com.buddyai.buddydrop.usage;

/**
 * One row of the profile's usage table: how many of a given action a user has performed within one
 * window, against the configured limit. A {@code limit} of 0 means that window is unlimited.
 */
public record UsageWindowStat(String action, String window, long used, int limit) {

    public boolean unlimited() {
        return limit <= 0;
    }

    public int percent() {
        if (unlimited()) {
            return 0;
        }
        return (int) Math.min(100, Math.round(used * 100.0 / limit));
    }

    public String limitLabel() {
        return unlimited() ? "∞" : String.valueOf(limit);
    }
}
