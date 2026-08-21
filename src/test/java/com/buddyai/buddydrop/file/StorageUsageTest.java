package com.buddyai.buddydrop.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageUsageTest {

    @Test
    void humanReadableSizes() {
        assertThat(StorageUsage.human(0)).isEqualTo("0 B");
        assertThat(StorageUsage.human(512)).isEqualTo("512 B");
        assertThat(StorageUsage.human(1024)).isEqualTo("1.0 KB");
        assertThat(StorageUsage.human(2_517_000)).isEqualTo("2.4 MB");
        assertThat(StorageUsage.human(10L * 1024 * 1024 * 1024)).isEqualTo("10 GB");
    }

    @Test
    void percentUsedIsClampedAndSafe() {
        assertThat(new StorageUsage(0, 100).percentUsed()).isZero();
        assertThat(new StorageUsage(50, 100).percentUsed()).isEqualTo(50);
        assertThat(new StorageUsage(200, 100).percentUsed()).isEqualTo(100);
        assertThat(new StorageUsage(10, 0).percentUsed()).isZero(); // no divide-by-zero
    }
}
