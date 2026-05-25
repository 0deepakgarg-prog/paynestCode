package com.paynest.documents.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.documents.dto.DocumentDownload;
import com.paynest.documents.dto.DocumentResponse;
import com.paynest.documents.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> upload(
            @RequestParam String entityType,
            @RequestParam String entityId,
            @RequestParam String documentTypeCode,
            @RequestParam(required = false) String documentName,
            @RequestPart("file") MultipartFile file
    ) {
        DocumentResponse response = documentService.upload(
                entityType, entityId, documentTypeCode, documentName, file);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS", "Document uploaded successfully", "document", response));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse> getMetadata(@PathVariable UUID documentId) {
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Document fetched successfully",
                "document",
                documentService.getMetadata(documentId)
        ));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID documentId) {
        DocumentDownload document = documentService.download(documentId);
        return ResponseEntity.ok()
                .contentLength(document.size())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.fileName()).build().toString()
                )
                .body(document.resource());
    }

    @GetMapping("/{documentId}/thumbnail")
    public ResponseEntity<Resource> downloadThumbnail(@PathVariable UUID documentId) {
        DocumentDownload document = documentService.downloadThumbnail(documentId);
        return ResponseEntity.ok()
                .contentLength(document.size())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(document.fileName()).build().toString()
                )
                .body(document.resource());
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse> listByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId
    ) {
        List<DocumentResponse> documents = documentService.listByEntity(entityType, entityId);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS", "Documents fetched successfully", "documents", documents));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse> delete(@PathVariable UUID documentId) {
        documentService.delete(documentId);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS", "Document deleted successfully", "documentId", documentId));
    }
}
