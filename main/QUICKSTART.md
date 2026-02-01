# GreenRoots - Quick Start Guide

## Prerequisites Check

Before starting, ensure you have:
- ✅ Java 17 or higher: `java -version`
- ✅ Maven 3.8+: `mvn -version`
- ✅ Docker & Docker Compose: `docker --version && docker-compose --version`

## 5-Minute Setup

### Step 1: Start Infrastructure (2 minutes)

```bash
cd greenroots-backend
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- MySQL (port 3306)
- Redis (port 6379)
- Kafka + Zookeeper (ports 9092, 2181)

**Verify all services are running:**
```bash
docker-compose ps
```

All services should show "Up" status.

### Step 2: Build Application (1 minute)

```bash
mvn clean install -DskipTests
```

### Step 3: Run Application (1 minute)

**With PostgreSQL (default):**
```bash
mvn spring-boot:run
```

**Or with MySQL:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

**Application ready when you see:**
```
Started GreenRootsApplication in X.XXX seconds
```

Access: `http://localhost:8080/api`

### Step 4: Test APIs (1 minute)

**Option A: Using provided test script**
```bash
./test-api.sh
```

**Option B: Manual test with curl**
```bash
# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@greenroots.com",
    "password": "Test1234!",
    "fullName": "Test User",
    "phoneNumber": "+1234567890"
  }'

# Get all plants
curl -X GET http://localhost:8080/api/plants
```

## Common Issues & Solutions

### Issue: Port already in use
```bash
# Check what's using the port
lsof -i :8080

# Kill the process or change port in application.yml
```

### Issue: Docker services not starting
```bash
# Stop all services
docker-compose down

# Remove volumes and restart
docker-compose down -v
docker-compose up -d
```

### Issue: Database connection failed
```bash
# Check if database is ready
docker-compose logs postgres  # or mysql

# Wait a few seconds for database initialization
```

### Issue: Kafka connection failed
```bash
# Kafka takes longer to start
docker-compose logs kafka

# Wait until you see: "Kafka Server started"
```

## Next Steps

### 1. Create Sample Data

First, you need an admin user to create plants. Modify the first registered user to admin in the database:

**PostgreSQL:**
```bash
docker exec -it greenroots-postgres psql -U greenroots_user -d greenroots
UPDATE users SET role = 'ADMIN' WHERE email = 'test@greenroots.com';
\q
```

**MySQL:**
```bash
docker exec -it greenroots-mysql mysql -u greenroots_user -pgreenroots_pass greenroots
UPDATE users SET role = 'ADMIN' WHERE email = 'test@greenroots.com';
exit
```

Then create plants:
```bash
# Get token from login/register response
TOKEN="your_jwt_token_here"

curl -X POST http://localhost:8080/api/plants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Snake Plant",
    "scientificName": "Sansevieria trifasciata",
    "description": "Hardy, low-maintenance indoor plant",
    "category": "INDOOR",
    "price": 19.99,
    "stockQuantity": 100,
    "lightRequirement": "LOW",
    "waterRequirement": "LOW",
    "imageUrl": "https://example.com/snake-plant.jpg"
  }'
```

### 2. Test Order Flow

```bash
# Create an order (requires authentication)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "items": [{"plantId": 1, "quantity": 2}],
    "shippingAddress": "123 Main St",
    "shippingCity": "San Francisco",
    "shippingPostalCode": "94102",
    "shippingCountry": "USA"
  }'
```

### 3. Monitor Kafka Events

```bash
# In a new terminal, watch Kafka topics
docker exec -it greenroots-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.created \
  --from-beginning
```

### 4. Setup Stripe (Optional)

1. Get test keys from https://dashboard.stripe.com/test/apikeys
2. Update `application.yml`:
   ```yaml
   app:
     stripe:
       api-key: sk_test_your_key
       webhook-secret: whsec_your_secret
   ```
3. Restart application

### 5. Import Postman Collection

Import `GreenRoots-Postman-Collection.json` into Postman for easy API testing.

## Development Workflow

### Running in IDE

**IntelliJ IDEA:**
1. Import as Maven project
2. Right-click `GreenRootsApplication.java`
3. Select "Run"

**VS Code:**
1. Install "Extension Pack for Java"
2. Open project folder
3. Press F5 to run

### Hot Reload

The application supports hot reload. Code changes automatically restart the application.

### Database Migrations

Flyway runs automatically on startup. To create new migrations:

```bash
# Create new migration file
touch src/main/resources/db/migration/postgres/V6__Your_Migration.sql

# Flyway will apply it on next startup
```

### Logs

Application logs are printed to console. Key logs to watch:
- User registration/login
- Order creation with lock acquisition
- Payment processing
- Kafka event publishing

### Testing Distributed Locks

Test race conditions by creating multiple orders simultaneously:

```bash
# Run in multiple terminals at once
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/orders \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{...order data...}' &
done
```

## Production Deployment

### Environment Variables

Create `.env` file:
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/greenroots
SPRING_DATASOURCE_USERNAME=prod_user
SPRING_DATASOURCE_PASSWORD=secure_password
SPRING_DATA_REDIS_HOST=prod-redis
SPRING_KAFKA_BOOTSTRAP_SERVERS=prod-kafka:9092
APP_JWT_SECRET=your-256-bit-secret-key
APP_STRIPE_API_KEY=sk_live_your_key
APP_STRIPE_WEBHOOK_SECRET=whsec_your_secret
```

### Build JAR

```bash
mvn clean package -DskipTests
```

JAR location: `target/greenroots-backend-1.0.0.jar`

### Run JAR

```bash
java -jar target/greenroots-backend-1.0.0.jar \
  --spring.profiles.active=postgres
```

### Docker Build (Optional)

Create `Dockerfile`:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/greenroots-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t greenroots-backend .
docker run -p 8080:8080 greenroots-backend
```

## Troubleshooting Commands

```bash
# View application logs
tail -f logs/spring.log

# Check database connections
docker-compose logs postgres | grep "database system is ready"

# Check Redis
docker exec -it greenroots-redis redis-cli ping

# Check Kafka topics
docker exec -it greenroots-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Restart specific service
docker-compose restart postgres

# Clean restart everything
docker-compose down -v
docker-compose up -d
mvn clean install -DskipTests
mvn spring-boot:run
```

## Support & Documentation

- **Full Documentation**: See `README.md`
- **Architecture Details**: See `ARCHITECTURE.md`
- **API Testing**: Use `test-api.sh` or Postman collection
- **Logs**: Check console output or `logs/` directory

## Quick Reference

### Default Ports
- Application: 8080
- PostgreSQL: 5432
- MySQL: 3306
- Redis: 6379
- Kafka: 9092
- Zookeeper: 2181

### Default Credentials
- **PostgreSQL**: greenroots_user / greenroots_pass
- **MySQL**: greenroots_user / greenroots_pass
- **Database Name**: greenroots

### Plant Categories
- INDOOR, OUTDOOR, SUCCULENT, HERB, FLOWER, TREE, VINE

### Light Requirements
- LOW, MEDIUM, HIGH, FULL_SUN, PARTIAL_SHADE

### Water Requirements
- LOW, MEDIUM, HIGH

### Order Status
- PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED

### Payment Status
- PENDING, PROCESSING, SUCCEEDED, FAILED, REFUNDED

---

**Need Help?** Check logs, verify all Docker services are running, and ensure correct database profile is active.
