package com.tablebanking.payment.entity;

import com.tablebanking.payment.entity.enums.CollectionStatus;
import com.tablebanking.payment.entity.enums.CollectionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stk_push_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StkPushRequest extends BaseEntity {

    @Column(name = "collection_ref", nullable = false, unique = true, length = 100)
    private String collectionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_type", nullable = false, length = 50)
    private CollectionType collectionType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "original_amount", precision = 15, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "account_reference", length = 50)
    private String accountReference;

    @Column(name = "transaction_desc")
    private String transactionDesc;

    @Column(name = "merchant_request_id", length = 100)
    private String merchantRequestId;

    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    @Column(name = "mpesa_receipt_number", length = 50)
    private String mpesaReceiptNumber;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc", columnDefinition = "TEXT")
    private String resultDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private CollectionStatus status = CollectionStatus.INITIATED;

    @Column(name = "completed_at")
    private Instant completedAt;
}
