package com.greenroots.service;

import com.greenroots.dto.order.OrderRequest;
import com.greenroots.dto.order.OrderResponse;
import com.greenroots.entity.*;
import com.greenroots.exception.BadRequestException;
import com.greenroots.exception.ResourceNotFoundException;
import com.greenroots.kafka.OrderEventProducer;
import com.greenroots.repository.OrderRepository;
import com.greenroots.repository.PlantRepository;
import com.greenroots.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final PlantRepository plantRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final OrderEventProducer orderEventProducer;
    private final RedissonClient redissonClient;

    @Value("${app.redis.lock.wait-time}")
    private long lockWaitTime;

    @Value("${app.redis.lock.lease-time}")
    private long lockLeaseTime;

    @Transactional
    public OrderResponse createOrder(OrderRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<String> lockKeys = new ArrayList<>();
        List<RLock> locks = new ArrayList<>();

        try {
            Order order = Order.builder()
                    .orderNumber(generateOrderNumber())
                    .user(user)
                    .status(Order.OrderStatus.PENDING)
                    .shippingAddress(request.getShippingAddress())
                    .shippingCity(request.getShippingCity())
                    .shippingPostalCode(request.getShippingPostalCode())
                    .shippingCountry(request.getShippingCountry())
                    .orderItems(new ArrayList<>())
                    .build();

            BigDecimal totalAmount = BigDecimal.ZERO;

            for (OrderRequest.OrderItemRequest itemRequest : request.getItems()) {
                String lockKey = "plant:stock:" + itemRequest.getPlantId();
                lockKeys.add(lockKey);
                RLock lock = redissonClient.getLock(lockKey);

                boolean acquired = lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw new BadRequestException("Unable to acquire lock for plant: " + itemRequest.getPlantId());
                }
                locks.add(lock);

                Plant plant = plantRepository.findByIdWithLock(itemRequest.getPlantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

                if (plant.getStockQuantity() < itemRequest.getQuantity()) {
                    throw new BadRequestException("Insufficient stock for plant: " + plant.getName());
                }

                plant.setStockQuantity(plant.getStockQuantity() - itemRequest.getQuantity());
                plantRepository.save(plant);

                BigDecimal subtotal = plant.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
                totalAmount = totalAmount.add(subtotal);

                OrderItem orderItem = OrderItem.builder()
                        .plant(plant)
                        .quantity(itemRequest.getQuantity())
                        .priceAtPurchase(plant.getPrice())
                        .subtotal(subtotal)
                        .build();

                order.addOrderItem(orderItem);
            }

            order.setTotalAmount(totalAmount);
            order = orderRepository.save(order);

            log.info("Order created successfully: {}", order.getOrderNumber());

            String paymentIntentId = paymentService.createPaymentIntent(order);

            orderEventProducer.sendOrderCreatedEvent(order);

            return buildOrderResponse(order);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Order creation interrupted");
        } finally {
            locks.forEach(lock -> {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to order");
        }

        return buildOrderResponse(order);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Order status updated: {} -> {}", order.getOrderNumber(), status);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderResponse.OrderItemResponse> items = order.getOrderItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .plantId(item.getPlant().getId())
                        .plantName(item.getPlant().getName())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        OrderResponse.ShippingInfo shippingInfo = OrderResponse.ShippingInfo.builder()
                .address(order.getShippingAddress())
                .city(order.getShippingCity())
                .postalCode(order.getShippingPostalCode())
                .country(order.getShippingCountry())
                .build();

        OrderResponse.PaymentInfo paymentInfo = null;
        if (order.getPayment() != null) {
            paymentInfo = OrderResponse.PaymentInfo.builder()
                    .stripePaymentIntentId(order.getPayment().getStripePaymentIntentId())
                    .status(order.getPayment().getStatus().name())
                    .amount(order.getPayment().getAmount())
                    .currency(order.getPayment().getCurrency())
                    .build();
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(items)
                .shippingInfo(shippingInfo)
                .paymentInfo(paymentInfo)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
