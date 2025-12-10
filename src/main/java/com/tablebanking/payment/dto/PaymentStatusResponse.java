package com.tablebanking.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payment status query result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusResponse {
    
    private UUID paymentId;
    private String txnReference;
    private String status;
    private String statusDescription;
    private BigDecimal amount;
    private List<AllocationInfo> allocations;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllocationInfo {
        private String type;
        private BigDecimal amount;
        private UUID targetId;
        private String status;
    }
}
