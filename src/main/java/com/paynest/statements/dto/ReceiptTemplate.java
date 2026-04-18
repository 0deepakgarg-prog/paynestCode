package com.paynest.statements.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptTemplate {

    private String serviceCode;
    private String language;
    private String templateVersion;
    private ReceiptTemplatePage page;
    private Map<String, ReceiptStyle> styles;
    private List<ReceiptTemplateElement> elements;
}
