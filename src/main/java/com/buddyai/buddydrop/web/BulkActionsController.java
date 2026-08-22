package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.file.FileService;
import com.buddyai.buddydrop.file.dto.BulkDeleteRequest;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.share.ShareService;
import com.buddyai.buddydrop.share.dto.BulkShareLink;
import com.buddyai.buddydrop.share.dto.BulkShareRequest;
import com.buddyai.buddydrop.share.dto.ShareSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Multi-select bulk actions from the dashboard: delete or share several selected files at once.
 * Spans the file and share services, so it lives in the web layer alongside the dashboard it serves.
 * Ownership is enforced inside each service; foreign ids are ignored.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class BulkActionsController {

    private final FileService fileService;
    private final ShareService shareService;

    @PostMapping("/bulk-delete")
    public Map<String, Integer> bulkDelete(@AuthenticationPrincipal AppUserPrincipal user,
                                           @RequestBody BulkDeleteRequest request) {
        return Map.of("deleted", fileService.deleteMany(user.id(), request.ids()));
    }

    @PostMapping("/bulk-share")
    public List<BulkShareLink> bulkShare(@AuthenticationPrincipal AppUserPrincipal user,
                                         @RequestBody BulkShareRequest request) {
        ShareSettings settings = new ShareSettings(
                request.expiresInDays(), request.maxDownloads(), request.password(), true);
        return shareService.shareMany(user.id(), request.ids(), settings);
    }
}
