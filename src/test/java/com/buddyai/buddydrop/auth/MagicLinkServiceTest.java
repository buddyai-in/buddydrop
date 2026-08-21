package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.MagicToken;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.MagicTokenRepository;
import com.buddyai.buddydrop.util.Tokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MagicLinkServiceTest {

    @Autowired MagicLinkService magicLinkService;
    @Autowired AppUserRepository users;
    @Autowired MagicTokenRepository tokens;

    @Test
    void requestCreatesUserAndToken() {
        magicLinkService.requestLink("Alice@Example.com");

        AppUser user = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(user.getQuotaBytes()).isPositive();
        assertThat(tokens.findAll()).hasSize(1);
        // Only the hash is stored — never the raw token.
        assertThat(tokens.findAll().get(0).getTokenHash()).hasSize(64);
    }

    @Test
    void tokenIsSingleUse() {
        String raw = seedToken("bob@example.com", Instant.now().plus(15, ChronoUnit.MINUTES));

        AppUser signedIn = magicLinkService.consume(raw);
        assertThat(signedIn.getEmail()).isEqualTo("bob@example.com");
        assertThat(signedIn.getLastLoginAt()).isNotNull();

        // Second use is rejected.
        assertThatThrownBy(() -> magicLinkService.consume(raw))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String raw = seedToken("carol@example.com", Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> magicLinkService.consume(raw))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void peekDoesNotConsume() {
        String raw = seedToken("dave@example.com", Instant.now().plus(15, ChronoUnit.MINUTES));

        assertThat(magicLinkService.peekEmail(raw)).contains("dave@example.com");
        // Peeking twice still works — the token is untouched.
        assertThat(magicLinkService.peekEmail(raw)).contains("dave@example.com");
        assertThat(magicLinkService.consume(raw).getEmail()).isEqualTo("dave@example.com");
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> magicLinkService.consume("not-a-real-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    private String seedToken(String email, Instant expiresAt) {
        AppUser user = users.save(AppUser.builder().email(email).quotaBytes(1_000).build());
        String raw = Tokens.generate();
        tokens.save(MagicToken.builder()
                .userId(user.getId())
                .tokenHash(Tokens.hash(raw))
                .expiresAt(expiresAt)
                .build());
        return raw;
    }
}
