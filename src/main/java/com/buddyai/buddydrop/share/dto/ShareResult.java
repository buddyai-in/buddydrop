package com.buddyai.buddydrop.share.dto;

/**
 * Outcome of creating or updating a share. {@link #url} is populated only when a token was freshly
 * minted (create or regenerate); a settings-only update returns null there, since the existing token
 * cannot be reconstructed from its stored hash.
 */
public record ShareResult(String url, ShareInfo info) {
}
