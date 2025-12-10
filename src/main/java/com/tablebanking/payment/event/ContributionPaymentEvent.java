package com.tablebanking.payment.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event to notify main app of contribution payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionPaymentEvent {
    
    private String eventId;
    private String eventType;  // CONTRIBUTION_PAYMENT
    
    private UUID contributionId;
    private UUID memberId;
    private String memberName;
    private UUID groupId;
    private BigDecimal amount;
    private String paymentReference;
    private String paymentMode;
    
    private Instant timestamp;
}
