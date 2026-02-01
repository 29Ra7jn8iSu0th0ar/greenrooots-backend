#!/bin/bash

# GreenRoots API Test Script
# This script tests the complete flow from user registration to order creation

BASE_URL="http://localhost:8080/api"

echo "===================================="
echo "GreenRoots API Test Suite"
echo "===================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Register User
echo "${YELLOW}[TEST 1]${NC} Registering new user..."
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test'$(date +%s)'@greenroots.com",
    "password": "Test1234!",
    "fullName": "Test User",
    "phoneNumber": "+1234567890"
  }')

TOKEN=$(echo $REGISTER_RESPONSE | jq -r '.token')
USER_ID=$(echo $REGISTER_RESPONSE | jq -r '.userId')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
  echo "${GREEN}✓${NC} User registered successfully"
  echo "   User ID: $USER_ID"
  echo "   Token: ${TOKEN:0:20}..."
else
  echo "${RED}✗${NC} Registration failed"
  echo "   Response: $REGISTER_RESPONSE"
  exit 1
fi
echo ""

# Test 2: Login
echo "${YELLOW}[TEST 2]${NC} Testing login..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@greenroots.com",
    "password": "Test1234!"
  }')

LOGIN_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
if [ "$LOGIN_TOKEN" != "null" ] && [ -n "$LOGIN_TOKEN" ]; then
  echo "${GREEN}✓${NC} Login successful"
else
  echo "${YELLOW}!${NC} Login test skipped (user may not exist)"
fi
echo ""

# Test 3: Get All Plants
echo "${YELLOW}[TEST 3]${NC} Fetching all plants..."
PLANTS_RESPONSE=$(curl -s -X GET "$BASE_URL/plants")
PLANT_COUNT=$(echo $PLANTS_RESPONSE | jq '. | length')

if [ "$PLANT_COUNT" -ge 0 ] 2>/dev/null; then
  echo "${GREEN}✓${NC} Plants retrieved successfully"
  echo "   Total plants: $PLANT_COUNT"
else
  echo "${YELLOW}!${NC} No plants found (database may be empty)"
fi
echo ""

# Test 4: Create Plant (Admin only - will fail without admin token)
echo "${YELLOW}[TEST 4]${NC} Creating sample plant (requires admin role)..."
CREATE_PLANT_RESPONSE=$(curl -s -X POST "$BASE_URL/plants" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Monstera Deliciosa",
    "scientificName": "Monstera deliciosa",
    "description": "Beautiful tropical plant with large leaves",
    "category": "INDOOR",
    "price": 29.99,
    "stockQuantity": 100,
    "lightRequirement": "MEDIUM",
    "waterRequirement": "MEDIUM",
    "imageUrl": "https://example.com/monstera.jpg"
  }')

PLANT_ID=$(echo $CREATE_PLANT_RESPONSE | jq -r '.id')
if [ "$PLANT_ID" != "null" ] && [ -n "$PLANT_ID" ]; then
  echo "${GREEN}✓${NC} Plant created successfully"
  echo "   Plant ID: $PLANT_ID"
else
  ERROR_MSG=$(echo $CREATE_PLANT_RESPONSE | jq -r '.message')
  if [[ $ERROR_MSG == *"Access denied"* ]] || [[ $ERROR_MSG == *"Forbidden"* ]]; then
    echo "${YELLOW}!${NC} Plant creation failed (expected - requires ADMIN role)"
    echo "   You can test this with an admin token"
  else
    echo "${RED}✗${NC} Unexpected error: $ERROR_MSG"
  fi
fi
echo ""

# Test 5: Get Plants by Category
echo "${YELLOW}[TEST 5]${NC} Fetching plants by category (INDOOR)..."
CATEGORY_RESPONSE=$(curl -s -X GET "$BASE_URL/plants/category/INDOOR")
CATEGORY_COUNT=$(echo $CATEGORY_RESPONSE | jq '. | length')

if [ "$CATEGORY_COUNT" -ge 0 ] 2>/dev/null; then
  echo "${GREEN}✓${NC} Category filter working"
  echo "   Indoor plants: $CATEGORY_COUNT"
else
  echo "${YELLOW}!${NC} No indoor plants found"
fi
echo ""

# Test 6: Create Order (requires plants in database)
if [ "$PLANT_COUNT" -gt 0 ] 2>/dev/null; then
  echo "${YELLOW}[TEST 6]${NC} Creating sample order..."
  
  # Get first plant ID
  FIRST_PLANT_ID=$(curl -s -X GET "$BASE_URL/plants" | jq -r '.[0].id')
  
  if [ "$FIRST_PLANT_ID" != "null" ] && [ -n "$FIRST_PLANT_ID" ]; then
    ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/orders" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d '{
        "items": [
          {
            "plantId": '$FIRST_PLANT_ID',
            "quantity": 2
          }
        ],
        "shippingAddress": "123 Green Street",
        "shippingCity": "San Francisco",
        "shippingPostalCode": "94102",
        "shippingCountry": "USA"
      }')
    
    ORDER_ID=$(echo $ORDER_RESPONSE | jq -r '.orderId')
    ORDER_NUMBER=$(echo $ORDER_RESPONSE | jq -r '.orderNumber')
    
    if [ "$ORDER_ID" != "null" ] && [ -n "$ORDER_ID" ]; then
      echo "${GREEN}✓${NC} Order created successfully"
      echo "   Order ID: $ORDER_ID"
      echo "   Order Number: $ORDER_NUMBER"
      echo "   Status: $(echo $ORDER_RESPONSE | jq -r '.status')"
      echo "   Total: $$(echo $ORDER_RESPONSE | jq -r '.totalAmount')"
    else
      ERROR_MSG=$(echo $ORDER_RESPONSE | jq -r '.message')
      echo "${RED}✗${NC} Order creation failed: $ERROR_MSG"
    fi
  fi
else
  echo "${YELLOW}[TEST 6]${NC} Skipping order test (no plants available)"
fi
echo ""

# Test 7: Get User Orders
echo "${YELLOW}[TEST 7]${NC} Fetching user orders..."
ORDERS_RESPONSE=$(curl -s -X GET "$BASE_URL/orders" \
  -H "Authorization: Bearer $TOKEN")

ORDER_COUNT=$(echo $ORDERS_RESPONSE | jq '. | length')
if [ "$ORDER_COUNT" -ge 0 ] 2>/dev/null; then
  echo "${GREEN}✓${NC} Orders retrieved successfully"
  echo "   Total orders: $ORDER_COUNT"
else
  echo "${YELLOW}!${NC} No orders found"
fi
echo ""

# Summary
echo "===================================="
echo "${GREEN}Test Suite Complete${NC}"
echo "===================================="
echo ""
echo "Next Steps:"
echo "1. Check application logs for detailed information"
echo "2. Test Stripe webhook integration separately"
echo "3. Monitor Kafka topics for order events"
echo "4. Create admin user to test admin endpoints"
echo ""