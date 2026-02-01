# GreenRoots Backend - Production-Grade E-Commerce Plant Nursery

## 🌿 Overview

GreenRoots is a production-grade backend system for an e-commerce plant nursery application built with **Java 17** and **Spring Boot 3**. It implements best practices for scalability, security, and maintainability.

## 🚀 Key Features

### Core Functionality
- **User Management**: Registration, authentication with JWT
- **Plant Catalog**: Browse, filter by category, search plants
- **Order Management**: Create orders with transactional consistency
- **Inventory Control**: Redis distributed locking to prevent race conditions
- **Payment Processing**: Stripe integration with webhook handling
- **Asynchronous Events**: Kafka-based event system for orders and payments

### Technical Highlights
- **Security**: Spring Security 6 with JWT authentication
- **Role-Based Access Control**: USER and ADMIN roles
- **Database Support**: Both PostgreSQL and MySQL
- **Caching**: Redis for performance optimization
- **Distributed Locking**: Redisson for inventory management
- **Database Migrations**: Flyway for version control
- **Event-Driven Architecture**: Kafka for async workflows
- **Clean Architecture**: Layered design (Controller → Service → Repository)

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker & Docker Compose (for infrastructure)

## 🏗️ Project Structure

```
greenroots-backend/
├── src/main/java/com/greenroots/
│   ├── GreenRootsApplication.java      # Main application entry
│   ├── config/                          # Configuration classes
│   │   ├── SecurityConfig.java          # Spring Security setup
│   │   ├── RedisConfig.java             # Redis & Redisson config
│   │   ├── KafkaConfig.java             # Kafka topics config
│   │   └── StripeConfig.java            # Stripe initialization
│   ├── entity/                          # JPA entities
│   │   ├── User.java
│   │   ├── Plant.java
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── Payment.java
│   ├── dto/                             # Data Transfer Objects
│   │   ├── auth/
│   │   ├── plant/
│   │   └── order/
│   ├── repository/                      # Spring Data JPA repositories
│   ├── service/                         # Business logic layer
│   │   ├── AuthService.java
│   │   ├── PlantService.java
│   │   ├── OrderService.java
│   │   └── PaymentService.java
│   ├── controller/                      # REST API controllers
│   │   ├── AuthController.java
│   │   ├── PlantController.java
│   │   ├── OrderController.java
│   │   └── StripeWebhookController.java
│   ├── security/                        # Security components
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── CustomUserDetailsService.java
│   ├── kafka/                           # Kafka producers & consumers
│   │   ├── OrderEventProducer.java
│   │   ├── PaymentEventProducer.java
│   │   └── OrderEventConsumer.java
│   └── exception/                       # Exception handling
│       ├── GlobalExceptionHandler.java
│       ├── ResourceNotFoundException.java
│       └── BadRequestException.java
├── src/main/resources/
│   ├── application.yml                  # Main config
│   ├── application-postgres.yml         # PostgreSQL profile
│   ├── application-mysql.yml            # MySQL profile
│   └── db/migration/                    # Flyway migrations
│       ├── postgres/
│       └── mysql/
├── docker-compose.yml                   # Infrastructure setup
└── pom.xml                              # Maven dependencies
```

## 🔧 Quick Start

### 1. Start Infrastructure Services

```bash
# Start PostgreSQL, MySQL, Redis, Kafka, and Zookeeper
cd greenroots-backend
docker-compose up -d

# Verify services are running
docker-compose ps
```

### 2. Configure Application

Edit `src/main/resources/application.yml` to set:

```yaml
spring:
  profiles:
    active: postgres  # or mysql

app:
  jwt:
    secret: your-secret-key-min-256-bits
  stripe:
    api-key: sk_test_your_stripe_key
    webhook-secret: whsec_your_webhook_secret
```

### 3. Build and Run

```bash
# Build the project
mvn clean install

# Run with PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=postgres

# Or run with MySQL
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

The application will start on `http://localhost:8080/api`

## 🔐 API Documentation

### Authentication Endpoints

#### Register User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "fullName": "John Doe",
    "phoneNumber": "+1234567890"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "type": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

#### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### Plant Endpoints

#### Get All Plants (Public)
```bash
curl -X GET http://localhost:8080/api/plants
```

#### Get Plants by Category
```bash
curl -X GET http://localhost:8080/api/plants/category/INDOOR
```

Categories: `INDOOR`, `OUTDOOR`, `SUCCULENT`, `HERB`, `FLOWER`, `TREE`, `VINE`

#### Create Plant (Admin Only)
```bash
curl -X POST http://localhost:8080/api/plants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "name": "Monstera Deliciosa",
    "scientificName": "Monstera deliciosa",
    "description": "Popular tropical plant with large, glossy leaves",
    "category": "INDOOR",
    "price": 29.99,
    "stockQuantity": 50,
    "lightRequirement": "MEDIUM",
    "waterRequirement": "MEDIUM",
    "imageUrl": "https://example.com/monstera.jpg"
  }'
```

### Order Endpoints

#### Create Order (Authenticated)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "items": [
      {
        "plantId": 1,
        "quantity": 2
      },
      {
        "plantId": 3,
        "quantity": 1
      }
    ],
    "shippingAddress": "123 Green Street",
    "shippingCity": "San Francisco",
    "shippingPostalCode": "94102",
    "shippingCountry": "USA"
  }'
```

**Response:**
```json
{
  "orderId": 1,
  "orderNumber": "ORD-A1B2C3D4",
  "totalAmount": 89.97,
  "status": "PENDING",
  "items": [
    {
      "plantId": 1,
      "plantName": "Monstera Deliciosa",
      "quantity": 2,
      "priceAtPurchase": 29.99,
      "subtotal": 59.98
    }
  ],
  "shippingInfo": {...},
  "paymentInfo": {
    "stripePaymentIntentId": "pi_xxx",
    "status": "PENDING",
    "amount": 89.97,
    "currency": "usd"
  },
  "createdAt": "2024-01-15T10:30:00"
}
```

#### Get User Orders
```bash
curl -X GET http://localhost:8080/api/orders \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 🏗️ Architecture Details

### Distributed Locking with Redis

The system uses Redisson to implement distributed locks for inventory management:

```java
// In OrderService.java
RLock lock = redissonClient.getLock("plant:stock:" + plantId);
boolean acquired = lock.tryLock(3000, 10000, TimeUnit.MILLISECONDS);

if (acquired) {
    try {
        // Update inventory with pessimistic locking
        Plant plant = plantRepository.findByIdWithLock(plantId);
        plant.setStockQuantity(plant.getStockQuantity() - quantity);
        plantRepository.save(plant);
    } finally {
        lock.unlock();
    }
}
```

### Kafka Event Flow

1. **Order Created** → `order.created` topic
2. **Payment Processed** → `payment.processed` topic  
3. **Order Confirmed** → `order.confirmed` topic

Consumers can react to these events for:
- Email notifications
- Inventory updates
- Analytics
- Fulfillment workflows

### Stripe Payment Integration

**Payment Flow:**
1. Order created → Payment Intent created in Stripe
2. Frontend completes payment with Stripe Elements
3. Stripe sends webhook to `/api/stripe/webhook`
4. Payment success → Order status updated to CONFIRMED
5. Kafka event published for downstream processing

**Webhook Events Handled:**
- `payment_intent.succeeded`
- `payment_intent.payment_failed`

### Security Configuration

**Public Endpoints:**
- `/api/auth/**` - Registration & Login
- `/api/plants/**` - Browse plants
- `/api/stripe/webhook` - Stripe webhooks

**Authenticated Endpoints:**
- `/api/orders/**` - Order management

**Admin Only:**
- `POST /api/plants` - Create plants
- `PUT /api/plants/{id}` - Update plants
- `DELETE /api/plants/{id}` - Delete plants

## 🗄️ Database Schema

### Entity Relationships

```
User (1) ──────< (N) Order
                       │
                       ├──< (N) OrderItem >── (1) Plant
                       │
                       └──── (1) Payment
```

### Key Tables

- **users**: Authentication and user profile
- **plants**: Product catalog with inventory
- **orders**: Order header information
- **order_items**: Line items for each order
- **payments**: Stripe payment tracking

