package com.buddyai.buddydrop.usage;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.UsageKind;
import com.buddyai.buddydrop.exception.RateLimitExceededException;
import com.buddyai.buddydrop.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        // Small, deterministic limits. 0 disables a window.
        "buddydrop.limits.upload.hourly=2",
        "buddydrop.limits.upload.daily=0",
        "buddydrop.limits.upload.monthly=0",
        "buddydrop.limits.download.hourly=0",
        "buddydrop.limits.download.daily=3",
        "buddydrop.limits.download.monthly=0",
})
class UsageLimitServiceTest {

    @Autowired UsageLimitService usageLimits;
    @Autowired AppUserRepository users;

    @Test
    void uploadsAreCappedByTheHourlyWindow() {
        UUID user = newUser();
        usageLimits.recordAction(user, UsageKind.UPLOAD);
        usageLimits.recordAction(user, UsageKind.UPLOAD);

        assertThatThrownBy(() -> usageLimits.recordAction(user, UsageKind.UPLOAD))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Upload")
                .hasMessageContaining("hour");
    }

    @Test
    void downloadsAreCappedByTheDailyWindow() {
        UUID user = newUser();
        usageLimits.recordAction(user, UsageKind.DOWNLOAD);
        usageLimits.recordAction(user, UsageKind.DOWNLOAD);
        usageLimits.recordAction(user, UsageKind.DOWNLOAD);

        assertThatThrownBy(() -> usageLimits.recordAction(user, UsageKind.DOWNLOAD))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Download")
                .hasMessageContaining("day");
    }

    @Test
    void limitsAreScopedPerUser() {
        UUID a = newUser();
        UUID b = newUser();
        usageLimits.recordAction(a, UsageKind.UPLOAD);
        usageLimits.recordAction(a, UsageKind.UPLOAD); // a now at its limit

        // b is unaffected.
        assertThatCode(() -> usageLimits.recordAction(b, UsageKind.UPLOAD)).doesNotThrowAnyException();
    }

    @Test
    void uploadAndDownloadQuotasAreIndependent() {
        UUID user = newUser();
        usageLimits.recordAction(user, UsageKind.UPLOAD);
        usageLimits.recordAction(user, UsageKind.UPLOAD); // uploads maxed (hourly=2)

        // Downloads use a separate counter and quota.
        assertThatCode(() -> usageLimits.recordAction(user, UsageKind.DOWNLOAD)).doesNotThrowAnyException();
    }

    @Test
    void snapshotReportsUsagePerWindow() {
        UUID user = newUser();
        assertThat(usageLimits.snapshot(user)).hasSize(6); // uploads + downloads × 3 windows

        usageLimits.recordAction(user, UsageKind.UPLOAD);
        usageLimits.recordAction(user, UsageKind.UPLOAD);

        UsageWindowStat uploadHour = usageLimits.snapshot(user).stream()
                .filter(s -> s.action().equals("Uploads") && s.window().equals("hour"))
                .findFirst().orElseThrow();
        assertThat(uploadHour.used()).isEqualTo(2);
        assertThat(uploadHour.limit()).isEqualTo(2);
        assertThat(uploadHour.percent()).isEqualTo(100);
    }

    private UUID newUser() {
        return users.save(AppUser.builder()
                .email("u-" + UUID.randomUUID() + "@ex.com").quotaBytes(1_000_000).build()).getId();
    }
}
