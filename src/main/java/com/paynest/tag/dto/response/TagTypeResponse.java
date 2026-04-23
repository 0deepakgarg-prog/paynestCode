package com.paynest.tag.dto.response;

import com.paynest.tag.entity.TagType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TagTypeResponse {

    private final Long tagTypeId;
    private final String typeCode;
    private final String typeName;
    private final String description;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TagTypeResponse(TagType tagType) {
        this.tagTypeId = tagType.getTagTypeId();
        this.typeCode = tagType.getTypeCode();
        this.typeName = tagType.getTypeName();
        this.description = tagType.getDescription();
        this.status = tagType.getStatus();
        this.createdAt = tagType.getCreatedAt();
        this.updatedAt = tagType.getUpdatedAt();
    }
}
