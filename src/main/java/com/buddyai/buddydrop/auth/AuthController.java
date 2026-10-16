package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.auth.social.SocialClientRegistrations;
import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The passwordless sign-in surface: request a link, land on the confirm page, and establish a
 * session on an explicit POST. The login page also offers any configured social providers, whose
 * flow is handled by Spring's OAuth2 filters (see {@code SocialLoginConfigurer}).
 *
 * <p>The GET/POST split on {@code /auth/verify} is the load-bearing security detail — the GET only
 * previews the token (via {@link MagicLinkService#peekEmail}), so an email client or scanner that
 * pre-fetches the link cannot consume it; only the human's POST does.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final MagicLinkService magicLinkService;
    private final SessionAuthenticator sessionAuthenticator;
    private final SocialClientRegistrations socialProviders;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/files";
        }
        if (error != null) {
            model.addAttribute("error", socialErrorMessage(error));
        }
        return loginView(model);
    }

    @PostMapping("/auth/request")
    public String requestLink(@RequestParam("email") @NotBlank @Email String email, Model model) {
        try {
            magicLinkService.requestLink(email);
        } catch (TooManyRequestsException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return loginView(model);
        }
        model.addAttribute("email", email.trim().toLowerCase());
        return "auth/sent";
    }

    /** Preview-only: shows the confirm button without consuming the token. */
    @GetMapping("/auth/verify")
    public String verifyPage(@RequestParam(value = "token", required = false) String token, Model model) {
        return magicLinkService.peekEmail(token)
                .map(email -> {
                    model.addAttribute("email", email);
                    model.addAttribute("token", token);
                    return "auth/confirm";
                })
                .orElse("auth/expired");
    }

    /** Consumes the token, opens the session, and lands on the dashboard. */
    @PostMapping("/auth/verify")
    public String confirm(@RequestParam("token") String token,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        AppUser user;
        try {
            user = magicLinkService.consume(token);
        } catch (InvalidTokenException e) {
            return "auth/expired";
        }
        sessionAuthenticator.signIn(user, request, response);
        return "redirect:/files";
    }

    private String loginView(Model model) {
        model.addAttribute("providers", socialProviders.providers());
        return "auth/login";
    }

    private static String socialErrorMessage(String code) {
        return "social-email".equals(code)
                ? "That account didn't share a verified email address. Use your email below instead."
                : "Sign-in didn't complete. Please try again, or use your email below.";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal;
    }
}
