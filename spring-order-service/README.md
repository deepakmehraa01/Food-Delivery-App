# Order Service

Order Service runs on port `8084`, stores order and item price/name snapshots in PostgreSQL, and integrates with the existing Product Service on `8083` through OpenFeign.

For the complete architecture, verified integration contract, lifecycle rules, compensation behavior, API reference, configuration, database model, and testing guide, see [docs/ORDER-SERVICE-DETAILED.md](docs/ORDER-SERVICE-DETAILED.md).

## Verified Product Service contract

- `GET /api/products/{productId}`
- `GET /internal/api/inventory/{productId}`
- `POST /internal/api/inventory/reserve` with `{ "orderId": 1, "items": [{"productId": 10,"quantity": 2}] }`
- `POST /internal/api/inventory/release` with `{ "orderId": 1 }`

Product Service reservation is atomic across items and idempotent for the same order and item payload. Order Service therefore performs an availability pre-check, then relies on the atomic reservation as the authoritative overselling guard. Reserve calls are not retried. Read calls use Resilience4j retry/circuit breaker; Feign connect/read timeouts bound remote waits.

## Architecture summary

The flow is:

```text
Validate request
    -> read product details and price
    -> read available inventory
    -> persist PENDING order
    -> atomically reserve inventory in Product Service
    -> update order to INVENTORY_RESERVED
```

The availability read is only a pre-check. The Product Service reservation is the concurrency-safe operation that prevents overselling. If a reservation response is uncertain, Order Service attempts an idempotent compensating release.

## Run

Create `order_db` in PostgreSQL, then set `DB_USERNAME` and `DB_PASSWORD` if needed:

```powershell
mvn spring-boot:run
```

Swagger UI: `http://localhost:8084/swagger-ui.html`

## APIs

- `POST /api/orders`
- `GET /api/orders/{orderId}`
- `GET /api/orders?page=0&size=20`
- `GET /api/orders/customer/{customerId}?page=0&size=20`
- `PATCH /api/orders/{orderId}/cancel`

Customer IDs are request-supplied for this phase. A later authenticated version should derive them from the security context. Orders stop at `INVENTORY_RESERVED`; payment and confirmation are intentionally not implemented.

## Example

```json
{
  "customerId": 101,
  "items": [{"productId": 1001, "quantity": 2}]
}
```

Successful placement returns `201 CREATED` with `INVENTORY_RESERVED`. Insufficient stock returns `409`; an unavailable Product Service returns `503`.

See the detailed documentation for standardized error responses, state transition rules, database behavior, failure handling, and the future payment workflow.

## Postman smoke test sequence

1. Start Product Service on `8083` and create active products with inventory.
2. Start this service with PostgreSQL `order_db` available.
3. `POST http://localhost:8084/api/orders` using the example body above; expect `201` and `INVENTORY_RESERVED`.
4. `GET http://localhost:8084/api/orders/{orderId}` and `GET /api/orders/customer/101?page=0&size=20` to verify snapshots and totals.
5. `GET http://localhost:8083/internal/api/inventory/{productId}`; `reservedQuantity` should include the order quantity.
6. `PATCH http://localhost:8084/api/orders/{orderId}/cancel`; expect `CANCELLED`, then verify Product Service inventory shows the released reservation.
7. Request more than available stock; expect `409` with `INSUFFICIENT_STOCK`.
8. Stop Product Service and post an order; after the configured Feign timeout/circuit behavior, expect `503` with `PRODUCT_SERVICE_UNAVAILABLE`.
