package com.tablebanking.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Validation request from Family Bank
 * Family Bank calls us to validate a customer before accepting payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationRequest {
    
    private String action;  // "VALIDATION"
    private ValidationPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidationPayload {
        private String identifier;
        
        @JsonProperty("identifier_type")
        private String identifierType;
        
        @JsonProperty("collection_account")
        private String collectionAccount;
    }
}
