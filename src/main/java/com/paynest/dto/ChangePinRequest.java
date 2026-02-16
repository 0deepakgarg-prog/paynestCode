package com.paynest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePinRequest {

    @NotBlank
    @Size(min = 4, max = 4)
    private String oldPin;

    @NotBlank
    @Size(min = 4, max = 4)
    private String newPin;

    private String accountId;

    @NotBlank
    private String identifierType;

    @NotBlank
    private String identifierValue;

    // getters & setters
}
