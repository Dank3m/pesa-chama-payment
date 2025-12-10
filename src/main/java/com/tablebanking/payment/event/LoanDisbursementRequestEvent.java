package com.tablebanking.payment.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event received from main app when loan is approved for disbursement
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanDisbursementRequestEvent {
    
    private String eventId;
    private String eventType;  // LOAN_DISBURSEMENT_REQUEST
    
    private UUID loanId;
    private String loanNumber;
    private UUID memberId;
    private String memberName;
    private String phoneNumber;
    private String bankAccount;
    private String bankCode;
    private UUID groupId;
    private String groupName;
    
    private BigDecimal amount;
    private String disbursementMethod;  // MPESA, BANK, PESALINK
    
    private Instant timestamp;
}
