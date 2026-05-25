package com.paynest.pricing.service;

import com.paynest.payments.service.PricingCalculator;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.entity.PricingRule;
import com.paynest.pricing.repository.PricingRuleRepository;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.UserTagRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private PricingCalculator pricingCalculator;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private UserTagRepository userTagRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private PricingService pricingService;

    @Test
    void calculatePricingAmountsForAccounts_shouldApplyAllTagsRuleWhenAccountsHaveSpecificTags() {
        PricingRule allTagsRule = pricingRule("IPSP2P", "ALLTAGS", "ALLTAGS");
        UserTag senderUserTag = userTag("sender-1", 11L);
        UserTag receiverUserTag = userTag("receiver-1", 22L);
        Tag senderTag = tag(11L, "SUBSCRIBER_BASE");
        Tag receiverTag = tag(22L, "SUBSCRIBER_BASE");

        when(userTagRepository.findByAccountId("sender-1")).thenReturn(List.of(senderUserTag));
        when(userTagRepository.findByAccountId("receiver-1")).thenReturn(List.of(receiverUserTag));
        when(tagRepository.findById(11L)).thenReturn(Optional.of(senderTag));
        when(tagRepository.findById(22L)).thenReturn(Optional.of(receiverTag));
        when(pricingRuleRepository.findApplicableCampaignRules(eq("IPSP2P"), eq("USD"), any()))
                .thenReturn(List.of());
        when(pricingRuleRepository.findApplicableStaticRules(eq("IPSP2P"), any(), any(), eq("USD"), any()))
                .thenAnswer(invocation -> {
                    String senderTagKey = invocation.getArgument(1);
                    String receiverTagKey = invocation.getArgument(2);
                    if ("ALLTAGS".equals(senderTagKey) && "ALLTAGS".equals(receiverTagKey)) {
                        return List.of(allTagsRule);
                    }
                    return List.of();
                });
        when(pricingCalculator.calculate(
                any(),
                eq(new BigDecimal("10.00")),
                eq("IPSP2P"),
                eq("sender-1"),
                eq("receiver-1"),
                eq("SERVICE_CHARGE")
        )).thenReturn(new BigDecimal("1.00"));

        PricingComputationResponse response = pricingService.calculatePricingAmountsForAccounts(
                "IPSP2P",
                "USD",
                new BigDecimal("10.00"),
                "sender-1",
                "receiver-1"
        );

        assertEquals(new BigDecimal("1.00"), response.getServiceChargeAmount());
        assertEquals("SENDER", response.getServiceChargeAffectedParty());
        assertEquals("ALLTAGS", response.getSenderTagKey());
        assertEquals("ALLTAGS", response.getReceiverTagKey());
    }

    private PricingRule pricingRule(String serviceCode, String senderTagKey, String receiverTagKey) {
        PricingRule rule = new PricingRule();
        rule.setId(1L);
        rule.setPricingName("All tags service charge");
        rule.setServiceCode(serviceCode);
        rule.setRuleType("SERVICE_CHARGE");
        rule.setPricingType("STATIC");
        rule.setPayer("SENDER");
        rule.setSenderTagKey(senderTagKey);
        rule.setReceiverTagKey(receiverTagKey);
        rule.setCurrency("USD");
        rule.setStatus("ACTIVE");
        return rule;
    }

    private UserTag userTag(String accountId, Long tagId) {
        UserTag userTag = new UserTag();
        userTag.setAccountId(accountId);
        userTag.setTagId(tagId);
        userTag.setStatus("ACTIVE");
        return userTag;
    }

    private Tag tag(Long tagId, String tagCode) {
        Tag tag = new Tag();
        tag.setTagId(tagId);
        tag.setTagCode(tagCode);
        tag.setTagName(tagCode);
        tag.setCategory("SUBSCRIBER");
        tag.setIsDefault(true);
        tag.setTagType("BASE");
        tag.setStatus("ACTIVE");
        return tag;
    }
}
