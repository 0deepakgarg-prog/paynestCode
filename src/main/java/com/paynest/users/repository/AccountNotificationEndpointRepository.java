package com.paynest.users.repository;

import com.paynest.users.entity.AccountNotificationEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountNotificationEndpointRepository extends JpaRepository<AccountNotificationEndpoint, Long> {

    Optional<AccountNotificationEndpoint> findByAccountIdAndEndpointTypeAndIsPrimaryTrue(
            String accountId,
            String endpointType
    );

    List<AccountNotificationEndpoint> findByAccountId(String accountId);

    List<AccountNotificationEndpoint> findByAccountIdAndStatusAndIsPrimaryTrue(String accountId, String status);
}
