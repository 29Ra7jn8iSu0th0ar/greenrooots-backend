# 🌱 GreenRoots Backend

GreenRoots is a production-grade backend system for a scalable plant nursery e-commerce platform, built using **Java 17** and **Spring Boot 3**.  
The project is designed to simulate real-world backend engineering challenges such as secure authentication, distributed inventory management, asynchronous workflows, and payment processing.

---

## 🚀 Features

- User & Admin management with role-based access control
- Secure authentication using JWT and Spring Security
- Plant catalog browsing with filtering and categories
- Order placement with transactional consistency
- Inventory management using Redis distributed locks
- Kafka-based asynchronous order & payment events
- Stripe payment gateway integration with webhook handling
- Redis used for caching and concurrency control
- Relational database support (MySQL / PostgreSQL)
- Flyway-based database migrations
- Centralized exception handling and validation
- Clean layered architecture (Controller, Service, Repository)

---

## 🛠 Tech Stack

- **Backend:** Java 17, Spring Boot 3
- **Security:** Spring Security, JWT
- **Database:** MySQL / PostgreSQL, Spring Data JPA
- **Caching & Locking:** Redis (Redisson)
- **Messaging:** Apache Kafka
- **Payments:** Stripe API
- **Migrations:** Flyway
- **Build Tool:** Maven
- **Containerization:** Docker (optional)

---

## 🏗 Architecture Overview

- **Controller Layer:** Handles REST APIs
- **Service Layer:** Business logic & transactions
- **Repository Layer:** Database interactions
- **DTO Layer:** Request/Response abstraction
- **Entity Layer:** JPA domain models
- **Async Layer:** Kafka producers & consumers

---

## 🔄 Example Workflow (Order Placement)

1. User places an order via REST API
2. Inventory is locked using Redis to avoid race conditions
3. Order is saved transactionally
4. Kafka event is published for async processing
5. Payment is initiated via Stripe
6. Payment webhook updates order status

---

## 📊 Performance Simulation (Local Testing)

- Designed to handle **high-concurrency order placement**
- Redis locking prevents overselling
- Async Kafka consumers decouple heavy workflows
- Optimized database access patterns

> Note: Performance metrics are based on local simulations and architectural design assumptions.

---

## 🧪 Running the Project

```bash
mvn clean install
mvn spring-boot:run
