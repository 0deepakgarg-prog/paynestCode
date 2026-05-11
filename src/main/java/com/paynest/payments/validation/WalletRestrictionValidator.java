package com.paynest.payments.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletRestriction;
import com.paynest.users.repository.WalletRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WalletRestrictionValidator {

    private static final String ALL_SERVICES = "ALL_SERVICES";
    private static final String SELECTED_SERVICES = "SELECTED_SERVICES";

    private final WalletRestrictionRepository walletRestrictionRepository;

    public void validateTransfer(Wallet debitorWallet, Wallet creditorWallet, String serviceCode) {
        validateSendAllowed(debitorWallet, serviceCode);
        validateReceiveAllowed(creditorWallet, serviceCode);
    }

    private void validateSendAllowed(Wallet wallet, String serviceCode) {
        if (wallet == null || wallet.getWalletId() == null) {
            return;
        }

        walletRestrictionRepository.findById(wallet.getWalletId())
                .map(WalletRestriction::getRestrictions)
                .filter(restrictions -> isBlockedForService(restrictions.get("sendBlock"), serviceCode))
                .ifPresent(restrictions -> {
                    throw new ApplicationException(
                            PaymentErrorCode.WALLET_SEND_BLOCKED,
                            null,
                            Map.of(
                                    "walletId", wallet.getWalletId(),
                                    "serviceCode", normalize(serviceCode)
                            )
                    );
                });
    }

    private void validateReceiveAllowed(Wallet wallet, String serviceCode) {
        if (wallet == null || wallet.getWalletId() == null) {
            return;
        }

        walletRestrictionRepository.findById(wallet.getWalletId())
                .map(WalletRestriction::getRestrictions)
                .filter(restrictions -> isBlockedForService(restrictions.get("receiveBlock"), serviceCode))
                .ifPresent(restrictions -> {
                    throw new ApplicationException(
                            PaymentErrorCode.WALLET_RECEIVE_BLOCKED,
                            null,
                            Map.of(
                                    "walletId", wallet.getWalletId(),
                                    "serviceCode", normalize(serviceCode)
                            )
                    );
                });
    }

    private boolean isBlockedForService(JsonNode blockConfig, String serviceCode) {
        if (blockConfig == null || !blockConfig.path("blocked").asBoolean(false)) {
            return false;
        }

        String mode = blockConfig.path("mode").asText(ALL_SERVICES);
        if (mode.isBlank() || ALL_SERVICES.equalsIgnoreCase(mode)) {
            return true;
        }

        if (!SELECTED_SERVICES.equalsIgnoreCase(mode)) {
            return false;
        }

        JsonNode services = blockConfig.path("services");
        if (!services.isArray()) {
            return false;
        }

        String normalizedServiceCode = normalize(serviceCode);
        for (JsonNode service : services) {
            if (normalizedServiceCode.equals(normalize(service.asText()))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
