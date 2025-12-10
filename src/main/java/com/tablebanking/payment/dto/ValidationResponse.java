package com.tablebanking.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Our response to validation request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResponse {
    
    @JsonProperty("status_code")
    private String statusCode;
    
    @JsonProperty("status_description")
    private String statusDescription;
    
    @JsonProperty("date_time")
    private String dateTime;
    
    private ValidationResponsePayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationResponsePayload {
        private String identifier;
        
        @JsonProperty("identifier_type")
        private String identifierType;
        
        @JsonProperty("customer_id")
        private String customerId;
        
        @JsonProperty("customer_name")
        private String customerName;
    }
}
