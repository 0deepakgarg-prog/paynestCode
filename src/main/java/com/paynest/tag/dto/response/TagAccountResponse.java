package com.paynest.tag.dto.response;

import com.paynest.tag.entity.UserTag;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TagAccountResponse {

    private final Long id;
    private final Long tagId;
    private final String accountId;
    private final String status;
    private final LocalDateTime createdAt;
    private final String createdBy;

    public TagAccountResponse(UserTag userTag) {
        this.id = userTag.getId();
        this.tagId = userTag.getTagId();
        this.accountId = userTag.getAccountId();
        this.status = userTag.getStatus();
        this.createdAt = userTag.getCreatedAt();
        this.createdBy = userTag.getCreatedBy();
    }
}
