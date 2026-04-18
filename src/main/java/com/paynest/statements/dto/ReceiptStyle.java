package com.paynest.statements.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptStyle {

    private String font = "HELVETICA";
    private Float fontSize = 10F;
    private String color = "#111111";
    private String fillColor;
    private String strokeColor;
    private Float lineWidth = 1F;
}
