package com.paynest.tag.dto.response;

import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AccountTagResponse {

    private final Long id;
    private final String accountId;
    private final String status;
    private final LocalDateTime createdAt;
    private final String createdBy;
    private final Long tagId;
    private final String tagCode;
    private final String tagName;
    private final String category;
    private final Boolean isDefault;
    private final String tagType;
    private final String tagStatus;

    public AccountTagResponse(UserTag userTag, Tag tag) {
        this.id = userTag.getId();
        this.accountId = userTag.getAccountId();
        this.status = userTag.getStatus();
        this.createdAt = userTag.getCreatedAt();
        this.createdBy = userTag.getCreatedBy();
        this.tagId = tag.getTagId();
        this.tagCode = tag.getTagCode();
        this.tagName = tag.getTagName();
        this.category = tag.getCategory();
        this.isDefault = tag.getIsDefault();
        this.tagType = tag.getTagType();
        this.tagStatus = tag.getStatus();
    }
}
