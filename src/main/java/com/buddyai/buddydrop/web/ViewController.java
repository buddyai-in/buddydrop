package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.domain.ShareLink;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.file.FileService;
import com.buddyai.buddydrop.file.StorageUsage;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.share.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the authenticated dashboard. Assembles pre-formatted {@link DashboardFile} rows for one
 * page of the user's files, joining each with its (optional) share link in a single batch lookup so
 * the template just prints strings.
 */
@Controller
@RequiredArgsConstructor
public class ViewController {

    /** Files shown per dashboard page. */
    static final int PAGE_SIZE = 12;

    private final FileService fileService;
    private final ShareService shareService;

    @GetMapping("/")
    public String home() {
        return "redirect:/files";
    }

    @GetMapping("/files")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal user,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        int pageIndex = Math.max(0, page);
        Page<StoredFile> filePage = fileService.listFiles(user.id(), PageRequest.of(pageIndex, PAGE_SIZE));

        Map<UUID, ShareLink> shares = shareService.sharesFor(
                filePage.getContent().stream().map(StoredFile::getId).toList());

        List<DashboardFile> rows = filePage.getContent().stream().map(f -> {
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
        model.addAttribute("hasFiles", filePage.getTotalElements() > 0);
        model.addAttribute("usedHuman", usage.usedHuman());
        model.addAttribute("quotaHuman", usage.quotaHuman());
        model.addAttribute("percent", usage.percentUsed());

        // Pagination (1-based for display).
        model.addAttribute("currentPage", pageIndex + 1);
        model.addAttribute("totalPages", Math.max(1, filePage.getTotalPages()));
        model.addAttribute("totalFiles", filePage.getTotalElements());
        model.addAttribute("hasPrev", filePage.hasPrevious());
        model.addAttribute("hasNext", filePage.hasNext());
        model.addAttribute("prevPage", pageIndex - 1);
        model.addAttribute("nextPage", pageIndex + 1);
        return "dashboard";
    }
}
