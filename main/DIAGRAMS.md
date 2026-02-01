# GreenRoots - Visual Diagrams

## System Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                              │
│                   (Web/Mobile Applications)                       │
└────────────────────────────┬─────────────────────────────────────┘
                             │ HTTPS/REST
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                      SPRING BOOT APPLICATION                      │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              Spring Security + JWT Filter                   │ │
│  │  • JwtAuthenticationFilter                                  │ │
│  │  • JwtTokenProvider                                         │ │
│  │  • CustomUserDetailsService                                 │ │
│  └──────────────────────────┬──────────────────────────────────┘ │
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │                   REST Controllers                          │ │
│  │  ┌─────────────┬──────────────┬──────────────┬────────────┐ │ │
│  │  │Auth         │Plant         │Order         │Stripe      │ │ │
│  │  │Controller   │Controller    │Controller    │Webhook     │ │ │
│  │  └─────────────┴──────────────┴──────────────┴────────────┘ │ │
│  └──────────────────────────┬──────────────────────────────────┘ │
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │                    Service Layer                            │ │
│  │  ┌─────────────┬──────────────┬──────────────┬────────────┐ │ │
│  │  │Auth         │Plant         │Order         │Payment     │ │ │
│  │  │Service      │Service       │Service       │Service     │ │ │
│  │  │             │+ Cache       │+ Lock        │+ Stripe    │ │ │
│  │  └─────────────┴──────────────┴──────────────┴────────────┘ │ │
│  └──────────────┬─────────────────────────────┬────────────────┘ │
│                 │                              │                  │
│  ┌──────────────▼──────────────┐  ┌───────────▼────────────────┐ │
│  │   Repository Layer          │  │   Kafka Producers          │ │
│  │  • UserRepository           │  │  • OrderEventProducer      │ │
│  │  • PlantRepository          │  │  • PaymentEventProducer    │ │
│  │  • OrderRepository          │  └────────────┬───────────────┘ │
│  │  • PaymentRepository        │               │                 │
│  └──────────────┬──────────────┘               │                 │
│                 │                               │                 │
└─────────────────┼───────────────────────────────┼─────────────────┘
                  │                               │
        ┌─────────▼────────┐          ┌──────────▼───────────┐
        │   PostgreSQL     │          │   Apache Kafka       │
        │      or          │          │   ┌──────────────┐   │
        │     MySQL        │          │   │order.created │   │
        │                  │          │   │order.confirm │   │
        │  ┌────────────┐  │          │   │payment.proc  │   │
        │  │   users    │  │          │   └──────┬───────┘   │
        │  │   plants   │  │          └───────────┼───────────┘
        │  │   orders   │  │                      │
        │  │order_items │  │          ┌───────────▼───────────┐
        │  │  payments  │  │          │   Event Consumers     │
        │  └────────────┘  │          │  • Email Service      │
        └──────────────────┘          │  • Analytics          │
                                      │  • Fulfillment        │
        ┌──────────────────┐          └───────────────────────┘
        │     Redis        │
        │                  │
        │  ┌────────────┐  │
        │  │   Cache    │  │
        │  │  • Plants  │  │
        │  │  • Categories│ │
        │  ├────────────┤  │
        │  │   Locks    │  │
        │  │• Inventory │  │
        │  └────────────┘  │
        └──────────────────┘

        ┌──────────────────┐
        │   Stripe API     │
        │                  │
        │  Payment Intents │
        │    Webhooks      │
        └──────────────────┘
```

## Order Creation Flow with Distributed Locking

```
┌─────────┐                                                    ┌─────────┐
│Client   │                                                    │ Redis   │
│Request  │                                                    │ Server  │
└────┬────┘                                                    └────┬────┘
     │                                                              │
     │ 1. POST /orders                                              │
     ├─────────────────────────────────────►┌──────────────────┐   │
     │                                       │ OrderController  │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │  OrderService    │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                          2. Acquire Lock      │             │
     │                          for Plant IDs        │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │ RedissonClient   │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                                                │ tryLock()   │
     │                                                ├─────────────►
     │                                                │             │
     │                                                │◄────────────┤
     │                                                │  Lock OK    │
     │                          3. Read & Update     │             │
     │                             Inventory         │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │PlantRepository   │   │
     │                                       │  (Pessimistic)   │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │   PostgreSQL     │   │
     │                                       │  SELECT FOR      │   │
     │                                       │     UPDATE       │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                          4. Create Order      │             │
     │                             & Payment         │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │  Stripe API      │   │
     │                                       │ PaymentIntent    │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                          5. Publish Event     │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │     Kafka        │   │
     │                                       │ order.created    │   │
     │                                       └────────┬─────────┘   │
     │                                                │             │
     │                          6. Release Lock      │             │
     │                                       ┌────────▼─────────┐   │
     │                                       │ RedissonClient   │   │
     │                                       └────────┬─────────┘   │
     │                                                │ unlock()    │
     │                                                ├─────────────►
     │                                                │             │
     │ 7. Return Order Response                      │             │
     │◄──────────────────────────────────────────────┤             │
     │                                                              │
     │  {                                                           │
     │    "orderId": 1,                                             │
     │    "orderNumber": "ORD-12345",                               │
     │    "status": "PENDING",                                      │
     │    "paymentInfo": {...}                                      │
     │  }                                                           │
     │                                                              │
