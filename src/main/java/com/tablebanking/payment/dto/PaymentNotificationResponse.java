package com.tablebanking.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Our response to payment notification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentNotificationResponse {
    
    @JsonProperty("status_code")
    private String statusCode;
    
    @JsonProperty("status_description")
    private String statusDescription;
    
    @JsonProperty("payment_ref")
    private String paymentRef;
    
    @JsonProperty("date_time")
    private String dateTime;
}
