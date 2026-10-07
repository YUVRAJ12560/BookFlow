package com.bookflow.controller;

import com.bookflow.entity.User;
import com.bookflow.repository.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // ----------------------------------------------------------------
    // Dashboard — GET /user/dashboard
    // ----------------------------------------------------------------

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = resolveUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Dashboard");
        return "user/dashboard";
    }

    // ----------------------------------------------------------------
    // Profile completion — GET /user/complete-profile
    //
    // Shown to Google Sign-In users whose year/semester are not yet set.
    // Already authenticated users who have a complete profile are
    // redirected straight to the dashboard.
    // ----------------------------------------------------------------

    @GetMapping("/complete-profile")
    public String showCompleteProfile(Authentication authentication, Model model) {
        User user = resolveUser(authentication);

        // If profile is already complete, skip this page.
        if (user.getYear() != null && user.getSemester() != null) {
            return "redirect:/user/dashboard";
        }

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Complete Your Profile");
        return "user/complete-profile";
    }

    // ----------------------------------------------------------------
    // Profile completion — POST /user/complete-profile
    //
    // Saves year and semester to the authenticated user's DB record.
    // Validates server-side: year 1-4, semester 1-8, both required.
    // ----------------------------------------------------------------

    @PostMapping("/complete-profile")
    public String saveCompleteProfile(
            Authentication authentication,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer semester,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = resolveUser(authentication);

        // Server-side validation — never trust the client.
        boolean valid = year != null && year >= 1 && year <= 4
                     && semester != null && semester >= 1 && semester <= 8;

        if (!valid) {
            model.addAttribute("user", user);
            model.addAttribute("profileError",
                    "Please select a valid year (1–4) and semester (1–8).");
            model.addAttribute("pageTitle", "Complete Your Profile");
            return "user/complete-profile";
        }

        user.setYear(year);
        user.setSemester(semester);
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("profileSuccess",
                "Your academic profile has been saved.");
        return "redirect:/user/dashboard";
    }

    // ----------------------------------------------------------------
    // Private helper
    // ----------------------------------------------------------------

    private User resolveUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
