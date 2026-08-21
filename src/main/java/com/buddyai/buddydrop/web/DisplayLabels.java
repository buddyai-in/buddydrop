package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.domain.ShareLink;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Small presentation helpers shared by the view controllers. Pure functions, no state. */
public final class DisplayLabels {

    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private DisplayLabels() {
    }

    /** Uppercase file extension (max 4 chars) for the row icon, e.g. {@code PDF}; {@code FILE} if none. */
    public static String ext(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "FILE";
        }
        String e = name.substring(dot + 1).toUpperCase();
        return e.length() > 4 ? e.substring(0, 4) : e;
    }

    public static String shortDate(Instant instant) {
        return instant == null ? "" : SHORT_DATE.format(instant);
    }

    public static String fullDate(Instant instant) {
        return instant == null ? "" : FULL_DATE.format(instant);
    }

    /** Human status for a share, e.g. {@code shared · 2 days left}, {@code shared · no expiry}. */
    public static String shareLabel(ShareLink link) {
        Instant now = Instant.now();
        if (link.isExhausted()) {
            return "shared · limit reached";
        }
        if (link.isExpired(now)) {
            return "shared · expired";
        }
        if (link.getExpiresAt() == null) {
            return "shared · no expiry";
        }
        long days = ChronoUnit.DAYS.between(now, link.getExpiresAt());
        if (days >= 1) {
            return "shared · " + days + (days == 1 ? " day left" : " days left");
        }
        long hours = ChronoUnit.HOURS.between(now, link.getExpiresAt());
        if (hours >= 1) {
            return "shared · " + hours + (hours == 1 ? " hour left" : " hours left");
        }
        return "shared · expiring soon";
    }

    /** Expiry phrasing for the public page, or {@code null} when the link never expires. */
    public static String expiryLabel(Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(Instant.now(), expiresAt);
        if (days >= 1) {
            return "expires in " + days + (days == 1 ? " day" : " days");
        }
        long hours = ChronoUnit.HOURS.between(Instant.now(), expiresAt);
        return hours >= 1 ? "expires in " + hours + (hours == 1 ? " hour" : " hours") : "expires soon";
    }
}
