package com.bookflow.repository;

import com.bookflow.entity.Resource;
import com.bookflow.entity.ResourceStatus;
import com.bookflow.entity.ResourceType;
import com.bookflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Returns all resources uploaded by a specific user,
     * newest first. Used for the "My Resources" page.
     */
    List<Resource> findByUploadedByOrderByCreatedAtDesc(User uploadedBy);

    /**
     * Returns all resources with the given status, oldest first.
     * Used by the admin verification queue (PENDING resources).
     */
    List<Resource> findByStatusOrderByCreatedAtAsc(ResourceStatus status);

    /**
     * Counts resources by status. Used by the admin dashboard statistics.
     * Spring Data derives the query automatically from the method name.
     */
    long countByStatus(ResourceStatus status);

    /**
     * Counts resources by status AND type.
     * Used by the library category cards to show real resource counts.
     */
    long countByStatusAndType(ResourceStatus status, ResourceType type);

    /**
     * Returns the N most-recently created resources across all statuses.
     * Used by the admin dashboard recent-activity panel.
     */
    @Query("SELECT r FROM Resource r ORDER BY r.createdAt DESC LIMIT :limit")
    List<Resource> findRecentResources(@Param("limit") int limit);

    /**
     * Public resource library query.
     *
     * Enforces status = APPROVED at the database level — this is non-negotiable
     * and cannot be bypassed by any filter parameter.
     *
     * All filter parameters are optional:
     *   - a null type   → matches any type
     *   - a null/blank keyword → skips full-text search
     *   - a null/blank branch/subject → skips those filters
     *   - a null year/semester → skips those filters
     *
     * Results are ordered newest-first.
     */
    @Query("""
            SELECT r FROM Resource r
            WHERE r.status = 'APPROVED'
              AND (:type     IS NULL OR r.type = :type)
              AND (:year     IS NULL OR r.year = :year)
              AND (:semester IS NULL OR r.semester = :semester)
              AND (:branch   IS NULL OR LOWER(r.branch)  LIKE LOWER(CONCAT('%', :branch,  '%')))
              AND (:subject  IS NULL OR LOWER(r.subject) LIKE LOWER(CONCAT('%', :subject, '%')))
              AND (
                    :keyword IS NULL
                    OR LOWER(r.title)       LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(r.subject)     LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
            ORDER BY r.createdAt DESC
            """)
    List<Resource> findApproved(
            @Param("keyword")  String keyword,
            @Param("type")     ResourceType type,
            @Param("branch")   String branch,
            @Param("subject")  String subject,
            @Param("year")     Integer year,
            @Param("semester") Integer semester
    );
}
