package com.tablebanking.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionEvent {
    private String eventId;
    private String eventType; // STK_PUSH_REQUEST, STK_SENT, COLLECTION_COMPLETED, COLLECTION_FAILED
    private String collectionRef;
    private String collectionType; // LOAN_REPAYMENT, CONTRIBUTION
    private UUID sourceId;
    private UUID memberId;
    private UUID groupId;
    private BigDecimal amount;
    private BigDecimal originalAmount; // Pre-rounding amount (when M-Pesa rounds up, this holds the exact requested amount)
    private String phoneNumber;
    private String mpesaReceiptNumber;
    private String status;
    private String statusDescription;
    private Instant timestamp;
}
