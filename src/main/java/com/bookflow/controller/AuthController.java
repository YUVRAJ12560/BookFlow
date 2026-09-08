package com.bookflow.controller;

import com.bookflow.dto.RegisterDto;
import com.bookflow.exception.EmailAlreadyExistsException;
import com.bookflow.exception.InvalidEmailDomainException;
import com.bookflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    // ----------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerDto", new RegisterDto());
        return "public/register";
    }

    @PostMapping("/register")
    public String processRegistration(
            @Valid @ModelAttribute("registerDto") RegisterDto dto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "public/register";
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            model.addAttribute("passwordMismatch", "Passwords do not match. Please try again.");
            return "public/register";
        }

        try {
            userService.register(dto);
        } catch (InvalidEmailDomainException e) {
            model.addAttribute("domainError", e.getMessage());
            return "public/register";
        } catch (EmailAlreadyExistsException e) {
            model.addAttribute("emailError", e.getMessage());
            return "public/register";
        } catch (RuntimeException e) {
            // Catches failures from the Gmail API (IOException/MessagingException
            // wrapped in RuntimeException by EmailService).
            // The user/token row was already committed so registration succeeded —
            // we redirect to the verify-pending page where they can request a resend.
            log.warn("[REGISTER] Email delivery failed for {}: {}", dto.getEmail(), e.getMessage());
            redirectAttributes.addFlashAttribute("registeredEmail", dto.getEmail());
            redirectAttributes.addFlashAttribute("mailError",
                    "Your account was created, but we could not send the "
                    + "verification email right now. Please use the form below "
                    + "to request a new verification link.");
            return "redirect:/verify-pending";
        }

        log.info("[REGISTER] Registration + email send succeeded for {}", dto.getEmail());

        // Registration successful — tell the user to check their inbox.
        redirectAttributes.addFlashAttribute("registeredEmail", dto.getEmail());
        return "redirect:/verify-pending";
    }

    // ----------------------------------------------------------------
    // Login
    // ----------------------------------------------------------------

    @GetMapping("/login")
    public String showLoginForm() {
        return "public/login";
    }

    // ----------------------------------------------------------------
    // Email verification
    // ----------------------------------------------------------------

    /**
     * Displayed immediately after registration.
     * Also shown when an unverified user tries to log in.
     *
     * @param email optional — pre-fills the resend form with the user's address
     */
    @GetMapping("/verify-pending")
    public String verifyPending(
            @RequestParam(required = false) String email,
            Model model) {
        model.addAttribute("email", email != null ? email : "");
        return "public/verify-pending";
    }

    /**
     * Processes the verification token from the emailed link.
     *
     * GET /verify-email?token=<uuid>
     */
    @GetMapping("/verify-email")
    public String verifyEmail(
            @RequestParam(required = false) String token,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (token == null || token.isBlank()) {
            model.addAttribute("errorMessage",
                    "No verification token was provided. "
                    + "Please use the link from your verification email.");
            return "public/verify-error";
        }

        try {
            userService.verifyEmail(token);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "public/verify-error";
        }

        redirectAttributes.addFlashAttribute("verificationSuccess",
                "Your email has been verified! You can now log in.");
        return "redirect:/login";
    }

    /**
     * Resend a verification email to an unverified user.
     *
     * POST /resend-verification
     */
    @PostMapping("/resend-verification")
    public String resendVerification(
            @RequestParam String email,
            RedirectAttributes redirectAttributes) {

        try {
            userService.resendVerification(email);
            redirectAttributes.addFlashAttribute("resendSuccess",
                    "A new verification email has been sent to " + email
                    + ". Please check your inbox.");
        } catch (Exception e) {
            // Don't leak whether the address is registered.
            redirectAttributes.addFlashAttribute("resendSuccess",
                    "If that address is registered and unverified, "
                    + "a new verification email has been sent.");
        }

        return "redirect:/verify-pending?email=" +
                java.net.URLEncoder.encode(email.trim(),
                        java.nio.charset.StandardCharsets.UTF_8);
    }
}
