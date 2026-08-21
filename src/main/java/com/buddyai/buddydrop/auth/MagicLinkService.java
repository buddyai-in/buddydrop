package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.MagicToken;
import com.buddyai.buddydrop.mail.MailService;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.MagicTokenRepository;
import com.buddyai.buddydrop.util.Tokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Optional;

/**
 * Issues and consumes passwordless sign-in tokens.
 *
 * <p>Flow: {@link #requestLink} mints a raw token, persists only its hash with a short TTL, and emails
 * a confirmation link. {@link #consume} validates and single-uses that token on an explicit POST — a
 * GET preview never reaches here, so email scanners cannot burn a link. Users are created lazily on
 * first request. To avoid leaking which addresses are registered, {@link #requestLink} is silent about
 * whether the email existed before.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MagicLinkService {

    private final AppUserRepository users;
    private final MagicTokenRepository tokens;
    private final MailService mailService;
    private final RateLimitService rateLimiter;
    private final AppProperties properties;

    /**
     * Mint and email a sign-in link for the given email.
     *
     * @throws TooManyRequestsException if the address is currently throttled
     */
    @Transactional
    public void requestLink(String rawEmail) {
        String email = normalize(rawEmail);
        if (!rateLimiter.tryAcquire("magic:" + email)) {
            throw new TooManyRequestsException();
        }

        AppUser user = users.findByEmail(email)
                .orElseGet(() -> users.save(AppUser.builder()
                        .email(email)
                        .quotaBytes(properties.getStorage().getQuotaBytes())
                        .build()));

        String raw = Tokens.generate();
        tokens.save(MagicToken.builder()
                .userId(user.getId())
                .tokenHash(Tokens.hash(raw))
                .expiresAt(Instant.now().plus(properties.getAuth().getTokenTtl()))
                .build());

        String link = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path("/auth/verify")
                .queryParam("token", raw)
                .build()
                .toUriString();

        mailService.sendMagicLink(email, link);
        log.info("Issued magic-link for {}", email);
    }

    /**
     * Validate and single-use a raw token.
     *
     * @return the signed-in user
     * @throws InvalidTokenException if the token is unknown, expired, or already used
     */
    @Transactional
    public AppUser consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException();
        }
        MagicToken token = tokens.findByTokenHash(Tokens.hash(rawToken))
                .orElseThrow(InvalidTokenException::new);

        Instant now = Instant.now();
        if (!token.isUsable(now)) {
            throw new InvalidTokenException();
        }
        token.setConsumedAt(now);

        AppUser user = users.findById(token.getUserId())
                .orElseThrow(InvalidTokenException::new);
        user.setLastLoginAt(now);
        log.info("Consumed magic-link for {}", user.getEmail());
        return user;
    }

    /**
     * Read-only peek used by the GET confirm page to show which address will be signed in — without
     * consuming the token. Returns empty rather than throwing so the page can render a friendly
     * "link expired" state.
     */
    @Transactional(readOnly = true)
    public Optional<String> peekEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(Tokens.hash(rawToken))
                .filter(t -> t.isUsable(Instant.now()))
                .flatMap(t -> users.findById(t.getUserId()))
                .map(AppUser::getEmail);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
