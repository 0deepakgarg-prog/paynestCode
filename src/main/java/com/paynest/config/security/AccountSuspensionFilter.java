package com.paynest.config.security;

import com.paynest.common.Constants;
import com.paynest.exception.ApiErrorResponseWriter;
import com.paynest.exception.CommonErrorCode;
import com.paynest.users.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AccountSuspensionFilter extends OncePerRequestFilter {

    private static final String WALLET_BALANCE_PATH_PREFIX = "/api/v1/wallet/getAccountWallets/";

    private final AccountRepository accountRepository;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String accountId = authentication.getName();
        boolean suspended = accountRepository.findById(accountId)
                .map(account -> Constants.ACCOUNT_STATUS_SUSPENDED.equalsIgnoreCase(account.getStatus()))
                .orElse(false);
        if (!suspended || isAllowedSuspendedAccountRequest(request, accountId)) {
            filterChain.doFilter(request, response);
            return;
        }

        apiErrorResponseWriter.write(request, response, CommonErrorCode.ACCESS_DENIED);
    }

    private boolean isAllowedSuspendedAccountRequest(HttpServletRequest request, String accountId) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path != null
                && path.equals(WALLET_BALANCE_PATH_PREFIX + accountId);
    }
}
