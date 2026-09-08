package com.bookflow.dto;

import com.bookflow.entity.ResourceType;
import lombok.Getter;
import lombok.Setter;

/**
 * Carries the search keyword and all optional filter values submitted
 * from the public resource library page.
 *
 * All fields are optional — a null or blank value means "no filter on this field".
 * Validation is intentionally absent; invalid/unknown values simply return no results.
 */
@Getter
@Setter
public class ResourceFilterDto {

    /** Free-text search: matched against title, description, and subject. */
    private String keyword;

    /** Filter by exact ResourceType enum value. */
    private ResourceType type;

    /** Filter by branch (case-insensitive partial match). */
    private String branch;

    /** Filter by subject (case-insensitive partial match). */
    private String subject;

    /** Filter by academic year (1–4). */
    private Integer year;

    /** Filter by semester (1–8). */
    private Integer semester;

    /** Returns true when no filter or keyword has been supplied. */
    public boolean isEmpty() {
        return isBlank(keyword)
                && type == null
                && isBlank(branch)
                && isBlank(subject)
                && year == null
                && semester == null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
