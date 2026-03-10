package com.tablebanking.payment.scheduler;

import com.tablebanking.payment.client.DarajaStkClient;
import com.tablebanking.payment.entity.StkPushRequest;
import com.tablebanking.payment.entity.enums.CollectionStatus;
import com.tablebanking.payment.entity.enums.DisbursementStatus;
import com.tablebanking.payment.repository.DisbursementRepository;
import com.tablebanking.payment.repository.StkPushRequestRepository;
import com.tablebanking.payment.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TransactionStatusScheduler {

    private final StkPushRequestRepository stkPushRequestRepository;
    private final DisbursementRepository disbursementRepository;
    private final DarajaStkClient darajaStkClient;
    private final CollectionService collectionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Check STK Push requests that have been in STK_SENT status for more than 2 minutes.
     * Queries Daraja for the current status and updates accordingly.
     */
    @Scheduled(fixedRate = 120000)
    public void checkStkPushStatus() {
        Instant cutoff = Instant.now().minus(2, ChronoUnit.MINUTES);
        List<StkPushRequest> pendingRequests = stkPushRequestRepository
                .findByStatusAndCreatedAtBefore(CollectionStatus.STK_SENT, cutoff);

        if (pendingRequests.isEmpty()) {
            return;
        }

        log.info("Checking status of {} pending STK Push requests", pendingRequests.size());

        for (StkPushRequest request : pendingRequests) {
            try {
                DarajaStkClient.StkQueryResponse queryResponse = darajaStkClient
                        .queryStkPushStatus(request.getCheckoutRequestId());

                if (queryResponse == null || queryResponse.getResultCode() == null) {
                    log.warn("No query response for checkoutRequestId={}", request.getCheckoutRequestId());
                    continue;
                }

                int resultCode;
                try {
                    resultCode = Integer.parseInt(queryResponse.getResultCode());
                } catch (NumberFormatException e) {
                    log.warn("Invalid result code '{}' for checkoutRequestId={}",
                            queryResponse.getResultCode(), request.getCheckoutRequestId());
                    continue;
                }

                if (resultCode == 0) {
                    collectionService.handleStkCallback(
                            request.getCheckoutRequestId(),
                            0,
                            queryResponse.getResultDesc(),
                            null); // Receipt number not available from query
                    log.info("STK Push completed via query: collectionRef={}", request.getCollectionRef());
                } else if (resultCode == 1032) {
                    collectionService.handleStkCallback(
                            request.getCheckoutRequestId(),
                            1032,
                            queryResponse.getResultDesc(),
                            null);
                    log.info("STK Push cancelled via query: collectionRef={}", request.getCollectionRef());
                } else {
                    collectionService.handleStkCallback(
                            request.getCheckoutRequestId(),
                            resultCode,
                            queryResponse.getResultDesc(),
                            null);
                    log.info("STK Push failed via query: collectionRef={}, resultCode={}",
                            request.getCollectionRef(), resultCode);
                }
            } catch (Exception e) {
                log.error("Error querying STK Push status for collectionRef={}: {}",
                        request.getCollectionRef(), e.getMessage());
            }
        }
    }

    /**
     * Check for stuck disbursements.
     * B2C disbursements are async (callback-based), so we can only log warnings for stuck ones.
     */
    @Scheduled(fixedRate = 300000)
    public void checkDisbursementStatus() {
        // Check QUEUED disbursements older than 5 minutes
        Instant queuedCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        var stuckQueued = disbursementRepository.findByStatus(DisbursementStatus.QUEUED)
                .stream()
                .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isBefore(queuedCutoff))
                .toList();

        if (!stuckQueued.isEmpty()) {
            log.warn("Found {} QUEUED disbursements older than 5 minutes", stuckQueued.size());
            stuckQueued.forEach(d -> log.warn("Stuck QUEUED disbursement: id={}, paymentRef={}, createdAt={}",
                    d.getId(), d.getPaymentRef(), d.getCreatedAt()));
        }

        // Check THIRDPARTY_POSTED disbursements older than 10 minutes
        Instant postedCutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        var stuckPosted = disbursementRepository.findByStatus(DisbursementStatus.THIRDPARTY_POSTED)
                .stream()
                .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isBefore(postedCutoff))
                .toList();

        if (!stuckPosted.isEmpty()) {
            log.warn("Found {} THIRDPARTY_POSTED disbursements older than 10 minutes", stuckPosted.size());
            stuckPosted.forEach(d -> log.warn("Stuck THIRDPARTY_POSTED disbursement: id={}, paymentRef={}, createdAt={}",
                    d.getId(), d.getPaymentRef(), d.getCreatedAt()));
        }
    }
}
