package com.buddyai.buddydrop.web;

import lombok.Builder;
import lombok.Getter;

/**
 * Pre-formatted row for the dashboard file list. All display values (sizes, dates, share status) are
 * resolved on the Java side so the Thymeleaf template stays free of date/records formatting pitfalls
 * and simply prints strings.
 */
@Getter
@Builder
public class DashboardFile {
    private final String id;
    private final String name;
    private final String ext;
    private final String sizeHuman;
    private final String dateLabel;
    private final boolean shared;
    private final String shareLabel;
}
