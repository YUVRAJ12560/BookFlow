package com.bookflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Defines the PasswordEncoder bean in its own configuration class so that
 * it has no dependencies on any other application bean.
 *
 * This breaks the SecurityConfig -> UserService -> PasswordEncoder -> SecurityConfig
 * circular dependency: PasswordEncoder initialises first, then UserService can be
 * constructed, then SecurityConfig can wire UserService without a cycle.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
