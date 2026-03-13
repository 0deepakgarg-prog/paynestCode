package com.paynest.dto.response;

import com.paynest.entity.Account;
import com.paynest.entity.KycDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountKycDetailsResponse {

    private Account account;
    private List<KycDocument> kycDocuments;
}
