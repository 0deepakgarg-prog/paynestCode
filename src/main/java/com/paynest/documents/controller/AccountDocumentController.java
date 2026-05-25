package com.paynest.documents.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.documents.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountDocumentController {

    private final DocumentService documentService;

    @GetMapping("/{accountId}/documents")
    public ResponseEntity<ApiResponse> listDocuments(@PathVariable String accountId) {
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Documents fetched successfully",
                "documents",
                documentService.listForAccount(accountId)
        ));
    }
}
