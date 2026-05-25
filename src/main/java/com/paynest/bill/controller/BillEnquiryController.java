package com.paynest.bill.controller;

import com.paynest.bill.dto.BillEnquiryRequest;
import com.paynest.bill.service.BillEnquiryService;
import com.paynest.config.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bill")
@RequiredArgsConstructor
public class BillEnquiryController {

    private final BillEnquiryService billEnquiryService;

    @PostMapping("/subscriber/enquiry")
    @PreAuthorize("hasRole('SUBSCRIBER')")
    public ResponseEntity<ApiResponse> subscriberBillEnquiry(@Valid @RequestBody BillEnquiryRequest request) {
        Object camelResponse = billEnquiryService.enquire(request);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Bill enquiry successful", "billEnquiry", camelResponse));
    }

    @PostMapping("/agent/enquiry")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse> agentBillEnquiry(@Valid @RequestBody BillEnquiryRequest request) {
        Object camelResponse = billEnquiryService.enquire(request);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Bill enquiry successful", "billEnquiry", camelResponse));
    }
}
