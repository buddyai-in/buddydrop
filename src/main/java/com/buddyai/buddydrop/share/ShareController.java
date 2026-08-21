package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.share.dto.ShareInfo;
import com.buddyai.buddydrop.share.dto.ShareResult;
import com.buddyai.buddydrop.share.dto.ShareSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Owner-side sharing API, nested under a file. Creating/updating a share returns the link URL only
 * when a token was freshly minted (see {@link ShareService#share}).
 */
@RestController
@RequestMapping("/api/files/{id}/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @GetMapping
    public ShareInfo get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return shareService.getShareInfo(user.id(), id);
    }

    @PostMapping
    public ShareResult share(@AuthenticationPrincipal AppUserPrincipal user,
                             @PathVariable UUID id,
                             @RequestBody ShareSettings settings) {
        return shareService.share(user.id(), id, settings);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        shareService.revoke(user.id(), id);
    }
}
