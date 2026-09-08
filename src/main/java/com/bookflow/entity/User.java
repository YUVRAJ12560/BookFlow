package com.bookflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    /**
     * Whether the user has clicked the email verification link.
     * Defaults to false on registration. ADMIN accounts are pre-verified.
     * Hibernate ddl-auto=update will add this column with a default of false
     * for existing rows (MySQL treats missing column as NULL → we default to false).
     */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean emailVerified = false;

    /**
     * Academic year (1–4). Collected at registration.
     * Null-safe for existing rows that predate this column.
     */
    @Column
    private Integer year;

    /**
     * Academic semester (1–8). Collected at registration.
     * Null-safe for existing rows that predate this column.
     */
    @Column
    private Integer semester;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
