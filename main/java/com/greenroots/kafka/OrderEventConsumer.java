package com.greenroots.kafka;

import com.greenroots.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class OrderEventConsumer {

    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderCreated(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received order created event: {}", event);

        String orderNumber = (String) event.get("orderNumber");
        log.info("Processing order created: {}", orderNumber);
    }

    @KafkaListener(topics = KafkaConfig.ORDER_CONFIRMED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderConfirmed(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received order confirmed event: {}", event);

        String orderNumber = (String) event.get("orderNumber");
        log.info("Processing order confirmation: {}", orderNumber);
    }

    @KafkaListener(topics = KafkaConfig.PAYMENT_PROCESSED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handlePaymentProcessed(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received payment processed event: {}", event);

        String orderNumber = (String) event.get("orderNumber");
        String status = (String) event.get("status");
        log.info("Processing payment for order: {} - Status: {}", orderNumber, status);
    }
}
