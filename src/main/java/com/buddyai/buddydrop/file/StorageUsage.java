package com.buddyai.buddydrop.file;

/** A user's storage consumption against their quota, with formatting helpers used by the views. */
public record StorageUsage(long usedBytes, long quotaBytes) {

    public int percentUsed() {
        if (quotaBytes <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(usedBytes * 100.0 / quotaBytes));
    }

    public String usedHuman() {
        return human(usedBytes);
    }

    public String quotaHuman() {
        return human(quotaBytes);
    }

    /** Human-readable byte size, e.g. {@code 2.4 MB}. */
    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return (value >= 10 ? String.format("%.0f", value) : String.format("%.1f", value)) + " " + units[unit];
    }
}
