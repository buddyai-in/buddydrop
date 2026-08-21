package com.buddyai.buddydrop.auth;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The passwordless sign-in surface: request a link, land on the confirm page, and establish a
 * session on an explicit POST.
 *
 * <p>The GET/POST split on {@code /auth/verify} is the load-bearing security detail — the GET only
 * previews the token (via {@link MagicLinkService#peekEmail}), so an email client or scanner that
 * pre-fetches the link cannot consume it; only the human's POST does.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final MagicLinkService magicLinkService;

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/files";
        }
        return "auth/login";
    }

    @PostMapping("/auth/request")
    public String requestLink(@RequestParam("email") @NotBlank @Email String email, Model model) {
        try {
            magicLinkService.requestLink(email);
        } catch (TooManyRequestsException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "auth/login";
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
        establishSession(user, request, response);
        return "redirect:/files";
    }

    private void establishSession(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal principal = new AppUserPrincipal(user.getId(), user.getEmail());
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_USER"));

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(auth);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal;
    }
}
