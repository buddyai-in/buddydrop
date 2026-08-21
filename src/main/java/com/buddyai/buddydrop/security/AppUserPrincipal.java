package com.buddyai.buddydrop.security;

import java.util.UUID;

/**
 * The authenticated principal stored in the security context and session. Deliberately minimal —
 * just the identity a request needs to authorize file/share operations. Resolved into controller
 * methods via {@code @AuthenticationPrincipal}.
 */
public record AppUserPrincipal(UUID id, String email) {
}
