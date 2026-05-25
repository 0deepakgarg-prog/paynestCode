package com.paynest.payments.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.payments.dto.RecentRecipientResponse;
import com.paynest.payments.service.RecentRecipientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class RecentRecipientController {

    private final RecentRecipientService recentRecipientService;

    @GetMapping("/recent-recipients")
    public ResponseEntity<ApiResponse> getRecentRecipients(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) Integer limit
    ) {
        List<RecentRecipientResponse> response = recentRecipientService.getRecentRecipients(accountId, serviceCode, limit);
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "Recent recipients fetched successfully", "recentRecipients", response)
        );
    }
}
