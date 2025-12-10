package com.tablebanking.payment.service;

import com.tablebanking.payment.entity.*;
import com.tablebanking.payment.entity.enums.*;
import com.tablebanking.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled jobs for payment service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentScheduler {

    private final DisbursementService disbursementService;
    private final DisbursementBatchRepository batchRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * Poll pending disbursement batches for status updates
     * Runs every 2 minutes
     */
    @Scheduled(fixedDelayString = "${payment.scheduler.poll-interval:120000}")
    public void pollPendingDisbursements() {
        log.debug("Polling pending disbursement batches...");
        
        List<DisbursementBatch> pendingBatches = batchRepository.findPendingBatches();
        
        if (pendingBatches.isEmpty()) {
            log.debug("No pending batches to poll");
            return;
        }

        log.info("Polling {} pending disbursement batches", pendingBatches.size());

        for (DisbursementBatch batch : pendingBatches) {
            try {
                disbursementService.updateBatchStatus(batch.getBatchRef());
            } catch (Exception e) {
                log.error("Failed to poll batch status: batchRef={}, error={}", 
                        batch.getBatchRef(), e.getMessage());
            }
        }
    }

    /**
     * Retry failed payment processing
     * Runs every 5 minutes
     */
    @Scheduled(fixedDelayString = "${payment.scheduler.retry-interval:300000}")
    public void retryFailedPayments() {
        log.debug("Checking for failed payments to retry...");
        
        List<PaymentTransaction> failedPayments = paymentTransactionRepository
                .findByStatus(PaymentStatus.FAILED);
        
        // Only retry payments that are less than 24 hours old
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        
        long retryCount = failedPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(cutoff))
                .count();

        if (retryCount > 0) {
            log.info("Found {} failed payments eligible for retry", retryCount);
            // TODO: Implement retry logic when needed
        }
    }

    /**
     * Cleanup stale transactions
     * Runs daily at 3 AM
     */
    @Scheduled(cron = "${payment.scheduler.cleanup-cron:0 0 3 * * ?}")
    public void cleanupStaleTransactions() {
        log.info("Running stale transaction cleanup...");
        
        // Mark transactions stuck in PROCESSING for more than 1 hour as FAILED
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        
        List<PaymentTransaction> staleTransactions = paymentTransactionRepository
                .findStaleTransactions(PaymentStatus.PROCESSING, cutoff);
        
        for (PaymentTransaction txn : staleTransactions) {
            txn.setStatus(PaymentStatus.FAILED);
            txn.setStatusDescription("Transaction timed out during processing");
            paymentTransactionRepository.save(txn);
            log.warn("Marked stale transaction as failed: txnRef={}", txn.getTxnReference());
        }
        
        log.info("Cleanup completed. Marked {} stale transactions as failed", staleTransactions.size());
    }

    /**
     * Log daily statistics
     * Runs daily at 6 AM
     */
    @Scheduled(cron = "${payment.scheduler.stats-cron:0 0 6 * * ?}")
    public void logDailyStats() {
        long pendingPayments = paymentTransactionRepository.countByStatus(PaymentStatus.RECEIVED);
        long completedPayments = paymentTransactionRepository.countByStatus(PaymentStatus.COMPLETED);
        long failedPayments = paymentTransactionRepository.countByStatus(PaymentStatus.FAILED);
        
        log.info("Daily Payment Stats - Pending: {}, Completed: {}, Failed: {}", 
                pendingPayments, completedPayments, failedPayments);
    }
}
