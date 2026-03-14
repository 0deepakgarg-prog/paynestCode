package com.paynest.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AddAccountKycRequest {

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

        @JsonProperty("kycData")
        private KYCData kycData;

        @Data
        public static class KYCData {

            @NotBlank(message = "KYC Type is required")
            @JsonProperty("kycType")
            private String kycType;

            @NotBlank(message = "KYC Value is required")
            @JsonProperty("kycValue")
            private String kycValue;

            @JsonProperty("issueDate")
            private LocalDate issueDate;

            @JsonProperty("expiryDate")
            private LocalDate expiryDate;

            @JsonProperty("isPrimary")
            private boolean isPrimary;

            @JsonProperty("kycImageUrl")
            private String kycImageUrl;
    }
}

