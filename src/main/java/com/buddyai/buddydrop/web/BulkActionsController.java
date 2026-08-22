package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.file.FileService;
import com.buddyai.buddydrop.file.dto.BulkDeleteRequest;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.share.ShareBundleService;
import com.buddyai.buddydrop.share.dto.BulkShareRequest;
import com.buddyai.buddydrop.share.dto.BundleShareResult;
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
 * Multi-select bulk actions from the dashboard: delete several files, or share several as a single
 * bundle link (one URL that downloads them all as a ZIP). Spans the file and share modules, so it
 * lives in the web layer alongside the dashboard it serves. Ownership is enforced in each service.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class BulkActionsController {

    private final FileService fileService;
    private final ShareBundleService bundleService;

    @PostMapping("/bulk-delete")
    public Map<String, Integer> bulkDelete(@AuthenticationPrincipal AppUserPrincipal user,
                                           @RequestBody BulkDeleteRequest request) {
        return Map.of("deleted", fileService.deleteMany(user.id(), request.ids()));
    }

    /** Share the selected files as one bundle; returns the single link. */
    @PostMapping("/bulk-share")
    public BundleShareResult bulkShare(@AuthenticationPrincipal AppUserPrincipal user,
                                       @RequestBody BulkShareRequest request) {
        ShareSettings settings = new ShareSettings(
                request.expiresInDays(), request.maxDownloads(), request.password(), false);
        List<java.util.UUID> ids = request.ids();
        String url = bundleService.create(user.id(), ids, settings);
        return new BundleShareResult(url, ids == null ? 0 : ids.size());
    }
}
