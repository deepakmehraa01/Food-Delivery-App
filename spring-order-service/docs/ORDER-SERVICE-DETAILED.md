# Order Service — Detailed Documentation

## 1. Purpose and scope

The Order Service owns the order lifecycle and its PostgreSQL data. It does not own product catalog data or inventory data. Product and inventory operations are performed through the existing Product Service using Spring Cloud OpenFeign.

Orders stop at `INVENTORY_RESERVED`. A later payment phase can extend the lifecycle to `PAYMENT_PENDING` and `CONFIRMED`.

## 2. Runtime topology

```text
Client
  |
  v
Order Service :8084
  |-- PostgreSQL order_db
  |
  `-- OpenFeign / Resilience4j
          |
          v
      Product Service :8083
          |-- Product catalog
          `-- Atomic inventory reservation
```

The Order Service never connects directly to `product_db`.

## 3. Verified Product Service contract

The integration was based on the source code in the existing Product Service, not on the reference contract alone.

| Operation | Method and path | Request | Response |
|---|---|---|---|
| Product details | `GET /api/products/{productId}` | Path variable | `ProductResponse` |
| Inventory availability | `GET /internal/api/inventory/{productId}` | Path variable | `InventoryResponse` |
| Reserve inventory | `POST /internal/api/inventory/reserve` | `InventoryReservationRequest` | `InventoryReservationResponse` |
| Release inventory | `POST /internal/api/inventory/release` | `OrderInventoryRequest` | `InventoryReservationResponse` |
| Confirm inventory | `POST /internal/api/inventory/confirm` | `OrderInventoryRequest` | Available in Product Service; not called in this phase |

### 3.1 Product response

```json
{
  "id": 1001,
  "sku": "PHONE-1001",
  "name": "Example Phone",
  "description": "Example product",
  "price": 74999.00,
  "status": "ACTIVE",
  "createdAt": "2026-10-08T10:00:00",
  "updatedAt": "2026-10-08T10:00:00"
}
```

Only `ACTIVE` products can be ordered. The client cannot submit or override the price.

### 3.2 Inventory response

```json
{
  "productId": 1001,
  "totalQuantity": 100,
  "reservedQuantity": 20,
  "availableQuantity": 80
}
```

The availability check is a pre-validation step. It is not the concurrency guarantee.

### 3.3 Reservation request and response

```json
{
  "orderId": 5001,
  "items": [
    { "productId": 1001, "quantity": 2 },
    { "productId": 1002, "quantity": 1 }
  ]
}
```

Product Service reserves all items atomically. Its implementation also makes a repeated reservation with the same order ID and identical item quantities safe and idempotent. A changed payload for an existing order ID is rejected.

## 4. Order lifecycle

```text
PENDING
   |
   | atomic reservation succeeds
   v
INVENTORY_RESERVED
   |
   | cancellation + confirmed release
   v
CANCELLED

PENDING -- reservation failure --> FAILED
```

`CONFIRMED` exists for the future payment workflow and is not assigned by this version.

### Cancellation rules

| Current status | Cancellation behavior |
|---|---|
| `PENDING` | Mark `CANCELLED` without an inventory release call |
| `INVENTORY_RESERVED` | Release inventory first, then mark `CANCELLED` |
| `FAILED` | Reject with `409 INVALID_ORDER_STATE` |
| `CANCELLED` | Return the existing cancelled order; do not release again |
| `CONFIRMED` | Reject until payment/refund workflow exists |

The `Order` entity has an optimistic-lock version field. Concurrent state changes therefore produce a conflict instead of silently overwriting one another.

## 5. Create-order flow

`POST /api/orders` executes the following sequence:

1. Validate `customerId`, item presence, product IDs, and positive quantities.
2. Reject duplicate product IDs in the same request.
3. Fetch each product from Product Service.
4. Reject missing, inactive, or deleted products.
5. Fetch inventory for each product.
6. Reject if requested quantity exceeds `availableQuantity`.
7. Calculate each line total and the order total using `BigDecimal`.
8. Create a local `PENDING` order with product name and price snapshots.
9. Persist the order to obtain its database ID.
10. Call Product Service's atomic reservation endpoint using that ID.
11. On success, update the local order to `INVENTORY_RESERVED`.
12. Return `201 CREATED`.

The availability check can become stale between steps 5 and 10. This is expected. The atomic Product Service reservation is the authoritative overselling guard for simultaneous orders.

## 6. Failure and compensation behavior

The local database transaction cannot roll back a remote Product Service transaction. The implementation therefore treats reservation failures and uncertain remote outcomes explicitly.

- Reserve calls are not blindly retried because they are non-idempotent from the caller's perspective.
- Product Service idempotency is based on `orderId` and the exact item payload.
- If reserve returns or times out with an exception, Order Service attempts a compensating release for the order ID.
- A timeout is treated as ambiguous: Product Service may have committed the reservation even if the response was lost.
- If compensation itself fails, the failure is logged with the order ID for operational reconciliation.
- The local order is persisted as `FAILED` when the reservation workflow fails.

