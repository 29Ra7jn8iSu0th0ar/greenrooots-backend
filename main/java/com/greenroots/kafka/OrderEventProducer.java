package com.greenroots.kafka;

import com.greenroots.config.KafkaConfig;
import com.greenroots.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderCreatedEvent(Order order) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", order.getId());
        event.put("orderNumber", order.getOrderNumber());
        event.put("userId", order.getUser().getId());
        event.put("totalAmount", order.getTotalAmount());
        event.put("status", order.getStatus().name());
        event.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(KafkaConfig.ORDER_CREATED_TOPIC, order.getOrderNumber(), event);
        log.info("Order created event sent: {}", order.getOrderNumber());
    }

    public void sendOrderConfirmedEvent(Order order) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", order.getId());
        event.put("orderNumber", order.getOrderNumber());
        event.put("status", order.getStatus().name());
        event.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(KafkaConfig.ORDER_CONFIRMED_TOPIC, order.getOrderNumber(), event);
        log.info("Order confirmed event sent: {}", order.getOrderNumber());
    }
}
