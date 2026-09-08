package com.bookflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType type;

    // Server-generated safe filename used for storage — never the raw uploaded name.
    @Column(nullable = false)
    private String filePath;

    // Original filename kept as metadata for display purposes only.
    @Column(length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    private String branch;

    @Column(nullable = false, length = 100)
    private String subject;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer semester;

    // Who uploaded this resource — set server-side from the authenticated principal.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResourceStatus status;

    @Column(length = 500)
    private String rejectionReason;

    // Set by admin when approving or rejecting — never set by the uploader.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
