package com.paynest.documents.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.documents.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentCatalogueController {

    private final DocumentService documentService;

    @GetMapping("/api/v1/document-categories")
    public ResponseEntity<ApiResponse> listCategories() {
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Document categories fetched successfully",
                "documentCategories",
                documentService.listCategories()
        ));
    }

    @GetMapping("/api/v1/document-types")
    public ResponseEntity<ApiResponse> listTypes(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String categoryCode
    ) {
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Document types fetched successfully",
                "documentTypes",
                documentService.listTypes(entityType, categoryCode)
        ));
    }
}
