package com.tablebanking.payment.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Member info response (from main app)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoResponseEvent {
    
    private String eventId;
    private String correlationId;
    private boolean found;
    
    private UUID memberId;
    private String memberName;
    private String idNumber;
    private String phoneNumber;
    private UUID groupId;
    private String groupName;
    private String status;
    
    // Financial status
    private BigDecimal outstandingContribution;
    private UUID currentContributionId;
    private BigDecimal outstandingLoanBalance;
    private UUID activeLoanId;
    
    private Instant timestamp;
}
