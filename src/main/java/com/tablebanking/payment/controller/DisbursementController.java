package com.tablebanking.payment.controller;

import com.tablebanking.payment.dto.DisbursementRequest;
import com.tablebanking.payment.dto.DisbursementResult;
import com.tablebanking.payment.entity.Disbursement;
import com.tablebanking.payment.service.DisbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for disbursement endpoints
 * These are called by our system to initiate payments
 */
@RestController
@RequestMapping("/api/v1/disbursements")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Disbursements", description = "Disbursement (Mass Payments) endpoints")
public class DisbursementController {

    private final DisbursementService disbursementService;
    private final com.tablebanking.payment.repository.DisbursementRepository disbursementRepository;
    private final com.tablebanking.payment.repository.DisbursementBatchRepository batchRepository;

    /**
     * Initiate a single disbursement
     */
    @PostMapping
    @Operation(summary = "Initiate a disbursement")
    public ResponseEntity<DisbursementResult> initiateDisbursement(@RequestBody DisbursementRequest request) {
        log.info("Disbursement request: memberId={}, amount={}", request.getMemberId(), request.getAmount());
        DisbursementResult result = disbursementService.initiateDisbursement(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Initiate bulk disbursement
     */
    @PostMapping("/bulk")
    @Operation(summary = "Initiate bulk disbursement")
    public ResponseEntity<List<DisbursementResult>> initiateBulkDisbursement(
            @RequestBody List<DisbursementRequest> requests) {
        log.info("Bulk disbursement request: count={}", requests.size());
        List<DisbursementResult> results = disbursementService.initiateBulkDisbursement(requests);
        return ResponseEntity.ok(results);
    }

    /**
     * Get disbursement by ID
     */
    @GetMapping("/{disbursementId}")
    @Operation(summary = "Get disbursement by ID")
    public ResponseEntity<com.tablebanking.payment.entity.Disbursement> getDisbursement(
            @PathVariable UUID disbursementId) {
        return disbursementRepository.findById(disbursementId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get disbursement by payment reference
     */
    @GetMapping("/reference/{paymentRef}")
    @Operation(summary = "Get disbursement by payment reference")
    public ResponseEntity<com.tablebanking.payment.entity.Disbursement> getDisbursementByRef(
            @PathVariable String paymentRef) {
        return disbursementRepository.findByPaymentRef(paymentRef)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get batch by ID
     */
    @GetMapping("/batches/{batchId}")
    @Operation(summary = "Get batch by ID")
    public ResponseEntity<com.tablebanking.payment.entity.DisbursementBatch> getBatch(
            @PathVariable UUID batchId) {
        return batchRepository.findByIdWithDisbursements(batchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get batch by reference
     */
    @GetMapping("/batches/reference/{batchRef}")
    @Operation(summary = "Get batch by reference")
    public ResponseEntity<com.tablebanking.payment.entity.DisbursementBatch> getBatchByRef(
            @PathVariable String batchRef) {
        return batchRepository.findByBatchRef(batchRef)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Refresh batch status from Family Bank
     */
    @PostMapping("/batches/{batchRef}/refresh")
    @Operation(summary = "Refresh batch status from Family Bank")
    public ResponseEntity<Void> refreshBatchStatus(@PathVariable String batchRef) {
        disbursementService.updateBatchStatus(batchRef);
        return ResponseEntity.ok().build();
    }

    /**
     * Get disbursements for a member
     */
    @GetMapping("/member/{memberId}")
    @Operation(summary = "Get disbursements for a member")
    public ResponseEntity<Page<Disbursement>> getMemberDisbursements(
            @PathVariable UUID memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                disbursementRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
        );
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    @Operation(summary = "Disbursement service health check")
    public ResponseEntity<Map<String, Object>> health() {
        long pendingBatches = batchRepository.findPendingBatches().size();
        long pendingDisbursements = disbursementRepository.findPendingDisbursements().size();

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Disbursements",
                "pendingBatches", pendingBatches,
                "pendingDisbursements", pendingDisbursements
        ));
    }
}
