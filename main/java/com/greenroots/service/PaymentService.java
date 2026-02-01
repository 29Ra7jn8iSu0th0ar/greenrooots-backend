package com.greenroots.service;

import com.greenroots.entity.Order;
import com.greenroots.entity.Payment;
import com.greenroots.exception.BadRequestException;
import com.greenroots.kafka.PaymentEventProducer;
import com.greenroots.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public String createPaymentIntent(Order order) {
        try {
            String idempotencyKey = UUID.randomUUID().toString();

            Long amountInCents = order.getTotalAmount()
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .putMetadata("order_id", order.getId().toString())
                    .putMetadata("order_number", order.getOrderNumber())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            Payment payment = Payment.builder()
                    .order(order)
                    .stripePaymentIntentId(paymentIntent.getId())
                    .amount(order.getTotalAmount())
                    .currency("usd")
                    .status(Payment.PaymentStatus.PENDING)
                    .idempotencyKey(idempotencyKey)
                    .build();

            paymentRepository.save(payment);
            log.info("Payment intent created: {} for order: {}", paymentIntent.getId(), order.getOrderNumber());

            return paymentIntent.getId();

        } catch (StripeException e) {
            log.error("Stripe payment intent creation failed: {}", e.getMessage());
            throw new BadRequestException("Payment processing failed: " + e.getMessage());
        }
    }

    @Transactional
    public void handlePaymentSuccess(String paymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new BadRequestException("Payment not found"));

        payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        order.setStatus(Order.OrderStatus.CONFIRMED);

        log.info("Payment succeeded for order: {}", order.getOrderNumber());
        paymentEventProducer.sendPaymentProcessedEvent(payment);
    }

    @Transactional
    public void handlePaymentFailure(String paymentIntentId, String failureReason) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new BadRequestException("Payment not found"));

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        order.setStatus(Order.OrderStatus.CANCELLED);

        log.error("Payment failed for order: {} - Reason: {}", order.getOrderNumber(), failureReason);
    }
}
