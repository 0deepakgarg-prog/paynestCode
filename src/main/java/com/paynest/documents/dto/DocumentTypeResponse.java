package com.paynest.documents.dto;

import java.util.List;

public record DocumentTypeResponse(
        String categoryCode,
        String typeCode,
        String typeName,
        boolean multipleAllowed,
        boolean verificationRequired,
        List<String> entityTypes
) {
}
