package com.tablebanking.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Member info for validation (fetched from main app)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfo {
    
    private UUID memberId;
    private String memberName;
    private String idNumber;
    private String phoneNumber;
    private String email;
    private UUID groupId;
    private String groupName;
    private String status;
    
    // Current financial status
    private BigDecimal outstandingContribution;
    private UUID currentContributionId;
    private BigDecimal outstandingLoanBalance;
    private UUID activeLoanId;
}
