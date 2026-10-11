
package com.bookflow.dto;

import com.bookflow.entity.Resource;

import java.util.List;

public record ResourceGroupDto(
        String title,
        String description,
        String type,
        String branch,
        String subject,
        Integer year,
        Integer semester,
        List<Resource> files
) {
}
