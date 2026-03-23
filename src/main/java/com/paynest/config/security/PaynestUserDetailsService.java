package com.paynest.config.security;

import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaynestUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String accountId) throws UsernameNotFoundException {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new UsernameNotFoundException("Account is not active");
        }

        String accountType = account.getAccountType() == null ? "USER" : account.getAccountType();
        return User.withUsername(account.getAccountId())
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + accountType)))
                .build();
    }
}

