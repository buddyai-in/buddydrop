package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.exception.InvalidPasswordException;
import com.buddyai.buddydrop.exception.NotFoundException;
import com.buddyai.buddydrop.exception.RateLimitExceededException;
import com.buddyai.buddydrop.exception.ShareUnavailableException;
import com.buddyai.buddydrop.web.DisplayLabels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The public, no-login transfer surface. A GET renders the download page (or a password prompt); the
 * POST verifies access, counts the download, and 302-redirects the browser to a presigned S3 URL.
 *
 * <p>All the "link is gone" cases are turned into a friendly rendered page here rather than a raw
 * error, since the audience is an anonymous recipient, not the app's own JS. Display values are
 * pre-formatted into model attributes so the templates only print strings.
 */
@Controller
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareService shareService;

    @GetMapping("/s/{token}")
    public String landing(@PathVariable String token, Model model) {
        try {
            PublicShareView view = shareService.resolvePublic(token);
            populate(model, view);
            return view.requiresPassword() ? "share/password" : "share/download";
        } catch (NotFoundException | ShareUnavailableException e) {
            model.addAttribute("reason", e.getMessage());
            return "share/unavailable";
        }
    }

    @PostMapping("/s/{token}")
    public Object download(@PathVariable String token,
                           @RequestParam(value = "password", required = false) String password,
                           Model model) {
        try {
            return new RedirectView(shareService.resolveDownload(token, password));
        } catch (InvalidPasswordException e) {
            populate(model, shareService.resolvePublic(token));
            model.addAttribute("error", e.getMessage());
            return "share/password";
        } catch (NotFoundException | ShareUnavailableException | RateLimitExceededException e) {
            // Rate limit here means the file owner is over their download limit — surface it kindly.
            model.addAttribute("reason", e.getMessage());
            return "share/unavailable";
        }
    }

    private void populate(Model model, PublicShareView view) {
        model.addAttribute("token", view.token());
        model.addAttribute("filename", view.filename());
        model.addAttribute("ext", DisplayLabels.ext(view.filename()));
        model.addAttribute("sizeHuman", view.sizeHuman());
        model.addAttribute("sharedBy", view.sharedBy());
        model.addAttribute("expiryLabel", DisplayLabels.expiryLabel(view.expiresAt()));
    }
}