```

## Payment Webhook Flow

```
┌──────────┐                                              ┌──────────┐
│ Stripe   │                                              │ Kafka    │
│  API     │                                              │          │
└────┬─────┘                                              └────┬─────┘
     │                                                         │
     │ 1. payment_intent.succeeded                             │
     ├──────────────────────────►┌──────────────────────┐     │
     │                            │StripeWebhookController│     │
     │                            └──────────┬───────────┘     │
     │                                       │                 │
     │                            2. Verify Signature          │
     │                            ┌──────────▼───────────┐     │
     │                            │  Stripe.Webhook      │     │
     │                            │ constructEvent()     │     │
     │                            └──────────┬───────────┘     │
     │                                       │                 │
     │                            3. Update Payment            │
     │                            ┌──────────▼───────────┐     │
     │                            │  PaymentService      │     │
     │                            │ handlePaymentSuccess │     │
     │                            └──────────┬───────────┘     │
     │                                       │                 │
     │                            4. Update Order Status       │
     │                            ┌──────────▼───────────┐     │
     │                            │     Database         │     │
     │                            │  payment.status =    │     │
     │                            │    SUCCEEDED         │     │
     │                            │  order.status =      │     │
     │                            │    CONFIRMED         │     │
     │                            └──────────┬───────────┘     │
     │                                       │                 │
     │                            5. Publish Event             │
     │                            ┌──────────▼───────────┐     │
     │                            │PaymentEventProducer  │     │
     │                            └──────────┬───────────┘     │
     │                                       │                 │
     │                                       ├─────────────────►
     │                                       │  payment.processed
     │                                       │                 │
     │                            6. Return 200 OK             │
     │◄───────────────────────────────────────┤                │
     │                                                         │
```

## Database Schema Diagram

```
┌─────────────────────┐
│       users         │
├─────────────────────┤
│ id (PK)             │
│ email (UNIQUE)      │
│ password            │
│ full_name           │
│ phone_number        │
│ role                │
│ active              │
│ created_at          │
│ updated_at          │
└──────────┬──────────┘
           │ 1
           │
           │ N
┌──────────▼──────────┐
│      orders         │
├─────────────────────┤
│ id (PK)             │
│ order_number (UK)   │
│ user_id (FK)        │───────┐
│ total_amount        │       │
│ status              │       │ 1
│ shipping_address    │       │
│ shipping_city       │       │ 1
│ shipping_postal_code│       │
│ shipping_country    │       │
│ created_at          │       │
│ updated_at          │       │
└──────────┬──────────┘       │
           │ 1                │
           │                  │
           │ N                │
┌──────────▼──────────┐       │
│    order_items      │       │
├─────────────────────┤       │
│ id (PK)             │       │
│ order_id (FK)       │       │
│ plant_id (FK)       │───┐   │
│ quantity            │   │   │
│ price_at_purchase   │   │   │
│ subtotal            │   │   │
└─────────────────────┘   │   │
                          │   │
                       N  │   │
┌─────────────────────┐   │   │
│      plants         │   │   │
├─────────────────────┤   │   │
│ id (PK)             │◄──┘   │
│ name                │       │
│ scientific_name     │       │
│ description         │       │
│ category            │       │
│ price               │       │
│ stock_quantity      │       │
│ light_requirement   │       │
│ water_requirement   │       │
│ image_url           │       │
│ active              │       │
│ created_at          │       │
│ updated_at          │       │
└─────────────────────┘       │
                              │
                              │
┌─────────────────────┐       │
│      payments       │       │
├─────────────────────┤       │
│ id (PK)             │       │
│ order_id (FK,UK)    │◄──────┘
│ stripe_payment_     │
│   intent_id (UK)    │
│ amount              │
│ currency            │
│ status              │
│ failure_reason      │
│ idempotency_key (UK)│
│ created_at          │
│ updated_at          │
└─────────────────────┘
```

## Caching Strategy

```
┌──────────────────────────────────────────────────────────┐
│                      Redis Cache                         │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  Cache Keys:                                             │
│  ┌────────────────────────────────────────────────────┐  │
│  │ plants::all              → List<PlantDTO>         │  │
│  │ plants::1                → PlantDTO (id=1)        │  │
│  │ plants::2                → PlantDTO (id=2)        │  │
│  │ plants::category_INDOOR  → List<PlantDTO>         │  │
│  │ plants::category_OUTDOOR → List<PlantDTO>         │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  Lock Keys (Redisson):                                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │ plant:stock:1   → RLock (for plant_id=1)          │  │
│  │ plant:stock:2   → RLock (for plant_id=2)          │  │
│  │ plant:stock:N   → RLock (for plant_id=N)          │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  Cache Eviction Events:                                  │
│  • Plant created    → Evict all plant caches            │
│  • Plant updated    → Evict all plant caches            │
│  • Plant deleted    → Evict all plant caches            │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

