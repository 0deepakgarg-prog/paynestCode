package com.paynest.tag.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddTagRequest {

    @NotBlank(message = "tagCode is required")
    @Size(max = 50, message = "tagCode must be at most 50 characters")
    private String tagCode;

    @NotBlank(message = "tagName is required")
    @Size(max = 100, message = "tagName must be at most 100 characters")
    private String tagName;

    @Size(max = 50, message = "category must be at most 50 characters")
    private String category;

    @Size(max = 50, message = "tagType must be at most 50 characters")
    private String tagType;
}
