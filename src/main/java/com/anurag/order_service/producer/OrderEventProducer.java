package com.anurag.order_service.producer;

import com.anurag.order_service.event.OrderPlacedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Value("${kafka.topic.order-placed:order-placed}")
    private String orderPlacedTopic;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(orderPlacedTopic, String.valueOf(event.getOrderId()), payload);
            log.info("Published order-placed event for orderId={} productId={}",
                    event.getOrderId(), event.getProductId());
        } catch (Exception e) {
            // Non-blocking — order is already confirmed, Kafka failure should not rollback
            log.error("Failed to publish order-placed event for orderId={}: {}",
                    event.getOrderId(), e.getMessage());
        }
    }
}
