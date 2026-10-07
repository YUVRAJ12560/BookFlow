package com.bookflow.config;

import com.bookflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;

    /*
     * PasswordEncoder lives in PasswordEncoderConfig to avoid the circular
     * dependency:  SecurityConfig -> UserService -> PasswordEncoder -> SecurityConfig
     */
    private final PasswordEncoder passwordEncoder;

    // ----------------------------------------------------------------
    // Shared authentication provider
    // ----------------------------------------------------------------

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ----------------------------------------------------------------
    // Admin security filter chain — Order(1) means it is evaluated first.
    //
    // Handles:
    //   GET  /admin          → admin login page (public)
    //   POST /admin-login    → admin login processing
    //   Everything under /admin/** → requires ROLE_ADMIN
    //
    // On successful login: ADMIN → /admin/dashboard
    // On failed login:     back  → /admin?error=true
    // On logout:                 → /admin?logout=true
    // ----------------------------------------------------------------

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            // This chain only applies to /admin and /admin-login paths.
            .securityMatcher("/admin", "/admin/**", "/admin-login")
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // The admin login page itself is public — it must be reachable
                // without credentials so admins can enter them.
                // Visiting /admin does NOT grant ADMIN privileges; the form only
                // collects credentials which are then validated server-side.
                .requestMatchers("/admin").permitAll()
                // Everything else under /admin requires an authenticated ADMIN.
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin")
                .loginProcessingUrl("/admin-login")
                .usernameParameter("email")
                .passwordParameter("password")
                // After admin login, always go to the admin dashboard.
                // The successHandler below also checks role for safety.
                .successHandler(adminSuccessHandler())
                .failureUrl("/admin?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin-logout")
                .logoutSuccessUrl("/admin?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    /**
     * Admin login success handler.
     * Only users with ROLE_ADMIN are allowed through; any other role is
     * rejected with a 403 even if their credentials were correct.
     * This prevents a normal USER from reaching /admin/dashboard by
     * submitting valid USER credentials to the admin login form.
     */
    @Bean
    public AuthenticationSuccessHandler adminSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isAdmin) {
                response.sendRedirect(request.getContextPath() + "/admin/dashboard");
            } else {
                // Authenticated but not ADMIN — send back to the admin login
                // page with an access-denied error rather than granting entry.
                response.sendRedirect(
                        request.getContextPath() + "/admin?error=forbidden");
            }
        };
    }

    // ----------------------------------------------------------------
    // User security filter chain — Order(2), handles all remaining paths.
    //
    // Handles:
    //   GET  /login          → user login page (public)
    //   POST /login          → user login processing
    //   /                    → public (home page)
    //   /register            → public
    //   static files         → public
    //   /auth/google         → Google Sign-In callback (public)
    //   /resources/**        → requires authentication (USER or ADMIN)
    //   /user/**             → requires authentication (USER or ADMIN)
    //   Everything else      → requires authentication
    //
    // Unauthenticated access to /resources or /resources/{id}/download
    // is redirected to /login automatically by Spring Security.
    // ----------------------------------------------------------------

    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Public pages — home, register, login, email verification
                .requestMatchers("/", "/register", "/login").permitAll()
                .requestMatchers("/verify-email", "/verify-pending", "/verify-success",
                                 "/verify-error", "/resend-verification", "/error").permitAll()
                // Google Sign-In callback — validates the ID token server-side
                .requestMatchers("/auth/google").permitAll()
                // Static assets
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                // Resource library and downloads require authentication.
                // Unauthenticated visitors are redirected to /login by Spring Security.
                // The controller additionally enforces status == APPROVED before serving files.
                .requestMatchers("/resources/**").hasAnyRole("USER", "ADMIN")
                // User area
                .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(userSuccessHandler())
                .failureHandler(userFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    /**
     * User login success handler.
     * Routes authenticated users to their role-appropriate landing page.
     */
    @Bean
    public AuthenticationSuccessHandler userSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isAdmin) {
                response.sendRedirect(request.getContextPath() + "/admin/dashboard");
            } else {
                response.sendRedirect(request.getContextPath() + "/user/dashboard");
            }
        };
    }

    /**
     * User login failure handler.
     * Distinguishes between a wrong password (→ /login?error=true) and an
     * unverified account (→ /verify-pending?email=...) so the user gets a
     * clear, actionable message rather than a generic "bad credentials" error.
     */
    @Bean
    public AuthenticationFailureHandler userFailureHandler() {
        return (request, response, exception) -> {
            if (exception instanceof DisabledException) {
                // The account exists but emailVerified=false.
                // Redirect to the verification-pending page carrying the email
                // so the user can request a resend if needed.
                String email = request.getParameter("email");
                String encoded = (email != null)
                        ? java.net.URLEncoder.encode(email.trim(),
                                java.nio.charset.StandardCharsets.UTF_8)
                        : "";
                response.sendRedirect(request.getContextPath()
                        + "/verify-pending?email=" + encoded);
            } else {
                response.sendRedirect(request.getContextPath() + "/login?error=true");
            }
        };
    }
}
