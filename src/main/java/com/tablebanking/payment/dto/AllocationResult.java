package com.tablebanking.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment allocation result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationResult {
    
    private UUID paymentTransactionId;
    private BigDecimal totalAmount;
    private BigDecimal allocatedToContribution;
    private BigDecimal allocatedToLoan;
    private BigDecimal unallocatedAmount;
    private UUID contributionId;
    private UUID loanId;
    private String status;
    private String message;
}
