package com.paynest.tag.dto.response;

import com.paynest.tag.entity.UserTag;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserTagResponse {

    private final Long id;
    private final String accountId;
    private final Long tagId;
    private final String status;
    private final LocalDateTime createdAt;
    private final String createdBy;

    public UserTagResponse(UserTag userTag) {
        this.id = userTag.getId();
        this.accountId = userTag.getAccountId();
        this.tagId = userTag.getTagId();
        this.status = userTag.getStatus();
        this.createdAt = userTag.getCreatedAt();
        this.createdBy = userTag.getCreatedBy();
    }
}
