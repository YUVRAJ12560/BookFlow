package com.bookflow.controller;

import com.bookflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserRepository userRepository;

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("pageTitle", "Home");
        if (authentication != null && authentication.isAuthenticated()) {
            userRepository.findByEmail(authentication.getName())
                    .ifPresent(u -> model.addAttribute("user", u));
        }
        return "public/home";
    }
}
