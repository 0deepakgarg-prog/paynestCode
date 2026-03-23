package com.paynest.users.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.ErrorCodes;
import com.paynest.users.dto.response.AccountWalletBalancesResponse;
import com.paynest.users.dto.response.BalanceResponse;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletCacheService {

    private static final String KEY_PREFIX = "wallets";
    private static final String DEFAULT_TENANT = "default";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;

    @Value("${app.wallet-cache.ttl-hours:1}")
    private long walletCacheTtlHours;

    public Optional<AccountWalletBalancesResponse> getCachedAccountWallets(String accountId) {
        try {
            String payload = redisTemplate.opsForValue().get(buildCacheKey(accountId));
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, AccountWalletBalancesResponse.class));
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable while reading wallet cache for accountId={}", accountId, ex);
            return Optional.empty();
        } catch (JsonProcessingException ex) {
            log.warn("Unable to deserialize wallet cache for accountId={}", accountId, ex);
            return Optional.empty();
        }
    }

    public AccountWalletBalancesResponse refreshAccountWallets(String accountId) {
        AccountWalletBalancesResponse response = buildAccountWallets(accountId);
        cacheAccountWallets(response);
        return response;
    }

    public void cacheAccountWallets(AccountWalletBalancesResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                    buildCacheKey(response.getAccountId()),
                    payload,
                    Duration.ofHours(walletCacheTtlHours)
            );
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable while writing wallet cache for accountId={}", response.getAccountId(), ex);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize wallet cache for accountId={}", response.getAccountId(), ex);
        }
    }

    private AccountWalletBalancesResponse buildAccountWallets(String accountId) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        List<Wallet> wallets = walletRepository.findByAccountId(accountId);
        List<BalanceResponse> balances = wallets.stream()
                .map(this::toBalanceResponse)
                .toList();

        return new AccountWalletBalancesResponse(accountId, balances);
    }

    private BalanceResponse toBalanceResponse(Wallet wallet) {
        WalletBalance balance = walletBalanceRepository.findById(wallet.getWalletId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO, "Wallet not found"));

        return new BalanceResponse(
                wallet.getWalletType(),
                wallet.getCurrency(),
                balance.getAvailableBalance(),
                balance.getFrozenBalance(),
                balance.getFicBalance()
        );
    }

    private String buildCacheKey(String accountId) {
        String tenant = TenantContext.getTenant();
        if (tenant == null || tenant.isBlank()) {
            tenant = DEFAULT_TENANT;
        }
        return KEY_PREFIX + ":" + tenant + ":" + accountId;
    }
}

