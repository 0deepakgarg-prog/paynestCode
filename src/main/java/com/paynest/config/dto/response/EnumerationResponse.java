package com.paynest.config.dto.response;

import com.paynest.config.entity.Enumeration;

public record EnumerationResponse(
        String enumType,
        String enumCode,
        String enumValue,
        Long parentEnumId,
        String description,
        Integer displayOrder,
        String field1,
        String field2,
        String field3,
        String field4,
        String field5
) {
    public static EnumerationResponse from(Enumeration enumeration) {
        return new EnumerationResponse(
                enumeration.getEnumType(),
                enumeration.getEnumCode(),
                enumeration.getEnumValue(),
                enumeration.getParentEnumId(),
                enumeration.getDescription(),
                enumeration.getDisplayOrder(),
                enumeration.getField1(),
                enumeration.getField2(),
                enumeration.getField3(),
                enumeration.getField4(),
                enumeration.getField5()
        );
    }
}
