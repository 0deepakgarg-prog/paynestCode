package com.paynest.payments.qr.controller;

import com.paynest.payments.qr.dto.QrGenerateRequest;
import com.paynest.payments.qr.dto.QrGenerateResponse;
import com.paynest.payments.qr.dto.QrPayRequest;
import com.paynest.payments.qr.dto.QrScanRequest;
import com.paynest.payments.qr.dto.QrScanResponse;
import com.paynest.payments.qr.service.QrPaymentService;
import com.paynest.users.enums.WalletType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class QrPaymentController {

    private final QrPaymentService qrPaymentService;

    @PostMapping("/generate")
    public ResponseEntity<QrGenerateResponse> generate(@Valid @RequestBody QrGenerateRequest request) {
        return ResponseEntity.ok(qrPaymentService.generate(request));
    }

    @GetMapping("/my-static")
    public ResponseEntity<QrGenerateResponse> myStatic(
            @RequestParam String currency,
            @RequestParam(defaultValue = "MAIN") WalletType walletType
    ) {
        return ResponseEntity.ok(qrPaymentService.generateMyStaticQr(currency, walletType));
    }

    @PostMapping("/scan")
    public ResponseEntity<QrScanResponse> scan(@Valid @RequestBody QrScanRequest request) {
        return ResponseEntity.ok(qrPaymentService.scan(request));
    }

    @PostMapping("/pay")
    public ResponseEntity<Object> pay(@Valid @RequestBody QrPayRequest request) {
        return ResponseEntity.ok(qrPaymentService.pay(request));
    }
}
