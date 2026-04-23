package com.paynest.statements.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptTemplateElement {

    private String id;
    private String type;
    private String text;
    private String field;
    private String template;
    private Float x;
    private Float y;
    private Float width;
    private Float height;
    private String style;
    private String align = "left";
    private String format;
    private Boolean visible = true;
    private String strokeColor;
    private String fillColor;
    private Float lineWidth;
}
