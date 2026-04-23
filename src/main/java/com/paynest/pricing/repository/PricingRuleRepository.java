package com.paynest.pricing.repository;

import com.paynest.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    List<PricingRule> findAllByOrderByCreatedAtDesc();

    //fetch all the pricing here.
    @Query("""
        SELECT p
        FROM PricingRule p
        WHERE p.serviceCode = :serviceCode
          AND (p.senderTagKey = :senderTagKey OR p.senderTagKey = 'ALLTAG')
          AND (p.receiverTagKey = :receiverTagKey OR p.receiverTagKey = 'ALLTAG')
          AND p.currency = :currency
          AND p.status = 'ACTIVE'
          AND p.validFrom <= :currentTime
          AND (p.validTo IS NULL OR p.validTo >= :currentTime)
        ORDER BY p.createdAt DESC
    """)
    List<PricingRule> findApplicableStaticRules(
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String currency,
            LocalDateTime currentTime
    );

    @Query("""
        SELECT p
        FROM PricingRule p
        WHERE p.serviceCode = :serviceCode
          AND p.senderTagKey = 'ALL'
          AND p.receiverTagKey = 'ALL'
          AND p.currency = :currency
          AND p.pricingType = 'CAMPAIGN'
          AND p.status = 'ACTIVE'
          AND p.validFrom <= :currentTime
          AND (p.validTo IS NULL OR p.validTo >= :currentTime)
        ORDER BY p.createdAt DESC
    """)
    List<PricingRule> findApplicableCampaignRules(
            String serviceCode,
            String currency,
            LocalDateTime currentTime
    );
}
