package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.exception.InvalidPasswordException;
import com.buddyai.buddydrop.exception.NotFoundException;
import com.buddyai.buddydrop.exception.RateLimitExceededException;
import com.buddyai.buddydrop.exception.ShareUnavailableException;
import com.buddyai.buddydrop.storage.StorageService;
import com.buddyai.buddydrop.web.DisplayLabels;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The public, no-login surface for a multi-file share bundle: a landing page, an optional password
 * unlock (held in the visitor's session so they don't re-enter it), and a ZIP stream of all files.
 *
 * <p>Zipping is the one place bytes flow through the app rather than a presigned URL — the archive is
 * streamed straight to the response output, never buffered whole. Any access problem (unknown /
 * expired / capped / not-yet-unlocked) redirects back to the landing page, which explains why.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class ShareBundleController {

    private final ShareBundleService bundleService;
    private final StorageService storage;

    private static String unlockKey(String token) {
        return "bundle_unlocked_" + token;
    }

    private static boolean isUnlocked(HttpSession session, String token) {
        return Boolean.TRUE.equals(session.getAttribute(unlockKey(token)));
    }

    @GetMapping("/d/{token}")
    public String landing(@PathVariable String token, HttpSession session, Model model) {
        try {
            BundleView view = bundleService.resolvePublic(token);
            if (view.requiresPassword() && !isUnlocked(session, token)) {
                model.addAttribute("token", token);
                return "share/bundle-password";
            }
            populate(model, view);
            return "share/bundle";
        } catch (NotFoundException | ShareUnavailableException e) {
            model.addAttribute("reason", e.getMessage());
            return "share/unavailable";
        }
    }

    @PostMapping("/d/{token}")
    public String unlock(@PathVariable String token,
                         @RequestParam(value = "password", required = false) String password,
                         HttpSession session, Model model) {
        try {
            bundleService.verifyPassword(token, password);
            session.setAttribute(unlockKey(token), Boolean.TRUE);
            return "redirect:/d/" + token;
        } catch (InvalidPasswordException e) {
            model.addAttribute("token", token);
            model.addAttribute("error", e.getMessage());
            return "share/bundle-password";
        } catch (NotFoundException | ShareUnavailableException e) {
            model.addAttribute("reason", e.getMessage());
            return "share/unavailable";
        }
    }

    /** Streams a ZIP of all the bundle's files. Written straight to the response; no whole-archive buffering. */
    @GetMapping("/d/{token}/zip")
    public void downloadZip(@PathVariable String token, HttpSession session,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<StoredFile> filesToZip;
        try {
            BundleView view = bundleService.resolvePublic(token);
            if (view.requiresPassword() && !isUnlocked(session, token)) {
                response.sendRedirect(request.getContextPath() + "/d/" + token);
                return;
            }
            filesToZip = bundleService.prepareDownload(token);
        } catch (NotFoundException | ShareUnavailableException | RateLimitExceededException e) {
            response.sendRedirect(request.getContextPath() + "/d/" + token);
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"buddyai-drop-%d-files.zip\"".formatted(filesToZip.size()));

        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (StoredFile f : filesToZip) {
                zip.putNextEntry(new ZipEntry(uniqueName(f.getOriginalName(), usedNames)));
                try (InputStream in = storage.openObject(f.getS3Key())) {
                    in.transferTo(zip);
                } catch (Exception e) {
                    log.warn("Skipping {} in bundle zip: {}", f.getId(), e.getMessage());
                }
                zip.closeEntry();
            }
        }
    }

    /** Ensure zip entry names are unique: "report.pdf", "report (2).pdf", … */
    private static String uniqueName(String name, Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int i = 2; ; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }

    private void populate(Model model, BundleView view) {
        model.addAttribute("token", view.token());
        model.addAttribute("files", view.files());
        model.addAttribute("count", view.count());
        model.addAttribute("totalHuman", view.totalHuman());
        model.addAttribute("sharedBy", view.sharedBy());
        model.addAttribute("expiryLabel", DisplayLabels.expiryLabel(view.expiresAt()));
    }
}
