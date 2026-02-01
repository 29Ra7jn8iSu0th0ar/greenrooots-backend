# GreenRoots - System Architecture

## Overview
This document describes the architecture, design decisions, and data flows of the GreenRoots plant nursery backend system.

## Architecture Diagram

```
┌─────────────┐
│   Client    │
│  (Mobile/   │
│    Web)     │
└──────┬──────┘
       │ HTTPS
       │
┌──────▼──────────────────────────────────────────────┐
│           Spring Boot Application                    │
│  ┌────────────────────────────────────────────┐    │
│  │         Security Layer (JWT)                │    │
│  └────────────────┬───────────────────────────┘    │
│                   │                                  │
│  ┌────────────────▼───────────────────────────┐    │
│  │          REST Controllers                   │    │
│  │  - AuthController                           │    │
│  │  - PlantController                          │    │
│  │  - OrderController                          │    │
│  │  - StripeWebhookController                  │    │
│  └────────────────┬───────────────────────────┘    │
│                   │                                  │
│  ┌────────────────▼───────────────────────────┐    │
│  │          Service Layer                      │    │
│  │  - AuthService                              │    │
│  │  - PlantService (+ Redis Cache)             │    │
│  │  - OrderService (+ Distributed Locking)     │    │
│  │  - PaymentService                           │    │
│  └─────┬──────────────────────────┬───────────┘    │
│        │                          │                 │
│  ┌─────▼──────────────┐   ┌───────▼─────────────┐ │
│  │   Repository Layer │   │   Kafka Producers   │ │
│  │  - Spring Data JPA │   │  - OrderEvents      │ │
│  └─────┬──────────────┘   │  - PaymentEvents    │ │
│        │                  └───────┬─────────────┘ │
└────────┼──────────────────────────┼───────────────┘
         │                          │
    ┌────▼──────┐            ┌──────▼───────┐
    │PostgreSQL/│            │    Kafka     │
    │   MySQL   │            │   Cluster    │
    └───────────┘            └──────┬───────┘
                                    │
                             ┌──────▼─────────────┐
                             │  Kafka Consumers   │
                             │ - Email Service    │
                             │ - Analytics        │
                             │ - Fulfillment      │
                             └────────────────────┘

    ┌───────────┐
    │   Redis   │
    │  Cache +  │
    │   Locks   │
    └───────────┘

    ┌───────────┐
    │  Stripe   │
    │   API     │
    └───────────┘
```

## Component Details

### 1. Security Layer

**Technology:** Spring Security 6 + JWT

**Flow:**
1. User authenticates with email/password
2. System validates credentials
3. JWT token generated and returned
4. Subsequent requests include token in Authorization header
5. JwtAuthenticationFilter validates token on each request
6. User loaded from database if token valid

**Key Classes:**
- `SecurityConfig` - Spring Security configuration
- `JwtTokenProvider` - Token generation and validation
- `JwtAuthenticationFilter` - Request interceptor
- `CustomUserDetailsService` - User loading

### 2. REST API Layer

**Controllers:**
- `AuthController` - Registration, login
- `PlantController` - CRUD operations, category filtering
- `OrderController` - Order creation, order history
- `StripeWebhookController` - Payment webhook handling

**Security Rules:**
```
Public:     /auth/**, /plants/**, /stripe/webhook
User:       /orders/**
Admin:      POST/PUT/DELETE /plants/**
```

### 3. Business Logic Layer

#### AuthService
- User registration with password hashing (BCrypt)
- Login with JWT token generation
- Email uniqueness validation

#### PlantService
- CRUD operations with soft delete
- Redis caching for performance
- Category-based filtering
- Cache eviction on updates

#### OrderService
**Critical Path: Order Creation with Distributed Locking**

```java
1. Acquire Redis distributed locks for all plants in order
2. For each plant:
   a. Lock acquired → Check stock
   b. If sufficient → Decrement quantity (with pessimistic DB lock)
   c. If insufficient → Rollback transaction, release locks
3. Create order with calculated total
4. Create Stripe payment intent
5. Save order and payment records
6. Publish order.created event to Kafka
7. Release all locks
```

**Why Distributed Locking?**
- Prevents race conditions in high-concurrency scenarios
- Multiple instances can safely modify inventory
- Redisson provides automatic lock release on failure

#### PaymentService
- Create Stripe payment intents
- Handle webhook events (success/failure)
- Update order status based on payment
- Publish payment events to Kafka

### 4. Data Access Layer

**Spring Data JPA Repositories:**
- `UserRepository` - User queries
- `PlantRepository` - Plant queries with pessimistic locking
- `OrderRepository` - Order queries
- `PaymentRepository` - Payment queries

