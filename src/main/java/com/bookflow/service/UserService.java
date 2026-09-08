package com.bookflow.service;

import com.bookflow.dto.RegisterDto;
import com.bookflow.entity.EmailVerificationToken;
import com.bookflow.entity.Role;
import com.bookflow.entity.User;
import com.bookflow.exception.EmailAlreadyExistsException;
import com.bookflow.exception.InvalidEmailDomainException;
import com.bookflow.repository.EmailVerificationTokenRepository;
import com.bookflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String ALLOWED_DOMAIN = "pvppcoe.ac.in";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    /** Token lifetime in hours — configurable without a redeploy. */
    @Value("${bookflow.verification.token-expiry-hours:24}")
    private int tokenExpiryHours;

    // ----------------------------------------------------------------
    // UserDetailsService — called by Spring Security on every login
    // ----------------------------------------------------------------

    /**
     * Loads the user and builds a Spring Security UserDetails object.
     *
     * ADMIN accounts are never blocked by email verification —
     * admins are created manually and are always considered active.
     *
     * USER accounts must have emailVerified = true before they can
     * reach any authenticated endpoint.  We signal an unverified account
     * by setting enabled=false, which causes Spring Security to throw
     * DisabledException and return the user to the login page with
     * an appropriate message.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for email: " + normalizedEmail));

        Collection<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // ADMINs bypass the email-verification gate.
        boolean enabled = (user.getRole() == Role.ADMIN) || user.isEmailVerified();

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                enabled,          // account enabled only when verified (or ADMIN)
                true,             // accountNonExpired
                true,             // credentialsNonExpired
                true,             // accountNonLocked
                authorities
        );
    }

    // ----------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------

    /**
     * Registers a new USER account, saves year/semester from the form,
     * creates a verification token, and commits everything in one transaction.
     * The verification email is sent AFTER the commit so a mail failure
     * never rolls back the saved account.
     *
     * Having a single @Transactional method (rather than one public method
     * calling a second @Transactional method on the same bean) ensures
     * Spring's proxy actually enforces the transaction boundary and that
     * issueVerificationToken() is called exactly once per registration.
     *
     * @throws MailException (unchecked) if SMTP delivery fails — callers
     *         should catch this and show a friendly message.
     */
    @Transactional
    public String registerAndReturnEmail(RegisterDto dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());

        validateEmailDomain(normalizedEmail);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(
                    "An account with this email address already exists.");
        }

        User user = new User();
        user.setName(dto.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);       // users can never self-assign ADMIN
        user.setEmailVerified(false);  // must verify before accessing the app
        user.setYear(dto.getYear());
        user.setSemester(dto.getSemester());
        userRepository.save(user);

        // issueVerificationToken is called exactly once here, inside the
        // same transaction — no self-invocation proxy bypass.
        return issueVerificationToken(user);
    }

    /**
     * Kept for callers that do not need the token value back.
     * Delegates to registerAndReturnEmail, then sends the email after commit.
     * Throws RuntimeException (from EmailService) if Gmail API delivery fails.
     */
    public void register(RegisterDto dto) {
        log.debug("[REGISTER] register() called for email domain check");
        // registerAndReturnEmail commits the transaction when it returns.
        String token = registerAndReturnEmail(dto);
        String normalizedEmail = normalizeEmail(dto.getEmail());
        log.info("[REGISTER] DB transaction committed; token issued for {}. Now sending email.", normalizedEmail);
        // Email is sent outside the transaction so a mail failure cannot
        // roll back the saved user and token rows.
        emailService.sendVerificationEmail(normalizedEmail, token);
        log.info("[REGISTER] register() completed successfully for {}", normalizedEmail);
    }

    // ----------------------------------------------------------------
    // Email verification
    // ----------------------------------------------------------------

    /**
     * Activates a user account when they follow the verification link.
     *
     * @param rawToken the UUID token from the link's query parameter
     * @throws IllegalArgumentException if the token is unknown, expired, or already used
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken record = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "This verification link is invalid. "
                        + "Please register again or request a new link."));

        if (record.isExpired()) {
            tokenRepository.delete(record);
            throw new IllegalArgumentException(
                    "This verification link has expired (links are valid for "
                    + tokenExpiryHours + " hours). "
                    + "Please register again or request a new link.");
        }

        User user = record.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        // Token is single-use — delete it immediately.
        tokenRepository.delete(record);
    }

    /**
     * Re-sends a verification email to an unverified user.
     * Call this if the user lost their original email.
     *
     * @param email the normalised email address of the unverified user
     */
    @Transactional
    public void resendVerification(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for: " + normalizedEmail));

        if (user.isEmailVerified()) {
            return; // already verified, nothing to do
        }

        String token = issueVerificationToken(user);
        emailService.sendVerificationEmail(normalizedEmail, token);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Creates (or replaces) a verification token for the given user.
     *
     * The delete runs as a direct JPQL DELETE statement (via @Modifying on
     * the repository method) before the new token is saved, preventing the
     * UNIQUE constraint on user_id from firing when a re-issue occurs.
     *
     * Must be called from within a @Transactional context.
     */
    private String issueVerificationToken(User user) {
        // Delete any existing token with a direct SQL DELETE so the row is
        // gone before we insert the replacement.
        tokenRepository.deleteByUser(user);
        // Flush ensures the DELETE reaches the DB before the INSERT below.
        tokenRepository.flush();

        String tokenValue = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(tokenExpiryHours);

        EmailVerificationToken token = new EmailVerificationToken(tokenValue, user, expiry);
        tokenRepository.save(token);

        return tokenValue;
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase();
    }

    /**
     * Enforces the @pvppcoe.ac.in domain restriction server-side.
     * Exact domain match — prevents sub-domain tricks.
     */
    private void validateEmailDomain(String normalizedEmail) {
        int atIndex = normalizedEmail.lastIndexOf('@');
        if (atIndex < 1) {
            throw new InvalidEmailDomainException(
                    "Please enter a valid @" + ALLOWED_DOMAIN + " email address.");
        }
        String domain = normalizedEmail.substring(atIndex + 1);
        if (!domain.equals(ALLOWED_DOMAIN)) {
            throw new InvalidEmailDomainException(
                    "Registration is only open to " + ALLOWED_DOMAIN + " email addresses. "
                    + "Other domains are not accepted.");
        }
    }
}
