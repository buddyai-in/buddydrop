package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.exception.NotFoundException;
import com.buddyai.buddydrop.file.FileService;
import com.buddyai.buddydrop.file.StorageUsage;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.usage.UsageLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The account / profile page: identity, current plan, storage usage, and live rate-limit usage.
 * Read-only today (there is no editable profile data yet); it's the natural home for the plan and
 * upgrade entry points described in {@code docs/PLANS.md}.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final AppUserRepository users;
    private final FileService fileService;
    private final UsageLimitService usageLimits;

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("Account not found"));
        StorageUsage usage = fileService.usage(user.getId());

        model.addAttribute("email", user.getEmail());
        model.addAttribute("plan", "Free");
        model.addAttribute("memberSince", DisplayLabels.fullDate(user.getCreatedAt()));
        model.addAttribute("lastLogin",
                user.getLastLoginAt() != null ? DisplayLabels.fullDate(user.getLastLoginAt()) : "—");

        model.addAttribute("usedHuman", usage.usedHuman());
        model.addAttribute("quotaHuman", usage.quotaHuman());
        model.addAttribute("percent", usage.percentUsed());

        model.addAttribute("usage", usageLimits.snapshot(user.getId()));
        return "profile";
    }
}
