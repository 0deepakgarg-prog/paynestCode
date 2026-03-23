package com.paynest.users.dto.response;

import com.paynest.users.entity.Account;
import com.paynest.users.entity.KycDocument;
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

