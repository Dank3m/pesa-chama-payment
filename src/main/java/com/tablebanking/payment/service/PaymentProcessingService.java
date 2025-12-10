package com.tablebanking.payment.service;

import com.tablebanking.payment.dto.*;
import com.tablebanking.payment.entity.*;
import com.tablebanking.payment.entity.enums.*;
import com.tablebanking.payment.event.*;
import com.tablebanking.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service for handling incoming payments (IPN) from Family Bank
 * and allocating them to contributions and loans
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessingService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final MemberLookupService memberLookupService;

    @Value("${kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    @Value("${kafka.topics.contribution-events:contribution-events}")
    private String contributionEventsTopic;

    @Value("${kafka.topics.loan-events:loan-events}")
    private String loanEventsTopic;

    @Value("${payment.idempotency.enabled:true}")
    private boolean idempotencyEnabled;

    @Value("${payment.idempotency.ttl-hours:24}")
    private int idempotencyTtlHours;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Validate customer for Family Bank (they call us before accepting payment)
     */
    public ValidationResponse validateCustomer(ValidationRequest request) {
        log.info("Validating customer: identifier={}, type={}", 
                request.getPayload().getIdentifier(), 
                request.getPayload().getIdentifierType());

        String identifier = request.getPayload().getIdentifier();
        String identifierType = request.getPayload().getIdentifierType();

        // Look up member in main system
        MemberInfo memberInfo = memberLookupService.findMember(identifier, identifierType);

        if (memberInfo == null || memberInfo.getMemberId() == null) {
            log.warn("Customer not found: identifier={}", identifier);
            return ValidationResponse.builder()
                    .statusCode("ACCOUNT_NOT_FOUND")
                    .statusDescription("Customer not found in system")
                    .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();
        }

        // Check if member is active
        if (!"ACTIVE".equalsIgnoreCase(memberInfo.getStatus())) {
            log.warn("Customer not active: memberId={}, status={}", 
                    memberInfo.getMemberId(), memberInfo.getStatus());
            return ValidationResponse.builder()
                    .statusCode("ACCOUNT_INACTIVE")
                    .statusDescription("Customer account is not active")
                    .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();
        }

        log.info("Customer validated successfully: memberId={}, name={}", 
                memberInfo.getMemberId(), memberInfo.getMemberName());

        return ValidationResponse.builder()
                .statusCode("ACCOUNT_FOUND")
                .statusDescription("ACCOUNT IS VALID")
                .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .payload(ValidationResponse.ValidationResponsePayload.builder()
                        .identifier(identifier)
                        .identifierType(identifierType)
                        .customerId(memberInfo.getMemberId().toString())
                        .customerName(memberInfo.getMemberName())
                        .build())
                .build();
    }

    /**
     * Process payment notification from Family Bank (IPN)
     */
    @Transactional
    public PaymentNotificationResponse processPaymentNotification(PaymentNotificationRequest request) {
        var payload = request.getPayload();
        String txnReference = payload.getTxnReference();
        
        log.info("Processing payment notification: txnRef={}, amount={}, customerId={}", 
                txnReference, payload.getTxnAmount(), payload.getCustomerId());

        // Check for duplicate (idempotency)
        if (idempotencyEnabled && isDuplicatePayment(txnReference)) {
            log.warn("Duplicate payment notification detected: txnRef={}", txnReference);
            PaymentTransaction existing = paymentTransactionRepository.findByTxnReference(txnReference)
                    .orElse(null);
            return PaymentNotificationResponse.builder()
                    .statusCode("PAYMENT_ACK")
                    .statusDescription("Payment already processed")
                    .paymentRef(existing != null ? existing.getPaymentRef() : null)
                    .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();
        }

        // Generate our payment reference
        String paymentRef = generatePaymentRef();

        try {
            // Look up member by customer_id (which is our member UUID)
            MemberInfo memberInfo = memberLookupService.findMemberById(
                    UUID.fromString(payload.getCustomerId()));

            if (memberInfo == null) {
                log.error("Member not found for payment: customerId={}", payload.getCustomerId());
                return PaymentNotificationResponse.builder()
                        .statusCode("PAYMENT_FAILED")
                        .statusDescription("Member not found")
                        .paymentRef(paymentRef)
                        .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                        .build();
            }

            // Create payment transaction record
            PaymentTransaction transaction = createPaymentTransaction(payload, memberInfo, paymentRef);
            
            // Allocate payment (contribution first, then loan)
            AllocationResult allocationResult = allocatePayment(transaction, memberInfo);
            
            // Update transaction with allocation details
            transaction.setAmountToContribution(allocationResult.getAllocatedToContribution());
            transaction.setAmountToLoan(allocationResult.getAllocatedToLoan());
            transaction.setAmountUnallocated(allocationResult.getUnallocatedAmount());
            transaction.setContributionId(allocationResult.getContributionId());
            transaction.setLoanId(allocationResult.getLoanId());
            transaction.setStatus(PaymentStatus.ALLOCATED);
            transaction.setProcessedAt(Instant.now());
            
            paymentTransactionRepository.save(transaction);

            // Publish events for main app to process
            publishAllocationEvents(transaction, allocationResult);

            // Mark as processed for idempotency
            markPaymentProcessed(txnReference);

            log.info("Payment processed successfully: txnRef={}, paymentRef={}, allocated={}", 
                    txnReference, paymentRef, allocationResult);

            return PaymentNotificationResponse.builder()
                    .statusCode("PAYMENT_ACK")
                    .statusDescription("Payment Transaction Received Successfully")
                    .paymentRef(paymentRef)
                    .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();

        } catch (Exception e) {
            log.error("Failed to process payment: txnRef={}, error={}", txnReference, e.getMessage(), e);
            
            // Still save the transaction for retry
            try {
                PaymentTransaction failedTxn = PaymentTransaction.builder()
                        .txnReference(txnReference)
                        .collectionAccount(payload.getCollectionAccount())
                        .payerIdentifier(payload.getCustomerId())
                        .payerIdentifierType(IdentifierType.ACCOUNT_NUMBER)
                        .payerName(payload.getPayerName())
                        .payerPhone(payload.getPayerPhone())
                        .txnAmount(payload.getTxnAmount())
                        .paymentMode(PaymentMode.valueOf(payload.getPaymentMode()))
                        .txnNarration(payload.getTxnNarration())
                        .txnDateTime(parseDateTime(payload.getDateTime()))
                        .status(PaymentStatus.FAILED)
                        .statusDescription(e.getMessage())
                        .paymentRef(paymentRef)
                        .build();
                paymentTransactionRepository.save(failedTxn);
            } catch (Exception saveEx) {
                log.error("Failed to save failed transaction: {}", saveEx.getMessage());
            }

            return PaymentNotificationResponse.builder()
                    .statusCode("PAYMENT_FAILED")
                    .statusDescription("Payment processing failed: " + e.getMessage())
                    .paymentRef(paymentRef)
                    .dateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();
        }
    }

    /**
     * Allocate payment to contribution first, then to loan
     * This is the key business logic
     */
    private AllocationResult allocatePayment(PaymentTransaction transaction, MemberInfo memberInfo) {
        BigDecimal remainingAmount = transaction.getTxnAmount();
        BigDecimal allocatedToContribution = BigDecimal.ZERO;
        BigDecimal allocatedToLoan = BigDecimal.ZERO;
        UUID contributionId = null;
        UUID loanId = null;
        int allocationOrder = 0;

        log.info("Starting payment allocation: amount={}, outstandingContribution={}, outstandingLoan={}", 
                remainingAmount, memberInfo.getOutstandingContribution(), memberInfo.getOutstandingLoanBalance());

        // Step 1: Allocate to contribution first (highest priority)
        if (memberInfo.getOutstandingContribution() != null && 
            memberInfo.getOutstandingContribution().compareTo(BigDecimal.ZERO) > 0 &&
            memberInfo.getCurrentContributionId() != null) {
            
            BigDecimal contributionDue = memberInfo.getOutstandingContribution();
            BigDecimal toContribution = remainingAmount.min(contributionDue);
            
            if (toContribution.compareTo(BigDecimal.ZERO) > 0) {
                allocatedToContribution = toContribution;
                remainingAmount = remainingAmount.subtract(toContribution);
                contributionId = memberInfo.getCurrentContributionId();
                
                // Create allocation record
                PaymentAllocation contributionAllocation = PaymentAllocation.builder()
                        .paymentTransaction(transaction)
                        .allocationType(AllocationType.CONTRIBUTION)
                        .allocationOrder(++allocationOrder)
                        .targetId(contributionId)
                        .targetType("CONTRIBUTION")
                        .allocatedAmount(toContribution)
                        .status(AllocationStatus.PENDING)
                        .createdAt(Instant.now())
                        .build();
                transaction.getAllocations().add(contributionAllocation);
                
                log.info("Allocated {} to contribution: contributionId={}", toContribution, contributionId);
            }
        }

        // Step 2: Allocate remaining to loan repayment
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0 &&
            memberInfo.getOutstandingLoanBalance() != null &&
            memberInfo.getOutstandingLoanBalance().compareTo(BigDecimal.ZERO) > 0 &&
            memberInfo.getActiveLoanId() != null) {
            
            BigDecimal loanDue = memberInfo.getOutstandingLoanBalance();
            BigDecimal toLoan = remainingAmount.min(loanDue);
            
            if (toLoan.compareTo(BigDecimal.ZERO) > 0) {
                allocatedToLoan = toLoan;
                remainingAmount = remainingAmount.subtract(toLoan);
                loanId = memberInfo.getActiveLoanId();
                
                // Create allocation record
                PaymentAllocation loanAllocation = PaymentAllocation.builder()
                        .paymentTransaction(transaction)
                        .allocationType(AllocationType.LOAN_REPAYMENT)
                        .allocationOrder(++allocationOrder)
                        .targetId(loanId)
                        .targetType("LOAN")
                        .allocatedAmount(toLoan)
                        .status(AllocationStatus.PENDING)
                        .createdAt(Instant.now())
                        .build();
                transaction.getAllocations().add(loanAllocation);
                
                log.info("Allocated {} to loan repayment: loanId={}", toLoan, loanId);
            }
        }

        // Step 3: Handle any remaining amount (overpayment)
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Unallocated amount (overpayment): {}", remainingAmount);
            
            PaymentAllocation overpaymentAllocation = PaymentAllocation.builder()
                    .paymentTransaction(transaction)
                    .allocationType(AllocationType.OVERPAYMENT)
                    .allocationOrder(++allocationOrder)
                    .allocatedAmount(remainingAmount)
                    .status(AllocationStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
            transaction.getAllocations().add(overpaymentAllocation);
        }

        return AllocationResult.builder()
                .paymentTransactionId(transaction.getId())
                .totalAmount(transaction.getTxnAmount())
                .allocatedToContribution(allocatedToContribution)
                .allocatedToLoan(allocatedToLoan)
                .unallocatedAmount(remainingAmount)
                .contributionId(contributionId)
                .loanId(loanId)
                .status("ALLOCATED")
                .message("Payment allocated successfully")
                .build();
    }

    /**
     * Publish events to main app for processing allocations
     */
    private void publishAllocationEvents(PaymentTransaction transaction, AllocationResult result) {
        // Publish contribution payment event
        if (result.getAllocatedToContribution().compareTo(BigDecimal.ZERO) > 0 
                && result.getContributionId() != null) {
            ContributionPaymentEvent contributionEvent = ContributionPaymentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("CONTRIBUTION_PAYMENT")
                    .contributionId(result.getContributionId())
                    .memberId(transaction.getMemberId())
                    .memberName(transaction.getMemberName())
                    .groupId(transaction.getGroupId())
                    .amount(result.getAllocatedToContribution())
                    .paymentReference(transaction.getTxnReference())
                    .paymentMode(transaction.getPaymentMode().name())
                    .timestamp(Instant.now())
                    .build();
            
            kafkaTemplate.send(contributionEventsTopic, result.getContributionId().toString(), contributionEvent);
            log.info("Published contribution payment event: contributionId={}, amount={}", 
                    result.getContributionId(), result.getAllocatedToContribution());
        }

        // Publish loan repayment event
        if (result.getAllocatedToLoan().compareTo(BigDecimal.ZERO) > 0 
                && result.getLoanId() != null) {
            LoanRepaymentEvent loanEvent = LoanRepaymentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("LOAN_REPAYMENT")
                    .loanId(result.getLoanId())
                    .memberId(transaction.getMemberId())
                    .memberName(transaction.getMemberName())
                    .groupId(transaction.getGroupId())
                    .amount(result.getAllocatedToLoan())
                    .paymentReference(transaction.getTxnReference())
                    .paymentMode(transaction.getPaymentMode().name())
                    .timestamp(Instant.now())
                    .build();
            
            kafkaTemplate.send(loanEventsTopic, result.getLoanId().toString(), loanEvent);
            log.info("Published loan repayment event: loanId={}, amount={}", 
                    result.getLoanId(), result.getAllocatedToLoan());
        }

        // Publish overall payment received event
        PaymentReceivedEvent paymentEvent = PaymentReceivedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_ALLOCATED")
                .paymentTransactionId(transaction.getId())
                .txnReference(transaction.getTxnReference())
                .amount(transaction.getTxnAmount())
                .paymentMode(transaction.getPaymentMode().name())
                .memberId(transaction.getMemberId())
                .memberName(transaction.getMemberName())
                .groupId(transaction.getGroupId())
                .allocatedToContribution(result.getAllocatedToContribution())
                .allocatedToLoan(result.getAllocatedToLoan())
                .unallocatedAmount(result.getUnallocatedAmount())
                .contributionId(result.getContributionId())
                .loanId(result.getLoanId())
                .timestamp(Instant.now())
                .build();
        
        kafkaTemplate.send(paymentEventsTopic, transaction.getId().toString(), paymentEvent);
    }

    private PaymentTransaction createPaymentTransaction(
            PaymentNotificationRequest.PaymentNotificationPayload payload,
            MemberInfo memberInfo,
            String paymentRef) {
        
        return PaymentTransaction.builder()
                .txnReference(payload.getTxnReference())
                .collectionAccount(payload.getCollectionAccount())
                .payerIdentifier(payload.getCustomerId())
                .payerIdentifierType(IdentifierType.ACCOUNT_NUMBER)
                .payerName(payload.getPayerName())
                .payerPhone(payload.getPayerPhone())
                .memberId(memberInfo.getMemberId())
                .memberName(memberInfo.getMemberName())
                .groupId(memberInfo.getGroupId())
                .txnAmount(payload.getTxnAmount())
                .paymentMode(PaymentMode.valueOf(payload.getPaymentMode().toUpperCase()))
                .txnNarration(payload.getTxnNarration())
                .txnDateTime(parseDateTime(payload.getDateTime()))
                .status(PaymentStatus.PROCESSING)
                .paymentRef(paymentRef)
                .build();
    }

    private String generatePaymentRef() {
        return "TB" + System.currentTimeMillis() + String.format("%04d", (int)(Math.random() * 10000));
    }

    private boolean isDuplicatePayment(String txnReference) {
        String key = "payment:txn:" + txnReference;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key)) 
                || paymentTransactionRepository.existsByTxnReference(txnReference);
    }

    private void markPaymentProcessed(String txnReference) {
        String key = "payment:txn:" + txnReference;
        redisTemplate.opsForValue().set(key, "processed", Duration.ofHours(idempotencyTtlHours));
    }

    private Instant parseDateTime(String dateTime) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
            return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse dateTime: {}, using current time", dateTime);
            return Instant.now();
        }
    }

    /**
     * Get payment status
     */
    public PaymentStatusResponse getPaymentStatus(UUID paymentId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        return PaymentStatusResponse.builder()
                .paymentId(transaction.getId())
                .txnReference(transaction.getTxnReference())
                .status(transaction.getStatus().name())
                .statusDescription(transaction.getStatusDescription())
                .amount(transaction.getTxnAmount())
                .allocations(transaction.getAllocations().stream()
                        .map(a -> PaymentStatusResponse.AllocationInfo.builder()
                                .type(a.getAllocationType().name())
                                .amount(a.getAllocatedAmount())
                                .targetId(a.getTargetId())
                                .status(a.getStatus().name())
                                .build())
                        .toList())
                .build();
    }
}
