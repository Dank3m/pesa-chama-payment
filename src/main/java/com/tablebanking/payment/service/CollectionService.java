package com.tablebanking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebanking.payment.client.DarajaStkClient;
import com.tablebanking.payment.entity.StkPushRequest;
import com.tablebanking.payment.entity.enums.CollectionStatus;
import com.tablebanking.payment.entity.enums.CollectionType;
import com.tablebanking.payment.event.CollectionEvent;
import com.tablebanking.payment.repository.StkPushRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionService {

    private final StkPushRequestRepository stkPushRequestRepository;
    private final DarajaStkClient darajaStkClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    /**
     * Initiate an STK Push collection from a Kafka event request.
     */
    @Transactional
    public void initiateCollection(CollectionEvent requestEvent) {
        log.info("Initiating collection: collectionRef={}, type={}, amount={}, phone={}",
                requestEvent.getCollectionRef(), requestEvent.getCollectionType(),
                requestEvent.getAmount(), requestEvent.getPhoneNumber());

        CollectionType collectionType = CollectionType.valueOf(requestEvent.getCollectionType());

        // Check for existing pending request to prevent duplicates
        List<StkPushRequest> existingPending = stkPushRequestRepository
                .findBySourceIdAndCollectionTypeAndStatusIn(
                        requestEvent.getSourceId(),
                        collectionType,
                        List.of(CollectionStatus.INITIATED, CollectionStatus.STK_SENT));

        if (!existingPending.isEmpty()) {
            log.warn("Duplicate collection request detected for sourceId={}, type={}. Existing request: {}",
                    requestEvent.getSourceId(), collectionType, existingPending.get(0).getCollectionRef());
            throw new IllegalStateException("A pending collection request already exists for sourceId="
                    + requestEvent.getSourceId() + " and type=" + collectionType);
        }

        // Round up amount to nearest whole number for M-Pesa (which only accepts integers)
        BigDecimal originalAmount = requestEvent.getAmount();
        BigDecimal mpesaAmount = originalAmount.setScale(0, RoundingMode.CEILING);
        if (mpesaAmount.compareTo(originalAmount) != 0) {
            log.info("Amount rounded up for M-Pesa: original={}, mpesaAmount={}, excess={}",
                    originalAmount, mpesaAmount, mpesaAmount.subtract(originalAmount));
        }

        // Create the STK Push request entity
        StkPushRequest stkRequest = StkPushRequest.builder()
                .collectionRef(requestEvent.getCollectionRef())
                .collectionType(collectionType)
                .sourceId(requestEvent.getSourceId())
                .memberId(requestEvent.getMemberId())
                .groupId(requestEvent.getGroupId())
                .amount(mpesaAmount)
                .originalAmount(originalAmount)
                .phoneNumber(requestEvent.getPhoneNumber())
                .status(CollectionStatus.INITIATED)
                .accountReference(requestEvent.getCollectionRef())
                .transactionDesc("Payment for " + collectionType)
                .build();

        stkRequest = stkPushRequestRepository.save(stkRequest);

        // Call Daraja STK Push API (DarajaStkClient also rounds up via CEILING, but we pass the already-rounded amount)
        try {
            DarajaStkClient.StkPushApiResponse response = darajaStkClient.initiateStkPush(
                    mpesaAmount,
                    requestEvent.getPhoneNumber(),
                    requestEvent.getCollectionRef(),
                    "Payment for " + collectionType);

            if ("0".equals(response.getResponseCode())) {
                stkRequest.setStatus(CollectionStatus.STK_SENT);
                stkRequest.setMerchantRequestId(response.getMerchantRequestID());
                stkRequest.setCheckoutRequestId(response.getCheckoutRequestID());
                log.info("STK Push sent successfully: collectionRef={}, checkoutRequestId={}",
                        stkRequest.getCollectionRef(), response.getCheckoutRequestID());
            } else {
                stkRequest.setStatus(CollectionStatus.FAILED);
                stkRequest.setResultDesc(response.getResponseDescription());
                log.warn("STK Push failed: collectionRef={}, responseCode={}, description={}",
                        stkRequest.getCollectionRef(), response.getResponseCode(), response.getResponseDescription());
            }
        } catch (Exception e) {
            stkRequest.setStatus(CollectionStatus.FAILED);
            stkRequest.setResultDesc("STK Push API call failed: " + e.getMessage());
            log.error("STK Push API call failed for collectionRef={}: {}", stkRequest.getCollectionRef(), e.getMessage());
        }

        stkPushRequestRepository.save(stkRequest);

        // Publish event
        String eventType = stkRequest.getStatus() == CollectionStatus.STK_SENT ? "STK_SENT" : "COLLECTION_FAILED";
        publishCollectionEvent(stkRequest, eventType);
    }

    /**
     * Handle the STK Push callback from Daraja.
     */
    @Transactional
    public void handleStkCallback(String checkoutRequestId, int resultCode, String resultDesc, String mpesaReceiptNumber) {
        log.info("Handling STK callback: checkoutRequestId={}, resultCode={}, resultDesc={}",
                checkoutRequestId, resultCode, resultDesc);

        Optional<StkPushRequest> optionalRequest = stkPushRequestRepository.findByCheckoutRequestId(checkoutRequestId);
        if (optionalRequest.isEmpty()) {
            log.warn("No STK Push request found for checkoutRequestId={}", checkoutRequestId);
            return;
        }

        StkPushRequest stkRequest = optionalRequest.get();
        stkRequest.setResultCode(resultCode);
        stkRequest.setResultDesc(resultDesc);

        String eventType;

        if (resultCode == 0) {
            stkRequest.setStatus(CollectionStatus.COMPLETED);
            stkRequest.setMpesaReceiptNumber(mpesaReceiptNumber);
            stkRequest.setCompletedAt(Instant.now());
            eventType = "COLLECTION_COMPLETED";
            log.info("STK Push completed: collectionRef={}, mpesaReceipt={}",
                    stkRequest.getCollectionRef(), mpesaReceiptNumber);
        } else if (resultCode == 1032) {
            stkRequest.setStatus(CollectionStatus.CANCELLED);
            eventType = "COLLECTION_FAILED";
            log.info("STK Push cancelled by user: collectionRef={}", stkRequest.getCollectionRef());
        } else {
            stkRequest.setStatus(CollectionStatus.FAILED);
            eventType = "COLLECTION_FAILED";
            log.warn("STK Push failed: collectionRef={}, resultCode={}, resultDesc={}",
                    stkRequest.getCollectionRef(), resultCode, resultDesc);
        }

        stkPushRequestRepository.save(stkRequest);
        publishCollectionEvent(stkRequest, eventType);
    }

    private void publishCollectionEvent(StkPushRequest stkRequest, String eventType) {
        CollectionEvent event = CollectionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .collectionRef(stkRequest.getCollectionRef())
                .collectionType(stkRequest.getCollectionType().name())
                .sourceId(stkRequest.getSourceId())
                .memberId(stkRequest.getMemberId())
                .groupId(stkRequest.getGroupId())
                .amount(stkRequest.getAmount())
                .originalAmount(stkRequest.getOriginalAmount())
                .phoneNumber(stkRequest.getPhoneNumber())
                .mpesaReceiptNumber(stkRequest.getMpesaReceiptNumber())
                .status(stkRequest.getStatus().name())
                .statusDescription(stkRequest.getResultDesc())
                .timestamp(Instant.now())
                .build();

        try {
            kafkaTemplate.send(paymentEventsTopic, stkRequest.getCollectionRef(), event);
            log.info("Published collection event: eventType={}, collectionRef={}", eventType, stkRequest.getCollectionRef());
        } catch (Exception e) {
            log.error("Failed to publish collection event for collectionRef={}: {}",
                    stkRequest.getCollectionRef(), e.getMessage());
        }
    }
}