## 🔄 Database Migration

Flyway automatically applies migrations on startup.

**Migration files:**
- PostgreSQL: `src/main/resources/db/migration/postgres/`
- MySQL: `src/main/resources/db/migration/mysql/`

**Naming Convention:** `V{version}__{description}.sql`

Example: `V1__Create_Users_Table.sql`

## 🧪 Testing the Application

### 1. Complete Order Flow Test

```bash
#!/bin/bash

# 1. Register a user
REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test1234",
    "fullName": "Test User",
    "phoneNumber": "+1234567890"
  }')

TOKEN=$(echo $REGISTER_RESPONSE | jq -r '.token')
echo "Token: $TOKEN"

# 2. Create a plant (requires admin token)
curl -X POST http://localhost:8080/api/plants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "name": "Snake Plant",
    "scientificName": "Sansevieria trifasciata",
    "description": "Hardy indoor plant",
    "category": "INDOOR",
    "price": 19.99,
    "stockQuantity": 100,
    "lightRequirement": "LOW",
    "waterRequirement": "LOW",
    "imageUrl": "https://example.com/snake-plant.jpg"
  }'

# 3. Browse plants
curl -X GET http://localhost:8080/api/plants

# 4. Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "items": [{"plantId": 1, "quantity": 2}],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingPostalCode": "10001",
    "shippingCountry": "USA"
  }'

# 5. View orders
curl -X GET http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN"
```

## 📊 Monitoring & Logging

The application uses SLF4J with Logback for logging.

**Log Levels:**
- `INFO` - Application flow
- `DEBUG` - SQL queries and security
- `ERROR` - Exceptions and failures

**Key Logs:**
- User registration/login
- Order creation with distributed locks
- Payment processing
- Kafka event publishing
- Stripe webhook handling

## 🚀 Production Deployment Checklist

- [ ] Change JWT secret to a strong random key (min 256 bits)
- [ ] Update Stripe API keys to production keys
- [ ] Configure production database URLs
- [ ] Set up Redis cluster for high availability
- [ ] Configure Kafka cluster with replication
- [ ] Enable HTTPS/TLS
- [ ] Set up monitoring (Prometheus, Grafana)
- [ ] Configure log aggregation (ELK Stack)
- [ ] Set up database backups
- [ ] Implement rate limiting
- [ ] Enable CORS for frontend domain
- [ ] Set up CI/CD pipeline

## 🔧 Configuration Reference

### Environment Variables

```bash
# Database (PostgreSQL)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/greenroots
SPRING_DATASOURCE_USERNAME=greenroots_user
SPRING_DATASOURCE_PASSWORD=greenroots_pass

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT
APP_JWT_SECRET=your-secret-key-min-256-bits
APP_JWT_EXPIRATION=86400000

# Stripe
APP_STRIPE_API_KEY=sk_live_your_key
APP_STRIPE_WEBHOOK_SECRET=whsec_your_secret
```

## 📦 Dependencies

### Core
- Spring Boot 3.2.1
- Spring Security 6.x
- Spring Data JPA
- Spring Kafka

### Database
- PostgreSQL Driver
- MySQL Driver
- Flyway Migration

### Caching & Locking
- Spring Data Redis
- Redisson 3.25.2

### Payment
- Stripe Java SDK 24.3.0

### Security
- JJWT 0.12.3

### Utilities
- Lombok

## 🤝 API Contract Examples

### Error Responses

```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2024-01-15T10:30:00",
  "errors": [
    "email: Invalid email format",
    "password: Password must be at least 8 characters"
  ]
}
```

### Success Responses

All successful responses return appropriate HTTP status codes:
- `200 OK` - Successful GET, PUT
- `201 Created` - Successful POST
- `204 No Content` - Successful DELETE

## 📞 Support

For questions or issues, please check:
- Application logs in console
- Docker container logs: `docker-compose logs -f`
- Kafka consumer logs for event processing

## 📄 License

This is a production-grade template for educational and commercial use.

---

**Built with ❤️ for scalable e-commerce backends**