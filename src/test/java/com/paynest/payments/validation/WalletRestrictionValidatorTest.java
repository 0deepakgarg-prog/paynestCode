package com.paynest.payments.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.exception.ApplicationException;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletRestriction;
import com.paynest.users.repository.WalletRestrictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletRestrictionValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WalletRestrictionRepository walletRestrictionRepository = mock(WalletRestrictionRepository.class);
    private final WalletRestrictionValidator validator = new WalletRestrictionValidator(walletRestrictionRepository);

    @ParameterizedTest
    @ValueSource(strings = {"U2U", "MERCHANTPAY", "CASHIN", "CASHOUT", "BILLPAY", "O2C", "INTRAWALLET"})
    void validateTransfer_shouldRejectSelectedServiceSendBlockForEveryPaymentService(String serviceCode) throws Exception {
        Wallet debitorWallet = wallet(1001L);
        Wallet creditorWallet = wallet(1002L);
        when(walletRestrictionRepository.findById(1001L))
                .thenReturn(Optional.of(restriction(1001L, true, "SELECTED_SERVICES", List.of(serviceCode), false, null, List.of())));
        when(walletRestrictionRepository.findById(1002L)).thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validateTransfer(debitorWallet, creditorWallet, serviceCode)
        );

        assertEquals("WALLET_SEND_BLOCKED", exception.getErrorCode());
    }

    @Test
    void validateTransfer_shouldAllowServiceWhenSelectedServicesDoNotContainIt() throws Exception {
        Wallet debitorWallet = wallet(1001L);
        Wallet creditorWallet = wallet(1002L);
        when(walletRestrictionRepository.findById(1001L))
                .thenReturn(Optional.of(restriction(1001L, true, "SELECTED_SERVICES", List.of("CASHIN"), false, null, List.of())));
        when(walletRestrictionRepository.findById(1002L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> validator.validateTransfer(debitorWallet, creditorWallet, "U2U"));
    }

    @Test
    void validateTransfer_shouldRejectReceiveBlock() throws Exception {
        Wallet debitorWallet = wallet(1001L);
        Wallet creditorWallet = wallet(1002L);
        when(walletRestrictionRepository.findById(1001L)).thenReturn(Optional.empty());
        when(walletRestrictionRepository.findById(1002L))
                .thenReturn(Optional.of(restriction(1002L, false, null, List.of(), true, "ALL_SERVICES", List.of())));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validateTransfer(debitorWallet, creditorWallet, "U2U")
        );

        assertEquals("WALLET_RECEIVE_BLOCKED", exception.getErrorCode());
    }

    private Wallet wallet(Long walletId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        return wallet;
    }

    private WalletRestriction restriction(
            Long walletId,
            boolean sendBlocked,
            String sendMode,
            List<String> sendServices,
            boolean receiveBlocked,
            String receiveMode,
            List<String> receiveServices) throws Exception {
        WalletRestriction restriction = new WalletRestriction();
        restriction.setWalletId(walletId);
        restriction.setRestrictions(restrictions(sendBlocked, sendMode, sendServices, receiveBlocked, receiveMode, receiveServices));
        return restriction;
    }

    private JsonNode restrictions(
            boolean sendBlocked,
            String sendMode,
            List<String> sendServices,
            boolean receiveBlocked,
            String receiveMode,
            List<String> receiveServices) throws Exception {
        return objectMapper.readTree("""
                {
                  "sendBlock": {
                    "blocked": %s,
                    "mode": %s,
                    "services": %s
                  },
                  "receiveBlock": {
                    "blocked": %s,
                    "mode": %s,
                    "services": %s
                  }
                }
                """.formatted(
                sendBlocked,
                sendMode == null ? "null" : "\"" + sendMode + "\"",
                objectMapper.writeValueAsString(sendServices),
                receiveBlocked,
                receiveMode == null ? "null" : "\"" + receiveMode + "\"",
                objectMapper.writeValueAsString(receiveServices)
        ));
    }
}
