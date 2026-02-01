package com.greenroots.kafka;

import com.greenroots.config.KafkaConfig;
import com.greenroots.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentProcessedEvent(Payment payment) {
        Map<String, Object> event = new HashMap<>();
        event.put("paymentId", payment.getId());
        event.put("orderId", payment.getOrder().getId());
        event.put("orderNumber", payment.getOrder().getOrderNumber());
        event.put("stripePaymentIntentId", payment.getStripePaymentIntentId());
        event.put("amount", payment.getAmount());
        event.put("status", payment.getStatus().name());
        event.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(KafkaConfig.PAYMENT_PROCESSED_TOPIC, payment.getStripePaymentIntentId(), event);
        log.info("Payment processed event sent: {}", payment.getStripePaymentIntentId());
    }
}
