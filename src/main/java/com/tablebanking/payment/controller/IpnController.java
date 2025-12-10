package com.tablebanking.payment.controller;

import com.tablebanking.payment.dto.*;
import com.tablebanking.payment.entity.PaymentTransaction;
import com.tablebanking.payment.entity.enums.PaymentStatus;
import com.tablebanking.payment.repository.PaymentTransactionRepository;
import com.tablebanking.payment.service.PaymentProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for Family Bank IPN (Collections) endpoints
 * These endpoints are called by Family Bank
 */
@RestController
@RequestMapping("/api/v1/ipn")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "IPN", description = "Family Bank IPN (Collections) endpoints")
public class IpnController {

    private final PaymentProcessingService paymentProcessingService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * Customer validation endpoint - Family Bank calls this to validate customer
     * before accepting payment
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate customer for payment")
    public ResponseEntity<ValidationResponse> validateCustomer(@RequestBody ValidationRequest request) {
        log.info("Received validation request: action={}", request.getAction());

        if (!"VALIDATION".equals(request.getAction())) {
            return ResponseEntity.badRequest().body(
                    ValidationResponse.builder()
                            .statusCode("INVALID_ACTION")
                            .statusDescription("Invalid action type")
                            .build()
            );
        }

        ValidationResponse response = paymentProcessingService.validateCustomer(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Payment notification endpoint - Family Bank calls this when payment is received
     * This is the main IPN callback
     */
    @PostMapping("/payment-notification")
    @Operation(summary = "Receive payment notification from Family Bank")
    public ResponseEntity<PaymentNotificationResponse> receivePaymentNotification(
            @RequestBody PaymentNotificationRequest request) {
        log.info("Received payment notification: action={}, txnRef={}",
                request.getAction(),
                request.getPayload() != null ? request.getPayload().getTxnReference() : null);

        if (!"PAYMENT_NOTIFICATION".equals(request.getAction())) {
            return ResponseEntity.badRequest().body(
                    PaymentNotificationResponse.builder()
                            .statusCode("INVALID_ACTION")
                            .statusDescription("Invalid action type")
                            .build()
            );
        }

        PaymentNotificationResponse response = paymentProcessingService.processPaymentNotification(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get payment status
     */
    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Get payment status by ID")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentProcessingService.getPaymentStatus(paymentId));
    }

    /**
     * Get payment by transaction reference
     */
    @GetMapping("/payments/reference/{txnReference}")
    @Operation(summary = "Get payment by bank transaction reference")
    public ResponseEntity<PaymentTransaction> getPaymentByReference(@PathVariable String txnReference) {
        return paymentTransactionRepository.findByTxnReference(txnReference)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get payments for a member
     */
    @GetMapping("/payments/member/{memberId}")
    @Operation(summary = "Get payments for a member")
    public ResponseEntity<Page<PaymentTransaction>> getMemberPayments(
            @PathVariable UUID memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                paymentTransactionRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
        );
    }

    /**
     * Health check for IPN endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "IPN health check")
    public ResponseEntity<Map<String, Object>> health() {
        long pendingCount = paymentTransactionRepository.countByStatus(
                PaymentStatus.RECEIVED);

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "IPN",
                "pendingPayments", pendingCount
        ));
    }
}