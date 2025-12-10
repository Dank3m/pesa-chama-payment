package com.tablebanking.payment.event;

import lombok.*;

import java.time.Instant;

/**
 * Query member info request (to main app)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoQueryEvent {
    
    private String eventId;
    private String queryType;  // BY_ID_NUMBER, BY_PHONE, BY_MEMBER_ID
    private String identifier;
    private String correlationId;
    private Instant timestamp;
}
