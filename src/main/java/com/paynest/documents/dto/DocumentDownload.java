package com.paynest.documents.dto;

import org.springframework.core.io.Resource;

public record DocumentDownload(
        String fileName,
        String contentType,
        long size,
        Resource resource
) {
}