## Kafka Topics and Event Flow

```
┌────────────────────────────────────────────────────────────┐
│                      Kafka Cluster                         │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Topic: order.created (3 partitions)                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Event: {                                             │  │
│  │   orderId: 1,                                        │  │
│  │   orderNumber: "ORD-12345",                          │  │
│  │   userId: 10,                                        │  │
│  │   totalAmount: 99.99,                                │  │
│  │   status: "PENDING",                                 │  │
│  │   timestamp: 1234567890                              │  │
│  │ }                                                    │  │
│  └──────────────────────────────────────────────────────┘  │
│           │                                                │
│           ├──────► OrderEventConsumer                      │
│           │        • Send email notification               │
│           │        • Update analytics                      │
│           │                                                │
│                                                            │
│  Topic: payment.processed (3 partitions)                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Event: {                                             │  │
│  │   paymentId: 1,                                      │  │
│  │   orderId: 1,                                        │  │
│  │   orderNumber: "ORD-12345",                          │  │
│  │   stripePaymentIntentId: "pi_xxx",                   │  │
│  │   amount: 99.99,                                     │  │
│  │   status: "SUCCEEDED",                               │  │
│  │   timestamp: 1234567890                              │  │
│  │ }                                                    │  │
│  └──────────────────────────────────────────────────────┘  │
│           │                                                │
│           ├──────► PaymentEventConsumer                    │
│           │        • Trigger fulfillment                   │
│           │        • Update order status                   │
│           │                                                │
│                                                            │
│  Topic: order.confirmed (3 partitions)                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Event: {                                             │  │
│  │   orderId: 1,                                        │  │
│  │   orderNumber: "ORD-12345",                          │  │
│  │   status: "CONFIRMED",                               │  │
│  │   timestamp: 1234567890                              │  │
│  │ }                                                    │  │
│  └──────────────────────────────────────────────────────┘  │
│           │                                                │
│           ├──────► Email Service                           │
│           ├──────► Warehouse System                        │
│           └──────► Shipping System                         │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## JWT Authentication Flow

```
┌────────┐                                            ┌───────────┐
│ Client │                                            │  Backend  │
└───┬────┘                                            └─────┬─────┘
    │                                                       │
    │ 1. POST /auth/register or /auth/login                │
    │    { email, password }                               │
    ├──────────────────────────────────────────────────────►
    │                                                       │
    │                          2. Validate Credentials     │
    │                             Hash Password (BCrypt)   │
    │                             Generate JWT Token       │
    │                                                       │
    │ 3. Return JWT Token                                  │
    │    {                                                 │
    │      token: "eyJhbGciOiJIUzUxMiJ9...",               │
    │      userId: 1,                                      │
    │      email: "user@example.com",                      │
    │      role: "USER"                                    │
    │    }                                                 │
    │◄──────────────────────────────────────────────────────┤
    │                                                       │
    │ 4. Store token in localStorage/cookie                │
    │                                                       │
    │ 5. Subsequent request with token                     │
    │    Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...     │
    ├──────────────────────────────────────────────────────►
    │                                                       │
    │                          6. JwtAuthenticationFilter  │
    │                             • Extract token          │
    │                             • Validate signature     │
    │                             • Check expiration       │
    │                             • Load user details      │
    │                             • Set SecurityContext    │
    │                                                       │
    │ 7. Process request with authenticated user           │
    │                                                       │
    │ 8. Return response                                   │
    │◄──────────────────────────────────────────────────────┤
    │                                                       │
```

## Concurrent Order Handling

```
Time    Instance A              Redis               Instance B
────    ──────────────────      ─────────           ──────────────────
t0      Order Request
        (Plant ID: 1, Qty: 5)
        
t1      Try Lock: plant:stock:1
        ├──────────────────────►
        
t2                              Lock Acquired
                                Owner: Instance A
        ◄──────────────────────┤
        
t3      Read Stock: 10
        Update: 10 - 5 = 5
        
t4                                                  Order Request
                                                    (Plant ID: 1, Qty: 3)
                                                    
t5                                                  Try Lock: plant:stock:1
                                                    ├───────────────────►
                                                    
t6                              Lock Held by A
                                WAIT...
                                ├───────────────────►
                                
t7      Commit Transaction
        Release Lock
        ├──────────────────────►
        
t8                              Lock Released
                                ◄──────────────────┤
                                Lock Acquired
                                Owner: Instance B
                                ├───────────────────►
                                
t9                                                  Read Stock: 5
                                                    Update: 5 - 3 = 2
                                                    
t10                                                 Commit Transaction
                                                    Release Lock
                                                    ├───────────────────►
                                
t11                             Lock Released
                                
Final Stock: 2 ✅
(Correct! No race condition)
```

---

**Note**: These diagrams provide visual representations of the system architecture, data flows, and key processes in the GreenRoots backend application.
