package com.paynest.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PasscodeDetailsRequest {

    @NotBlank
    @Pattern(regexp = "\\d{10}")
    private String passcode;
}
