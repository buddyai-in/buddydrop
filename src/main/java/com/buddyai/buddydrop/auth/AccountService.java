package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Owns the account lifecycle shared by every sign-in method. Identity is the normalized email
 * address: a magic link and a social provider that vouches for the same verified email land on the
 * same {@link AppUser}. Accounts are created lazily with the default storage quota.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AppUserRepository users;
    private final AppProperties properties;

    /** The account for {@code rawEmail}, created on first sight. */
    @Transactional
    public AppUser findOrCreate(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        return users.findByEmail(email)
                .orElseGet(() -> users.save(AppUser.builder()
                        .email(email)
                        .quotaBytes(properties.getStorage().getQuotaBytes())
                        .build()));
    }

    /** Find-or-create and stamp the login time — for methods that authenticate in one step. */
    @Transactional
    public AppUser signIn(String rawEmail) {
        AppUser user = findOrCreate(rawEmail);
        user.setLastLoginAt(Instant.now());
        return user;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
