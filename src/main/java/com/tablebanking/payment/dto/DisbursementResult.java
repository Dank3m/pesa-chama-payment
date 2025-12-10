package com.tablebanking.payment.dto;

import lombok.*;

import java.util.UUID;

/**
 * Response after initiating disbursement
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementResult {
    
    private UUID disbursementId;
    private String batchRef;
    private String paymentRef;
    private String status;
    private String statusDescription;
    private String externalRef;
}
