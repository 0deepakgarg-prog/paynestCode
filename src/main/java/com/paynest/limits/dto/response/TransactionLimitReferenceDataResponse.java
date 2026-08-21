package com.paynest.limits.dto.response;

import com.paynest.tag.dto.response.TagResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TransactionLimitReferenceDataResponse {

    private List<String> limitTypes;
    private List<String> subjectKeys;
    private List<String> walletTypes;
    private List<String> partyTypes;
    private List<String> periodTypes;
    private List<String> requestGateways;
    private List<String> statuses;
    private List<TagResponse> tags;
}
