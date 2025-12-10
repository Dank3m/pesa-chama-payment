package com.tablebanking.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Payment notification from Family Bank (IPN)
 * Family Bank calls us when payment is received
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentNotificationRequest {
    
    private String action;  // "PAYMENT_NOTIFICATION"
    private PaymentNotificationPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentNotificationPayload {
        
        @JsonProperty("customer_id")
        private String customerId;
        
        @JsonProperty("payer_name")
        private String payerName;
        
        @JsonProperty("payer_phone")
        private String payerPhone;
        
        @JsonProperty("txn_amount")
        private BigDecimal txnAmount;
        
        @JsonProperty("payment_mode")
        private String paymentMode;
        
        @JsonProperty("txn_reference")
        private String txnReference;
        
        @JsonProperty("collection_account")
        private String collectionAccount;
        
        @JsonProperty("txn_narration")
        private String txnNarration;
        
        @JsonProperty("date_time")
        private String dateTime;
    }
}
