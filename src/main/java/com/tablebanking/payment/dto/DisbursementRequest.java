package com.tablebanking.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to disburse funds (e.g., loan disbursement)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementRequest {
    
    private UUID memberId;
    private String memberName;
    private UUID groupId;
    private String groupName;
    
    // Source of disbursement
    private String sourceType;  // LOAN_DISBURSEMENT, REFUND, etc.
    private UUID sourceId;      // Loan ID, etc.
    
    // Beneficiary details
    private String beneficiaryAccount;
    private String beneficiaryBank;
    private String beneficiaryBankBranch;
    private String beneficiaryPhone;
    
    // Payment details
    private String paymentType;  // MPESA, PESALINK, EFT, etc.
    private BigDecimal amount;
    private String currency;
    private String remarks;
    private String purpose;
}
