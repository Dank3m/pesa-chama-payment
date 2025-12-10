package com.tablebanking.payment.service;

import com.tablebanking.payment.client.FamilyBankApiClient;
import com.tablebanking.payment.dto.*;
import com.tablebanking.payment.entity.*;
import com.tablebanking.payment.entity.enums.*;
import com.tablebanking.payment.event.DisbursementEvent;
import com.tablebanking.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for disbursing funds via Family Bank Mass Payments API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisbursementService {

    private final DisbursementBatchRepository batchRepository;
    private final DisbursementRepository disbursementRepository;
    private final FamilyBankApiClient familyBankApiClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${familybank.payments.debit-account}")
    private String debitAccount;

    @Value("${familybank.payments.sender-bank}")
    private String senderBank;

    @Value("${familybank.payments.sender-branch}")
    private String senderBranch;

    @Value("${kafka.topics.disbursement-events:disbursement-events}")
    private String disbursementEventsTopic;

    private static final AtomicLong batchSequence = new AtomicLong(System.currentTimeMillis() % 1000000);
    private static final AtomicLong paymentSequence = new AtomicLong(System.currentTimeMillis() % 1000000);

    /**
     * Initiate a single disbursement (e.g., loan disbursement)
     */
    @Transactional
    public DisbursementResult initiateDisbursement(DisbursementRequest request) {
        log.info("Initiating disbursement: memberId={}, amount={}, type={}", 
                request.getMemberId(), request.getAmount(), request.getPaymentType());

        // Generate references
        String batchRef = generateBatchRef();
        String paymentRef = generatePaymentRef();

        // Create batch (single item batch)
        DisbursementBatch batch = DisbursementBatch.builder()
                .batchRef(batchRef)
                .accountDr(debitAccount)
                .narration(request.getRemarks())
                .valueDate(LocalDate.now())
                .currency(request.getCurrency() != null ? request.getCurrency() : "KES")
                .totalAmount(request.getAmount())
                .transactionCount(1)
                .status(BatchStatus.PENDING)
                .build();

        // Create disbursement record
        Disbursement disbursement = Disbursement.builder()
                .batch(batch)
                .paymentRef(paymentRef)
                .batchRef(batchRef)
                .paymentType(DisbursementType.valueOf(request.getPaymentType()))
                .sourceType(DisbursementSourceType.valueOf(request.getSourceType()))
                .sourceId(request.getSourceId())
                .memberId(request.getMemberId())
                .groupId(request.getGroupId())
                .senderAccount(debitAccount)
                .senderBank(senderBank)
                .senderBankBranch(senderBranch)
                .senderDetails(buildSenderDetails(request))
                .beneficiaryAccount(request.getBeneficiaryAccount())
                .beneficiaryBank(request.getBeneficiaryBank())
                .beneficiaryBankBranch(request.getBeneficiaryBankBranch())
                .beneficiaryName(request.getMemberName())
                .beneficiaryPhone(request.getBeneficiaryPhone())
                .beneficiaryDetails(buildBeneficiaryDetails(request))
                .currency(request.getCurrency() != null ? request.getCurrency() : "KES")
                .amount(request.getAmount())
                .remarks(request.getRemarks())
                .purpose(DisbursementPurpose.DirectCredit)
                .status(DisbursementStatus.PENDING)
                .build();

        batch.getDisbursements().add(disbursement);
        batchRepository.save(batch);

        log.info("Disbursement created: batchRef={}, paymentRef={}", batchRef, paymentRef);

        // Submit to Family Bank asynchronously
        submitToFamilyBankAsync(batch);

        return DisbursementResult.builder()
                .disbursementId(disbursement.getId())
                .batchRef(batchRef)
                .paymentRef(paymentRef)
                .status("PENDING")
                .statusDescription("Disbursement initiated")
                .build();
    }

    /**
     * Initiate bulk disbursement (multiple recipients)
     */
    @Transactional
    public List<DisbursementResult> initiateBulkDisbursement(List<DisbursementRequest> requests) {
        log.info("Initiating bulk disbursement: count={}", requests.size());

        if (requests.isEmpty()) {
            return List.of();
        }

        String batchRef = generateBatchRef();
        BigDecimal totalAmount = requests.stream()
                .map(DisbursementRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create batch
        DisbursementBatch batch = DisbursementBatch.builder()
                .batchRef(batchRef)
                .accountDr(debitAccount)
                .narration("Bulk disbursement - " + LocalDate.now())
                .valueDate(LocalDate.now())
                .currency("KES")
                .totalAmount(totalAmount)
                .transactionCount(requests.size())
                .status(BatchStatus.PENDING)
                .build();

        List<DisbursementResult> results = new ArrayList<>();

        for (DisbursementRequest request : requests) {
            String paymentRef = generatePaymentRef();

            Disbursement disbursement = Disbursement.builder()
                    .batch(batch)
                    .paymentRef(paymentRef)
                    .batchRef(batchRef)
                    .paymentType(DisbursementType.valueOf(request.getPaymentType()))
                    .sourceType(DisbursementSourceType.valueOf(request.getSourceType()))
                    .sourceId(request.getSourceId())
                    .memberId(request.getMemberId())
                    .groupId(request.getGroupId())
                    .senderAccount(debitAccount)
                    .senderBank(senderBank)
                    .senderBankBranch(senderBranch)
                    .senderDetails(buildSenderDetails(request))
                    .beneficiaryAccount(request.getBeneficiaryAccount())
                    .beneficiaryBank(request.getBeneficiaryBank())
                    .beneficiaryBankBranch(request.getBeneficiaryBankBranch())
                    .beneficiaryName(request.getMemberName())
                    .beneficiaryPhone(request.getBeneficiaryPhone())
                    .beneficiaryDetails(buildBeneficiaryDetails(request))
                    .currency(request.getCurrency() != null ? request.getCurrency() : "KES")
                    .amount(request.getAmount())
                    .remarks(request.getRemarks())
                    .purpose(DisbursementPurpose.DirectCredit)
                    .status(DisbursementStatus.PENDING)
                    .build();

            batch.getDisbursements().add(disbursement);

            results.add(DisbursementResult.builder()
                    .disbursementId(disbursement.getId())
                    .batchRef(batchRef)
                    .paymentRef(paymentRef)
                    .status("PENDING")
                    .statusDescription("Disbursement initiated")
                    .build());
        }

        batchRepository.save(batch);

        log.info("Bulk disbursement created: batchRef={}, count={}, totalAmount={}", 
                batchRef, requests.size(), totalAmount);

        // Submit to Family Bank asynchronously
        submitToFamilyBankAsync(batch);

        return results;
    }

    /**
     * Submit batch to Family Bank API asynchronously
     */
    @Async
    public CompletableFuture<Void> submitToFamilyBankAsync(DisbursementBatch batch) {
        try {
            submitToFamilyBank(batch);
        } catch (Exception e) {
            log.error("Failed to submit batch to Family Bank: batchRef={}, error={}", 
                    batch.getBatchRef(), e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Submit batch to Family Bank API
     */
    @Transactional
    public void submitToFamilyBank(DisbursementBatch batch) {
        log.info("Submitting batch to Family Bank: batchRef={}", batch.getBatchRef());

        try {
            // Build request
            BulkPaymentRequest request = buildBulkPaymentRequest(batch);

            // Update status to queued
            batch.setStatus(BatchStatus.QUEUED);
            batch.setSubmittedAt(Instant.now());
            batchRepository.save(batch);

            // Call Family Bank API
            BulkPaymentResponse response = familyBankApiClient.submitBulkPayment(request);

            // Update batch with response
            if (response != null) {
                batch.setCbsRef(response.getCbsref());
                batch.setStatus(mapBatchStatus(response.getStatus()));
                batch.setStatusDescription(response.getStatusdescription());

                // Update individual disbursements
                if (response.getDtl() != null) {
                    for (BulkPaymentResponse.DisbursementDetailResponse dtlResponse : response.getDtl()) {
                        updateDisbursementFromResponse(batch, dtlResponse);
                    }
                }

                batchRepository.save(batch);

                // Publish events
                publishDisbursementEvents(batch);

                log.info("Batch submitted successfully: batchRef={}, cbsRef={}, status={}", 
                        batch.getBatchRef(), response.getCbsref(), response.getStatus());
            }

        } catch (Exception e) {
            log.error("Failed to submit batch: batchRef={}, error={}", batch.getBatchRef(), e.getMessage());
            batch.setStatus(BatchStatus.FAILED);
            batch.setStatusDescription(e.getMessage());
            batchRepository.save(batch);

            // Publish failure events
            publishDisbursementFailureEvents(batch, e.getMessage());
        }
    }

    /**
     * Query and update batch status from Family Bank
     */
    @Transactional
    public void updateBatchStatus(String batchRef) {
        log.info("Updating batch status: batchRef={}", batchRef);

        DisbursementBatch batch = batchRepository.findByBatchRef(batchRef)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchRef));

        try {
            BulkPaymentResponse response = familyBankApiClient.queryBulkPaymentStatus(batchRef);

            if (response != null) {
                batch.setStatus(mapBatchStatus(response.getStatus()));
                batch.setStatusDescription(response.getStatusdescription());

                if (response.getDtl() != null) {
                    for (BulkPaymentResponse.DisbursementDetailResponse dtlResponse : response.getDtl()) {
                        updateDisbursementFromResponse(batch, dtlResponse);
                    }
                }

                if (batch.getStatus() == BatchStatus.CBS_COMPLETED || 
                    batch.getStatus() == BatchStatus.CBS_FAILED) {
                    batch.setCompletedAt(Instant.now());
                }

                batchRepository.save(batch);

                log.info("Batch status updated: batchRef={}, status={}", batchRef, batch.getStatus());
            }

        } catch (Exception e) {
            log.error("Failed to update batch status: batchRef={}, error={}", batchRef, e.getMessage());
        }
    }

    private void updateDisbursementFromResponse(DisbursementBatch batch, 
                                                 BulkPaymentResponse.DisbursementDetailResponse response) {
        disbursementRepository.findByPaymentRef(response.getPaymentref())
                .ifPresent(disbursement -> {
                    disbursement.setExternalRef(response.getExternalref());
                    disbursement.setCbsRef(response.getCbsref());
                    disbursement.setStatus(mapDisbursementStatus(response.getStatus()));
                    disbursement.setStatusDescription(response.getStatusdescription());
                    
                    if (disbursement.getStatus() == DisbursementStatus.CBS_COMPLETED ||
                        disbursement.getStatus() == DisbursementStatus.THIRDPARTY_COMPLETED) {
                        disbursement.setCompletedAt(Instant.now());
                    }
                    
                    disbursementRepository.save(disbursement);
                });
    }

    private BulkPaymentRequest buildBulkPaymentRequest(DisbursementBatch batch) {
        List<BulkPaymentRequest.DisbursementDetail> details = batch.getDisbursements().stream()
                .map(this::buildDisbursementDetail)
                .toList();

        return BulkPaymentRequest.builder()
                .batchref(batch.getBatchRef())
                .accountdr(batch.getAccountDr())
                .narration(batch.getNarration())
                .valuedate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .currency(batch.getCurrency())
                .totalamount(batch.getTotalAmount())
                .dtl(details)
                .build();
    }

    private BulkPaymentRequest.DisbursementDetail buildDisbursementDetail(Disbursement disbursement) {
        return BulkPaymentRequest.DisbursementDetail.builder()
                .batchref(disbursement.getBatchRef())
                .paymentref(disbursement.getPaymentRef())
                .paymenttype(disbursement.getPaymentType().name())
                .senderaccount(disbursement.getSenderAccount())
                .senderbank(disbursement.getSenderBank())
                .senderbankbranch(disbursement.getSenderBankBranch())
                .senderdetails(disbursement.getSenderDetails())
                .beneficiaryaccount(disbursement.getBeneficiaryAccount())
                .beneficiarybank(disbursement.getBeneficiaryBank())
                .beneficiarybankbranch(disbursement.getBeneficiaryBankBranch())
                .beneficiarydetails(disbursement.getBeneficiaryDetails())
                .remarks(disbursement.getRemarks())
                .purpose(disbursement.getPurpose().name())
                .currency(disbursement.getCurrency())
                .paymentamount(disbursement.getAmount())
                .build();
    }

    private void publishDisbursementEvents(DisbursementBatch batch) {
        for (Disbursement disbursement : batch.getDisbursements()) {
            DisbursementEvent event = DisbursementEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("DISBURSEMENT_COMPLETED")
                    .disbursementId(disbursement.getId())
                    .batchRef(batch.getBatchRef())
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
        }
    }

    private void publishDisbursementFailureEvents(DisbursementBatch batch, String errorMessage) {
        for (Disbursement disbursement : batch.getDisbursements()) {
            DisbursementEvent event = DisbursementEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("DISBURSEMENT_FAILED")
                    .disbursementId(disbursement.getId())
                    .batchRef(batch.getBatchRef())
                    .paymentRef(disbursement.getPaymentRef())
                    .memberId(disbursement.getMemberId())
                    .groupId(disbursement.getGroupId())
                    .sourceType(disbursement.getSourceType().name())
                    .sourceId(disbursement.getSourceId())
                    .amount(disbursement.getAmount())
                    .currency(disbursement.getCurrency())
                    .status("FAILED")
                    .statusDescription(errorMessage)
                    .timestamp(Instant.now())
                    .build();

            kafkaTemplate.send(disbursementEventsTopic, disbursement.getId().toString(), event);
        }
    }

    private String buildSenderDetails(DisbursementRequest request) {
        return String.format("%s|%s|%s", 
                request.getGroupName() != null ? request.getGroupName() : "TABLE BANKING",
                "HEAD OFFICE",
                "support@tablebanking.com");
    }

    private String buildBeneficiaryDetails(DisbursementRequest request) {
        return String.format("%s|%s", 
                request.getMemberName(),
                request.getBeneficiaryPhone() != null ? request.getBeneficiaryPhone() : "");
    }

    private String generateBatchRef() {
        return "BRF" + String.format("%012d", batchSequence.incrementAndGet());
    }

    private String generatePaymentRef() {
        return "PRF" + String.format("%012d", paymentSequence.incrementAndGet());
    }

    private BatchStatus mapBatchStatus(String status) {
        if (status == null) return BatchStatus.PENDING;
        return switch (status.toUpperCase()) {
            case "PENDING" -> BatchStatus.PENDING;
            case "QUEUED" -> BatchStatus.QUEUED;
            case "FAILED" -> BatchStatus.FAILED;
            case "CBS_POSTED" -> BatchStatus.CBS_POSTED;
            case "CBS_ACK" -> BatchStatus.CBS_ACK;
            case "CBS_COMPLETED" -> BatchStatus.CBS_COMPLETED;
            case "CBS_FAILED" -> BatchStatus.CBS_FAILED;
            default -> BatchStatus.PENDING;
        };
    }

    private DisbursementStatus mapDisbursementStatus(String status) {
        if (status == null) return DisbursementStatus.PENDING;
        return switch (status.toUpperCase()) {
            case "PENDING" -> DisbursementStatus.PENDING;
            case "QUEUED" -> DisbursementStatus.QUEUED;
            case "FAILED" -> DisbursementStatus.FAILED;
            case "CBS_POSTED" -> DisbursementStatus.CBS_POSTED;
            case "CBS_ACK" -> DisbursementStatus.CBS_ACK;
            case "CBS_COMPLETED" -> DisbursementStatus.CBS_COMPLETED;
            case "CBS_FAILED" -> DisbursementStatus.CBS_FAILED;
            case "THIRDPARTY_POSTED" -> DisbursementStatus.THIRDPARTY_POSTED;
            case "THIRDPARTY_ACK" -> DisbursementStatus.THIRDPARTY_ACK;
            case "THIRDPARTY_COMPLETED" -> DisbursementStatus.THIRDPARTY_COMPLETED;
            case "THIRDPARTY_FAILED" -> DisbursementStatus.THIRDPARTY_FAILED;
            default -> DisbursementStatus.PENDING;
        };
    }
}
