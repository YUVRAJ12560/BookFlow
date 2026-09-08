package com.bookflow.entity;

public enum ResourceType {

    NOTES("Lecture Notes"),
    PYQ("Previous Year Question Paper"),
    INTERNAL_PAPER("Internal Examination Paper"),
    EXAM_PATTERN("Exam Paper Pattern"),
    REFERENCE_BOOK("Reference Book / Material");

    private final String displayName;

    ResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
