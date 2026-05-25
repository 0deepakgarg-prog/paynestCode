package com.paynest.documents.dto;

public record DocumentCategoryResponse(
        String categoryCode,
        String categoryName,
        String description
) {
}
