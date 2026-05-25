package com.paynest.documents.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID documentId,
        String entityType,
        String entityId,
        String categoryCode,
        String documentTypeCode,
        String documentTypeName,
        String documentName,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String checksumSha256,
        boolean thumbnailAvailable,
        String status,
        String uploadedBy,
        LocalDateTime uploadedAt
) {
}
