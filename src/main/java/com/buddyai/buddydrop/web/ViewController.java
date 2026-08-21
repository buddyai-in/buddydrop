package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.domain.ShareLink;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.file.FileService;
import com.buddyai.buddydrop.file.StorageUsage;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.share.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the authenticated dashboard. Assembles pre-formatted {@link DashboardFile} rows by joining
 * each file with its (optional) share link in one batch lookup, so the template just prints strings.
 */
@Controller
@RequiredArgsConstructor
public class ViewController {

    private final FileService fileService;
    private final ShareService shareService;

    @GetMapping("/")
    public String home() {
        return "redirect:/files";
    }

    @GetMapping("/files")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal user, Model model) {
        List<StoredFile> files = fileService.listFiles(user.id());
        Map<UUID, ShareLink> shares = shareService.sharesFor(files.stream().map(StoredFile::getId).toList());

        List<DashboardFile> rows = files.stream().map(f -> {
            ShareLink link = shares.get(f.getId());
            return DashboardFile.builder()
                    .id(f.getId().toString())
                    .name(f.getOriginalName())
                    .ext(DisplayLabels.ext(f.getOriginalName()))
                    .sizeHuman(StorageUsage.human(f.getSizeBytes()))
                    .dateLabel(DisplayLabels.shortDate(f.getCreatedAt()))
                    .shared(link != null)
                    .shareLabel(link != null ? DisplayLabels.shareLabel(link) : null)
                    .build();
        }).toList();

        StorageUsage usage = fileService.usage(user.id());
        model.addAttribute("email", user.email());
        model.addAttribute("files", rows);
        model.addAttribute("usedHuman", usage.usedHuman());
        model.addAttribute("quotaHuman", usage.quotaHuman());
        model.addAttribute("percent", usage.percentUsed());
        return "dashboard";
    }
}
