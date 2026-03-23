package com.paynest.users.dto.response;

import com.paynest.users.entity.Wallet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountWalletsResponse {

    private String accountId;
    private List<Wallet> wallets;
}

