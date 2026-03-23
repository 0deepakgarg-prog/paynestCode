package com.paynest.users.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountWalletBalancesResponse {

    private String accountId;
    private List<BalanceResponse> balances;
}

