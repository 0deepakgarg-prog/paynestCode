package com.paynest.statements.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptTemplatePage {

    private String size = "A4";
    private String backgroundColor = "#FFFFFF";
}
