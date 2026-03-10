package com.tablebanking.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebanking.payment.event.CollectionEvent;
import com.tablebanking.payment.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionRequestConsumer {

    private final CollectionService collectionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.payment-events:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCollectionRequest(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received payment event: key={}, partition={}, offset={}",
                record.key(), record.partition(), record.offset());

        try {
            CollectionEvent event = objectMapper.readValue(record.value(), CollectionEvent.class);

            if (!"STK_PUSH_REQUEST".equals(event.getEventType())) {
                log.debug("Ignoring event with type: {}", event.getEventType());
                ack.acknowledge();
                return;
            }

            log.info("Processing STK Push request: collectionRef={}, type={}, amount={}",
                    event.getCollectionRef(), event.getCollectionType(), event.getAmount());

            collectionService.initiateCollection(event);

            ack.acknowledge();
            log.info("Successfully processed STK Push request: collectionRef={}", event.getCollectionRef());
        } catch (Exception e) {
            log.error("Failed to process collection request: key={}, error={}", record.key(), e.getMessage(), e);
            ack.acknowledge(); // Acknowledge to prevent infinite retry; error is logged
        }
    }
}
