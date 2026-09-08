package com.bookflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stores a single-use, expiring email verification token for a user.
 *
 * A new token overwrites the old one so there is never more than one
 * active token per user.  Tokens expire after a configurable TTL
 * (default 24 hours). Once the user clicks the link the row is deleted.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID-based opaque token value — never the user's password or PII. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** The user this token belongs to. One token per user at most. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** When this token stops being valid. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public EmailVerificationToken(String token, User user, LocalDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
