package com.paynest.fx.repository;

import com.paynest.fx.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findFirstByTargetCurrencyAndIsActiveTrueAndValidFromLessThanEqualOrderByVersionNoDesc(
            String targetCurrency,
            LocalDateTime validFrom
    );

    @Query("select coalesce(max(f.versionNo), 0) from FxRate f where f.targetCurrency = :targetCurrency")
    Long findLastVersionNoByTargetCurrency(String targetCurrency);
}