**Key Query:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Plant p WHERE p.id = :id")
Optional<Plant> findByIdWithLock(@Param("id") Long id);
```

This ensures database-level locking in addition to distributed locks.

### 5. Event-Driven Architecture

**Kafka Topics:**
- `order.created` - New order placed
- `order.confirmed` - Payment successful
- `payment.processed` - Payment status updated

**Producers:**
- `OrderEventProducer` - Publishes order events
- `PaymentEventProducer` - Publishes payment events

**Consumers:**
- `OrderEventConsumer` - Processes order events
- Can be extended for email notifications, analytics, etc.

**Benefits:**
- Decoupled services
- Asynchronous processing
- Scalability
- Fault tolerance with Kafka's durability

### 6. Caching Strategy

**Redis Cache:**
- Plant catalog (key: `plants::{id}` or `plants::all`)
- Category-based queries (key: `plants::category_{category}`)

**Cache Eviction:**
- On plant create/update/delete → evict all plant caches

**TTL:** Configured in Redis (default: no expiration, manual eviction)

### 7. Payment Integration

**Stripe Flow:**

```
1. Backend creates PaymentIntent → Returns client_secret
2. Frontend collects payment details with Stripe.js
3. Frontend confirms payment with client_secret
4. Stripe processes payment
5. Stripe sends webhook to /api/stripe/webhook
6. Backend validates webhook signature
7. Backend updates payment and order status
8. Backend publishes Kafka event
```

**Idempotency:**
- Each payment has unique idempotency key
- Prevents duplicate charges

**Webhook Security:**
- Signature verification with webhook secret
- Prevents unauthorized webhook calls

## Database Schema

### Tables

**users**
- id (PK)
- email (unique)
- password (hashed)
- full_name
- phone_number
- role (USER/ADMIN)
- active
- created_at, updated_at

**plants**
- id (PK)
- name
- scientific_name
- description
- category (enum)
- price
- stock_quantity
- light_requirement (enum)
- water_requirement (enum)
- image_url
- active (soft delete flag)
- created_at, updated_at

**orders**
- id (PK)
- order_number (unique)
- user_id (FK → users)
- total_amount
- status (enum)
- shipping_address, city, postal_code, country
- created_at, updated_at

**order_items**
- id (PK)
- order_id (FK → orders)
- plant_id (FK → plants)
- quantity
- price_at_purchase (snapshot)
- subtotal

**payments**
- id (PK)
- order_id (FK → orders, unique)
- stripe_payment_intent_id (unique)
- amount
- currency
- status (enum)
- failure_reason
- idempotency_key (unique)
- created_at, updated_at

### Indexes

```sql
-- Performance indexes
CREATE INDEX idx_plants_category ON plants(category);
CREATE INDEX idx_plants_active ON plants(active);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_payments_stripe_payment_intent_id ON payments(stripe_payment_intent_id);
```

## Concurrency Control

### Multi-Layer Locking Strategy

**Layer 1: Distributed Lock (Redis + Redisson)**
- Prevents multiple application instances from processing same inventory
- Lock key: `plant:stock:{plantId}`
- Wait time: 3 seconds
- Lease time: 10 seconds

**Layer 2: Pessimistic Database Lock**
- Ensures transaction-level consistency
- `SELECT ... FOR UPDATE` via JPA `@Lock`

**Why Both?**
- Distributed lock: Protects across multiple app instances
- DB lock: Protects within single instance (multiple threads)

**Example Scenario:**
```
Time  | Instance A         | Instance B
------|--------------------|-----------------
t0    | Acquire Redis lock |  
t1    | Read stock: 10     | Try Redis lock (wait)
t2    | Decrement to 8     |
t3    | Commit + Release   |
t4    |                    | Acquire Redis lock
t5    |                    | Read stock: 8
```

## Error Handling

**Global Exception Handler:**
- `@RestControllerAdvice` catches all exceptions
- Returns consistent error response format
- Logs errors with context

**Exception Types:**
- `ResourceNotFoundException` → 404
- `BadRequestException` → 400
- `BadCredentialsException` → 401
- `AccessDeniedException` → 403
- `MethodArgumentNotValidException` → 400 (validation)
- `Exception` → 500

**Response Format:**
```json
{
  "status": 400,
  "message": "Error description",
  "timestamp": "2024-01-15T10:30:00",
  "errors": ["Field-specific errors"]
}
```

## Scalability Considerations

### Horizontal Scaling
- Stateless application (JWT-based auth)
- Distributed locking enables multi-instance deployment
- Kafka for async processing
- Redis for shared cache

### Database Scaling
- Read replicas for plant catalog queries
- Write to master for orders/payments
- Connection pooling configured

### Performance Optimizations
- Redis caching for frequently accessed data
- JPA fetch strategies (LAZY for associations)
- Database indexes on frequently queried columns
- Kafka for offloading heavy processing

### Monitoring Points
- Lock acquisition failures
- Cache hit/miss ratios
- Payment webhook processing time
- Kafka consumer lag
- Database query performance

## Security Best Practices

1. **Password Security:** BCrypt with salt (Spring Security default)
2. **JWT Security:** HS512 algorithm, configurable expiration
3. **SQL Injection Prevention:** JPA with parameterized queries
4. **CORS:** Configure allowed origins in production
5. **Webhook Security:** Stripe signature verification
6. **Role-Based Access:** Method-level security with `@PreAuthorize`
7. **Sensitive Data:** Never log passwords, tokens, or API keys

## Testing Strategy

### Unit Tests
- Service layer business logic
- Repository queries
- JWT token generation/validation

### Integration Tests
- Controller endpoints
- Database transactions
- Kafka event publishing

### End-to-End Tests
- Complete order flow
- Payment webhook handling
- Concurrent order creation

## Deployment Architecture

```
┌─────────────┐
│ Load        │
│ Balancer    │
└──────┬──────┘
       │
   ┌───┴───┬───────┬───────┐
   │       │       │       │
┌──▼──┐ ┌──▼──┐ ┌──▼──┐ ┌──▼──┐
│App-1│ │App-2│ │App-3│ │App-N│
└──┬──┘ └──┬──┘ └──┬──┘ └──┬──┘
   │       │       │       │
   └───┬───┴───┬───┴───┬───┘
       │       │       │
   ┌───▼───────▼───────▼────┐
   │   PostgreSQL Cluster   │
   │   (Master + Replicas)   │
   └────────────────────────┘

   ┌────────────────────────┐
   │    Redis Cluster       │
   │  (Master + Replicas)   │
   └────────────────────────┘

   ┌────────────────────────┐
   │    Kafka Cluster       │
   │  (Multiple Brokers)    │
   └────────────────────────┘
```

---

**Last Updated:** January 2024  
**Version:** 1.0.0