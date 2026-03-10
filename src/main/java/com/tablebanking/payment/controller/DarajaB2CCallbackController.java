package com.tablebanking.payment.controller;

import com.tablebanking.payment.entity.Disbursement;
import com.tablebanking.payment.entity.enums.DisbursementStatus;
import com.tablebanking.payment.event.DisbursementEvent;
import com.tablebanking.payment.repository.DisbursementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Safaricom Daraja B2C result and timeout callbacks.
 * These endpoints are called by Safaricom and must be publicly accessible (no auth).
 */
@RestController
@RequestMapping("/api/v1/daraja/b2c")
@RequiredArgsConstructor
@Slf4j
public class DarajaB2CCallbackController {

    private final DisbursementRepository disbursementRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.disbursement-events:disbursement-events}")
    private String disbursementEventsTopic;

    /**
     * Safaricom B2C result callback.
     */
    @PostMapping("/result")
    @Transactional
    public ResponseEntity<Map<String, String>> handleB2CResult(@RequestBody Map<String, Object> payload) {
        log.info("Received Daraja B2C result callback");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            if (result == null) {
                log.warn("B2C callback missing Result field");
                return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Success"));
            }

            int resultCode = ((Number) result.getOrDefault("ResultCode", -1)).intValue();
            String resultDesc = (String) result.getOrDefault("ResultDesc", "");
            String originatorConversationId = (String) result.get("OriginatorConversationID");
            String transactionId = (String) result.get("TransactionID");

            log.info("B2C result: code={}, desc={}, ocId={}, txnId={}",
                    resultCode, resultDesc, originatorConversationId, transactionId);

            if (originatorConversationId == null) {
                log.warn("B2C callback missing OriginatorConversationID");
                return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Success"));
            }

            // Find disbursement by paymentRef (set as OriginatorConversationID during B2C request)
            disbursementRepository.findByPaymentRef(originatorConversationId).ifPresentOrElse(
                    disbursement -> {
                        if (resultCode == 0) {
                            disbursement.setStatus(DisbursementStatus.THIRDPARTY_COMPLETED);
                            disbursement.setExternalRef(transactionId);
                            disbursement.setStatusDescription(resultDesc);
                            disbursement.setCompletedAt(Instant.now());
                            disbursementRepository.save(disbursement);

                            publishDisbursementEvent(disbursement, "DISBURSEMENT_COMPLETED");
                            log.info("B2C disbursement completed: paymentRef={}, txnId={}",
                                    originatorConversationId, transactionId);
                        } else {
                            disbursement.setStatus(DisbursementStatus.THIRDPARTY_FAILED);
                            disbursement.setStatusDescription(resultDesc);
                            disbursementRepository.save(disbursement);

                            publishDisbursementEvent(disbursement, "DISBURSEMENT_FAILED");
                            log.warn("B2C disbursement failed: paymentRef={}, code={}, desc={}",
                                    originatorConversationId, resultCode, resultDesc);
                        }
                    },
                    () -> log.warn("No disbursement found for OriginatorConversationID: {}", originatorConversationId)
            );
        } catch (Exception e) {
            log.error("Error processing B2C result callback: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Success"));
    }

    /**
     * Safaricom B2C queue timeout callback.
     */
    @PostMapping("/timeout")
    public ResponseEntity<Map<String, String>> handleB2CTimeout(@RequestBody Map<String, Object> payload) {
        log.warn("Received Daraja B2C timeout callback: {}", payload);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            if (result != null) {
                String originatorConversationId = (String) result.get("OriginatorConversationID");
                if (originatorConversationId != null) {
                    disbursementRepository.findByPaymentRef(originatorConversationId).ifPresent(disbursement -> {
                        disbursement.setStatus(DisbursementStatus.THIRDPARTY_FAILED);
                        disbursement.setStatusDescription("Queue timeout - payment may still be processing");
                        disbursementRepository.save(disbursement);
                        log.warn("B2C disbursement timed out: paymentRef={}", originatorConversationId);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Error processing B2C timeout callback: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Success"));
    }

    private void publishDisbursementEvent(Disbursement disbursement, String eventType) {
        try {
            DisbursementEvent event = DisbursementEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .disbursementId(disbursement.getId())
                    .batchRef(disbursement.getBatchRef())
                    .paymentRef(disbursement.getPaymentRef())
                    .externalRef(disbursement.getExternalRef())
                    .memberId(disbursement.getMemberId())
                    .groupId(disbursement.getGroupId())
                    .sourceType(disbursement.getSourceType().name())
                    .sourceId(disbursement.getSourceId())
                    .amount(disbursement.getAmount())
                    .currency(disbursement.getCurrency())
                    .status(disbursement.getStatus().name())
                    .statusDescription(disbursement.getStatusDescription())
                    .timestamp(Instant.now())
                    .build();

            kafkaTemplate.send(disbursementEventsTopic, disbursement.getId().toString(), event);
            log.debug("Published {} event for disbursement: {}", eventType, disbursement.getPaymentRef());
        } catch (Exception e) {
            log.error("Failed to publish disbursement event: {}", e.getMessage());
        }
    }
}
