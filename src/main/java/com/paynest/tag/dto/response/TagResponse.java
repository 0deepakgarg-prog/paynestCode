package com.paynest.tag.dto.response;

import com.paynest.tag.entity.Tag;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TagResponse {

    private final Long tagId;
    private final String tagCode;
    private final String tagName;
    private final String category;
    private final Boolean isDefault;
    private final String tagType;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TagResponse(Tag tag) {
        this.tagId = tag.getTagId();
        this.tagCode = tag.getTagCode();
        this.tagName = tag.getTagName();
        this.category = tag.getCategory();
        this.isDefault = tag.getIsDefault();
        this.tagType = tag.getTagType();
        this.status = tag.getStatus();
        this.createdAt = tag.getCreatedAt();
        this.updatedAt = tag.getUpdatedAt();
    }
}
