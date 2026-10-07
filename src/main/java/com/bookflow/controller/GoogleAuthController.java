package com.bookflow.controller;

import com.bookflow.entity.Role;
import com.bookflow.entity.User;
import com.bookflow.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handles Google Sign-In via the "Sign in with Google" button / One-Tap flow.
 *
 * Flow:
 *   1. Frontend sends the Google ID token (credential) via a hidden HTML form POST.
 *   2. Backend verifies the token using GoogleIdTokenVerifier against Google's certs.
 *   3. Domain is validated server-side — only @pvppcoe.ac.in is accepted.
 *   4. User is created or fetched from DB, then a Spring Security session is established.
 *   5. User is redirected to /user/dashboard.
 *
 * No new Maven dependency is needed — google-api-client is already in the POM.
 */
@Controller
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private final UserRepository userRepository;

    @Value("${google.oauth.client-id:#{null}}")
    private String googleClientId;

    private static final String ALLOWED_DOMAIN = "pvppcoe.ac.in";

    public GoogleAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Logs safe diagnostics at startup so the configuration state is visible
     * in the console without exposing the actual client ID value.
     */
    @PostConstruct
    public void logGoogleSignInConfig() {
        boolean configured = googleClientId != null
                && !googleClientId.isBlank()
                && !googleClientId.startsWith("placeholder");

        log.info("[GOOGLE-SIGNIN] Web Client configured: {}", configured);
        if (configured) {
            // Print only the last 8 characters so the log is useful without being a leak.
            String id = googleClientId.trim();
            String suffix = id.length() >= 8 ? id.substring(id.length() - 8) : id;
            log.info("[GOOGLE-SIGNIN] Client ID suffix: ****{}", suffix);
        } else {
            log.warn("[GOOGLE-SIGNIN] GOOGLE_OAUTH_CLIENT_ID is NOT set or is a placeholder — "
                   + "Google Sign-In button will not work. "
                   + "Set the environment variable or add it to application-local.properties.");
        }
    }

    /**
     * POST /auth/google
     * Receives the Google credential (ID token) from the frontend and processes login/registration.
     */
    @PostMapping("/auth/google")
    public String handleGoogleSignIn(
            @RequestParam("credential") String idTokenString,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        // ── 1. Validate configuration ──────────────────────────────────────
        if (googleClientId == null || googleClientId.isBlank() || googleClientId.trim().startsWith("placeholder")) {
            redirectAttributes.addFlashAttribute("googleError",
                    "Google Sign-In is not configured yet. Please contact the administrator.");
            return "redirect:/login?error=google";
        }

        // ── 2. Verify the ID token with Google's public certs ──────────────
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId != null ? googleClientId.trim() : ""))
                .build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("googleError",
                    "Unable to verify Google token. Please try again.");
            return "redirect:/login?error=google";
        }

        if (idToken == null) {
            redirectAttributes.addFlashAttribute("googleError",
                    "Invalid Google token. Please try again.");
            return "redirect:/login?error=google";
        }

        Payload payload = idToken.getPayload();

        // ── 3. Check email_verified flag from Google ───────────────────────
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            redirectAttributes.addFlashAttribute("googleError",
                    "Your Google account email is not verified. Please verify it with Google first.");
            return "redirect:/login?error=google";
        }

        // ── 4. Domain enforcement — server-side, never trusts the browser ──
        String email = payload.getEmail().trim().toLowerCase();
        if (!email.endsWith("@" + ALLOWED_DOMAIN)) {
            return "redirect:/login?error=domain";
        }

        // ── 5. Find or create the BookFlow user ────────────────────────────
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // New user — create a BookFlow account
            user = new User();
            user.setEmail(email);
            String name = (String) payload.get("name");
            user.setName(name != null && !name.isBlank() ? name : email.split("@")[0]);
            // OAuth users don't have a password — store a random UUID (not guessable / never used for form login)
            user.setPassword("{noop}" + UUID.randomUUID());
            user.setRole(Role.USER);
            // Google has verified the email ownership — mark as verified immediately
            user.setEmailVerified(true);
            // year/semester are null for OAuth registrations; they can update via profile later
            userRepository.save(user);
        } else {
            // Existing user — ensure they are marked as email-verified
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
                userRepository.save(user);
            }
        }

        // ── 6. Establish a Spring Security session ─────────────────────────
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        // ── 7. Redirect: new users with incomplete profile go to completion page ──
        // Existing users with complete profiles go straight to the dashboard.
        if (user.getYear() == null || user.getSemester() == null) {
            return "redirect:/user/complete-profile";
        }
        return "redirect:/user/dashboard";
    }
}
