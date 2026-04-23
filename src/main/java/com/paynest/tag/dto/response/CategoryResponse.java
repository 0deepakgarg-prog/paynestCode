package com.paynest.tag.dto.response;

import com.paynest.tag.entity.Category;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CategoryResponse {

    private final Long categoryId;
    private final String categoryCode;
    private final String categoryName;
    private final String description;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CategoryResponse(Category category) {
        this.categoryId = category.getCategoryId();
        this.categoryCode = category.getCategoryCode();
        this.categoryName = category.getCategoryName();
        this.description = category.getDescription();
        this.status = category.getStatus();
        this.createdAt = category.getCreatedAt();
        this.updatedAt = category.getUpdatedAt();
    }
}
