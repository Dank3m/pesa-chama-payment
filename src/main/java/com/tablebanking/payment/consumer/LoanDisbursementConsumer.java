package com.tablebanking.payment.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebanking.payment.dto.DisbursementRequest;
import com.tablebanking.payment.event.LoanDisbursementRequestEvent;
import com.tablebanking.payment.service.DisbursementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for loan disbursement requests from main app
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementConsumer {

    private final DisbursementService disbursementService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for loan disbursement requests
     */
    @KafkaListener(
            topics = "${kafka.topics.disbursement-events:disbursement-events}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLoanDisbursementRequest(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received disbursement request: key={}, partition={}, offset={}", 
                record.key(), record.partition(), record.offset());
        
        try {
            LoanDisbursementRequestEvent event = objectMapper.readValue(
                    record.value(), LoanDisbursementRequestEvent.class);
            
            // Only process loan disbursement requests
            if (!"LOAN_DISBURSEMENT_REQUEST".equals(event.getEventType())) {
                log.debug("Ignoring non-disbursement-request event: {}", event.getEventType());
                ack.acknowledge();
                return;
            }
            
            log.info("Processing loan disbursement request: loanId={}, memberId={}, amount={}", 
                    event.getLoanId(), event.getMemberId(), event.getAmount());
            
            // Build disbursement request
            DisbursementRequest request = DisbursementRequest.builder()
                    .memberId(event.getMemberId())
                    .memberName(event.getMemberName())
                    .groupId(event.getGroupId())
                    .groupName(event.getGroupName())
                    .sourceType("LOAN_DISBURSEMENT")
                    .sourceId(event.getLoanId())
                    .beneficiaryAccount(determineBeneficiaryAccount(event))
                    .beneficiaryBank(determineBeneficiaryBank(event))
                    .beneficiaryPhone(event.getPhoneNumber())
                    .paymentType(mapDisbursementMethod(event.getDisbursementMethod()))
                    .amount(event.getAmount())
                    .currency("KES")
                    .remarks("Loan disbursement - " + event.getLoanNumber())
                    .purpose("DirectCredit")
                    .build();
            
            // Initiate disbursement
            disbursementService.initiateDisbursement(request);
            
            ack.acknowledge();
            log.info("Successfully processed loan disbursement request: loanId={}", event.getLoanId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse disbursement request: {}", e.getMessage());
            ack.acknowledge(); // Don't retry bad messages
        } catch (Exception e) {
            log.error("Error processing disbursement request: {}", e.getMessage(), e);
            // Don't acknowledge - will be retried
        }
    }

    private String determineBeneficiaryAccount(LoanDisbursementRequestEvent event) {
        // For MPESA, use phone number; for bank transfers, use bank account
        if ("MPESA".equalsIgnoreCase(event.getDisbursementMethod())) {
            return event.getPhoneNumber();
        }
        return event.getBankAccount();
    }

    private String determineBeneficiaryBank(LoanDisbursementRequestEvent event) {
        if ("MPESA".equalsIgnoreCase(event.getDisbursementMethod())) {
            return "SAFARICOM";
        }
        return event.getBankCode();
    }

    private String mapDisbursementMethod(String method) {
        if (method == null) return "MPESA";
        return switch (method.toUpperCase()) {
            case "MPESA", "M-PESA" -> "MPESA";
            case "BANK", "EFT" -> "EFT";
            case "PESALINK" -> "PESALINK";
            default -> "MPESA";
        };
    }
}