Read calls use Resilience4j retry and circuit breaker policies. `ProductNotFoundException` is excluded from retry. Feign connect and read timeouts bound the duration of remote calls.

## 7. Project structure

```text
src/main/java/com/ecommerce/order/
├── OrderServiceApplication.java
├── controller/OrderController.java
├── client/ProductClient.java
├── config/OpenApiConfig.java
├── dto/
│   ├── request/
│   ├── response/
│   └── inventory/
├── entity/Order.java, OrderItem.java
├── enums/OrderStatus.java
├── exception/
├── mapper/OrderMapper.java
├── repository/OrderRepository.java
└── service/
    ├── OrderService.java
    ├── ProductIntegrationService.java
    └── impl/OrderServiceImpl.java
```

## 8. API reference

### Create order

`POST http://localhost:8084/api/orders`

Request:

```json
{
  "customerId": 101,
  "items": [
    { "productId": 1001, "quantity": 2 },
    { "productId": 1002, "quantity": 1 }
  ]
}
```

Success: `201 CREATED`

```json
{
  "orderId": 5001,
  "orderNumber": "ORD-2026-10-08-1770000000000-1234",
  "customerId": 101,
  "status": "INVENTORY_RESERVED",
  "totalAmount": 159997.00,
  "items": [
    {
      "productId": 1001,
      "productName": "Example Phone",
      "unitPrice": 74999.00,
      "quantity": 2,
      "totalPrice": 149998.00
    }
  ]
}
```

### Read orders

- `GET /api/orders/{orderId}`
- `GET /api/orders?page=0&size=20`
- `GET /api/orders/customer/{customerId}?page=0&size=20`

Collection endpoints return Spring's paginated JSON structure. Product prices are not re-read; response values come from order snapshots.

### Cancel order

`PATCH /api/orders/{orderId}/cancel`

For an inventory-reserved order, Product Service must confirm the release before the local status becomes `CANCELLED`.

## 9. Error responses

Errors use a standard shape:

```json
{
  "timestamp": "2026-10-08T16:30:00Z",
  "status": 409,
  "error": "CONFLICT",
  "code": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock for product 1001: requested 5, available 3",
  "path": "/api/orders"
}
```

Common codes:

| HTTP status | Code | Meaning |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | Invalid customer, item, or quantity |
| 404 | `ORDER_NOT_FOUND` | Order ID does not exist |
| 404 | `PRODUCT_NOT_FOUND` | Product Service could not find the product |
| 409 | `INSUFFICIENT_STOCK` | Pre-check found insufficient availability |
| 409 | `INVALID_ORDER_STATE` | Requested transition is not allowed |
| 409 | `CONCURRENT_UPDATE` | Optimistic-lock conflict |
| 503 | `PRODUCT_SERVICE_UNAVAILABLE` | Product Service timeout, circuit open, or remote failure |

## 10. Configuration

The default configuration is in `src/main/resources/application.yml`:

```yaml
server:
  port: 8084
services:
  product:
    url: http://localhost:8083
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
```

Use environment variables for credentials:

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-password"
```

`ddl-auto: update` is suitable for local training only. Use migrations such as Flyway or Liquibase before production deployment.

## 11. Running locally

1. Start PostgreSQL and create the database:

   ```sql
   CREATE DATABASE order_db;
   ```

2. Start Product Service on port `8083`.
3. Start Order Service:

   ```powershell
   mvn spring-boot:run
   ```

4. Check health at `http://localhost:8084/actuator/health`.
5. Open Swagger at `http://localhost:8084/swagger-ui.html`.

## 12. Testing

Run the automated tests:

```powershell
mvn test
```

Current automated coverage includes:

- successful order creation and reservation request construction
- duplicate product-item rejection
- invalid controller request handling

Recommended integration verification requires both services and PostgreSQL:

1. Create active products and inventory in Product Service.
2. Place a single-product order and verify `INVENTORY_RESERVED`.
3. Place simultaneous orders for the last available units and verify Product Service accepts only the inventory it can atomically reserve.
4. Cancel the reserved order and verify the reservation is released.
5. Stop Product Service and verify a placement returns `503 PRODUCT_SERVICE_UNAVAILABLE`.

## 13. Database model

The service creates these tables through JPA in local development:

- `orders`: order number, customer, total, status, timestamps, optimistic-lock version
- `order_items`: product ID, product name snapshot, unit price snapshot, quantity, line total, and order foreign key

Order numbers are unique in the database and include the current date, timestamp, and random suffix.

## 14. Future payment phase

The intended next lifecycle is:

```text
PENDING -> INVENTORY_RESERVED -> PAYMENT_PENDING -> CONFIRMED
```

Payment failure should compensate by calling Product Service release. Payment success should eventually call Product Service confirm. Those operations are deliberately outside this phase.
