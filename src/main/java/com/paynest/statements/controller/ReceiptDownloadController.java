package com.paynest.statements.controller;

import com.paynest.statements.dto.ReceiptFile;
import com.paynest.statements.service.ReceiptDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/download")
@RequiredArgsConstructor
public class ReceiptDownloadController {

    private final ReceiptDownloadService receiptDownloadService;

    @GetMapping(value = "/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReceipt(
            @RequestParam String transactionId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String accountId
    ) {
        ReceiptFile receiptFile = receiptDownloadService.downloadReceipt(transactionId, language, accountId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(receiptFile.getFileName())
                                .build()
                                .toString()
                )
                .body(receiptFile.getContent());
    }
}
