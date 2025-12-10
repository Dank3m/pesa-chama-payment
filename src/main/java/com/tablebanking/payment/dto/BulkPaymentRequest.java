package com.tablebanking.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bulk payment request to Family Bank
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkPaymentRequest {
    
    private String batchref;
    private String accountdr;
    private String narration;
    private String valuedate;  // Format: yyyy-MM-ddTHH:mm:ss
    private String currency;
    private BigDecimal totalamount;
    private List<DisbursementDetail> dtl;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DisbursementDetail {
        private String batchref;
        private String paymentref;
        private String paymenttype;  // EFT, AAT, MPESA, PESALINK, SWIFT
        private String senderaccount;
        private String senderbank;
        private String senderbankbranch;
        private String senderdetails;
        private String beneficiaryaccount;
        private String beneficiarybank;
        private String beneficiarybankbranch;
        private String beneficiarydetails;
        private String remarks;
        private String purpose;  // DirectCredit, DividendPayment, etc.
        private String currency;
        private BigDecimal paymentamount;
    }
}
