package com.paynest.users.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.users.dto.request.WalletRestrictionRequest;
import com.paynest.users.dto.response.WalletRestrictionHistoryResponse;
import com.paynest.users.dto.response.WalletRestrictionResponse;
import com.paynest.users.entity.WalletRestriction;
import com.paynest.users.entity.WalletRestrictionHistory;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.repository.WalletRestrictionHistoryRepository;
import com.paynest.users.repository.WalletRestrictionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletRestrictionService {

    private final WalletRestrictionRepository walletRestrictionRepository;
    private final WalletRestrictionHistoryRepository walletRestrictionHistoryRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public WalletRestrictionResponse getWalletRestriction(Long walletId) {
        if (walletId == null) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, "Wallet ID is required");
        }
        validateAdminAccess();
        validateWalletExists(walletId);

        WalletRestriction restriction = walletRestrictionRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.WALLET_RESTRICTION_NOT_FOUND,
                        "Wallet restriction not found"
                ));

        return toResponse(restriction);
    }

    @Transactional
    public List<WalletRestrictionHistoryResponse> getWalletRestrictionHistory(Long walletId) {
        if (walletId == null) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, "Wallet ID is required");
        }
        validateAdminAccess();
        validateWalletExists(walletId);

        return walletRestrictionHistoryRepository.findByWalletIdOrderByVersionDesc(walletId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public WalletRestrictionResponse addWalletRestriction(WalletRestrictionRequest request) {
        Long walletId = resolveWalletId(request);
        validateAdminAccess();
        validateWalletExists(walletId);
        validateRestrictions(request.getRestrictions());

        if (walletRestrictionRepository.existsById(walletId)) {
            throw new ApplicationException(
                    ErrorCodes.WALLET_RESTRICTION_ALREADY_EXISTS,
                    "Wallet restriction already exists"
            );
        }

        WalletRestriction restriction = new WalletRestriction();
        restriction.setWalletId(walletId);
        restriction.setVersion(0L);
        restriction.setRestrictions(request.getRestrictions());
        restriction.setUpdatedBy(JWTUtils.getCurrentAccountId());

        WalletRestriction savedRestriction = walletRestrictionRepository.save(restriction);
        insertHistory(savedRestriction, "ADD");
        return toResponse(savedRestriction);
    }

    @Transactional
    public WalletRestrictionResponse updateWalletRestriction(Long walletId, WalletRestrictionRequest request) {
        if (walletId == null) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, "Wallet ID is required");
        }
        if (request == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Request body is required");
        }
        validateAdminAccess();
        validateWalletExists(walletId);
        validateRestrictions(request.getRestrictions());

        WalletRestriction restriction = walletRestrictionRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.WALLET_RESTRICTION_NOT_FOUND,
                        "Wallet restriction not found"
                ));

        Long nextVersion = restriction.getVersion() == null ? 1L : restriction.getVersion() + 1L;
        restriction.setVersion(nextVersion);
        restriction.setRestrictions(request.getRestrictions());
        restriction.setUpdatedBy(JWTUtils.getCurrentAccountId());

        WalletRestriction savedRestriction = walletRestrictionRepository.save(restriction);
        insertHistory(savedRestriction, "UPDATE");
        return toResponse(savedRestriction);
    }

    private Long resolveWalletId(WalletRestrictionRequest request) {
        if (request == null || request.getWalletId() == null) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, "Wallet ID is required");
        }
        return request.getWalletId();
    }

    private void validateWalletExists(Long walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new ApplicationException(ErrorCodes.WALLET_NOT_FOUND, "Wallet not found");
        }
    }

    private void validateRestrictions(JsonNode restrictions) {
        if (restrictions == null || !restrictions.isObject() || restrictions.isEmpty()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Restrictions must be a non-empty JSON object");
        }
    }

    private void validateAdminAccess() {
        if (!"ADMIN".equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Admin token is required");
        }
    }

    private WalletRestrictionResponse toResponse(WalletRestriction restriction) {
        return new WalletRestrictionResponse(
                restriction.getWalletId(),
                restriction.getRestrictions(),
                restriction.getVersion(),
                restriction.getUpdatedAt(),
                restriction.getUpdatedBy()
        );
    }

    private WalletRestrictionHistoryResponse toHistoryResponse(WalletRestrictionHistory history) {
        return new WalletRestrictionHistoryResponse(
                history.getHistoryId(),
                history.getWalletId(),
                history.getVersion(),
                history.getRestrictions(),
                history.getActionType(),
                history.getChangedBy(),
                history.getCreatedAt()
        );
    }

    private void insertHistory(WalletRestriction restriction, String actionType) {
        walletRestrictionHistoryRepository.insert(WalletRestrictionHistory.create(
                restriction.getWalletId(),
                restriction.getVersion(),
                restriction.getRestrictions(),
                actionType,
                restriction.getUpdatedBy()
        ));
    }
}
