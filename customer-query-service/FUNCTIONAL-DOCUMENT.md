# Customer Query Service

## Functional Document

**Application:** E-Commerce Microservices  
**Service:** Customer Query Service  
**Version:** 1.0  
**Recommended Port:** 8087  
**Pattern:** CQRS query/read side  
**Technology:** Java 25, Spring Boot 3, PostgreSQL

---

## 1. Purpose

Customer Query Service provides read-only access to customer information for the E-Commerce application.

The service is responsible for:

- Retrieving customers by ID, customer number, or email
- Listing customers with pagination
- Sorting customer results
- Filtering customers by status
- Searching customer records
- Returning consistent response and error formats

The service must never create, update, or delete customer data.

---

## 2. Scope

### In scope

- Customer read operations using HTTP `GET`
- Customer response DTOs
- Database-level filtering and searching
- Pagination with a maximum page size of 100
- Supported sorting fields and directions
- Soft-deleted customer visibility rules
- OpenAPI documentation
- Health and information actuator endpoints
- Unit and controller testing

### Out of scope

- Customer registration
- Customer updates
- Customer deletion
- Login and authentication
- JWT generation or validation
- Kafka event synchronization
- Database schema creation or modification
- Eureka service discovery
- Docker configuration
- Micrometer or OpenTelemetry tracing

---

## 3. CQRS Context

CQRS separates write responsibilities from read responsibilities.

```text
                         +----------------------+
                         |       API Client     |
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         |      API Gateway      |
                         +----------+-----------+
                                    |
                    +---------------+---------------+
                    |                               |
                    v                               v
        +--------------------------+    +--------------------------+
        | Customer Command Service |    |  Customer Query Service  |
        |        :8082             |    |          :8087            |
        |    POST / PUT / DELETE   |    |           GET             |
        +------------+-------------+    +------------+-------------+
                     |                               |
                     +---------------+---------------+
                                     v
                         +----------------------+
                         |     customer_db      |
                         |   shared learning   |
                         |       database       |
                         +----------------------+
```

The current implementation uses a shared database as a simplified CQRS learning stage. The Command Service owns write operations, while the Query Service performs reads only.

The Query Service must not modify customer rows, customer status, or database schema.

### Future CQRS evolution

```text
Customer Command Service
          |
          | CUSTOMER_CREATED
          | CUSTOMER_UPDATED
          | CUSTOMER_DELETED
          v
        Kafka
          |
          v
 Customer Query Consumer
          |
          v
   customer_read_db
          ^
          |
 Customer Query Service
          |
        GET APIs
```

The future implementation will maintain a separate read database populated from customer domain events.

---

## 4. Customer Read Model

The service reads the following customer fields:

| Field | Type | Description |
|---|---|---|
| `id` | Long | Unique customer identifier |
| `customerNumber` | String | Business customer number |
| `firstName` | String | Customer first name |
| `lastName` | String | Customer last name |
| `email` | String | Customer email address |
| `phone` | String | Customer phone number |
| `status` | CustomerStatus | `ACTIVE`, `INACTIVE`, or `DELETED` |
| `createdAt` | LocalDateTime | Customer creation timestamp |
| `updatedAt` | LocalDateTime | Last update timestamp |

The JPA entity is `CustomerReadModel`. It is not exposed directly by the controller.

---

## 5. Customer Status Rules

Supported statuses:

- `ACTIVE`
- `INACTIVE`
- `DELETED`

Normal customer lookup and list operations exclude `DELETED` records.

Examples:

- `GET /api/customers/101` returns `404` if customer 101 is deleted.
- `GET /api/customers` excludes deleted customers.
- `GET /api/customers/search?keyword=Anand` excludes deleted customers.
- `GET /api/customers/status/DELETED` explicitly returns deleted records for reporting use cases.

The Query Service does not change a customer's status.

---

## 6. API Functional Requirements

Base URL:

```text
http://localhost:8087/api/customers
```

### 6.1 Get customer by ID

```http
GET /api/customers/{customerId}
```

Example:

```http
GET /api/customers/101
```

Responses:

- `200 OK` when the customer exists and is not deleted
- `404 NOT FOUND` when the customer does not exist or is deleted
- `400 BAD REQUEST` when the ID is not numeric

### 6.2 Get customer by customer number

```http
GET /api/customers/customer-number/{customerNumber}
```

Example:

```http
GET /api/customers/customer-number/CUS-100101
```

Responses:

- `200 OK` when the customer exists
- `404 NOT FOUND` when the customer does not exist or is deleted

### 6.3 Get customer by email

```http
GET /api/customers/email?email=anand@example.com
```

Email matching is case-insensitive.

Responses:

- `200 OK` when the customer exists
- `404 NOT FOUND` when the customer does not exist or is deleted
- `400 BAD REQUEST` when the email parameter is missing

### 6.4 Get all customers

```http
GET /api/customers?page=0&size=10&sortBy=createdAt&direction=desc
```

Defaults:

| Parameter | Default |
|---|---:|
| `page` | `0` |
| `size` | `10` |
| `sortBy` | `createdAt` |
| `direction` | `desc` |

The result is paginated and excludes deleted customers.

### 6.5 Get customers by status

```http
GET /api/customers/status/{status}?page=0&size=10
```

Examples:

```http
GET /api/customers/status/ACTIVE?page=0&size=10
GET /api/customers/status/INACTIVE?page=0&size=10
GET /api/customers/status/DELETED?page=0&size=10
```

An unsupported status returns `400 BAD REQUEST`.

### 6.6 Search customers

```http
GET /api/customers/search?keyword=Anand&page=0&size=10&sortBy=createdAt&direction=desc
```

The keyword is searched at the database level across:

- `firstName`
- `lastName`
- `email`
- `customerNumber`
- `phone`

Matching is case-insensitive where supported by the database query.

### 6.7 Combined search and filtering

```http
GET /api/customers/search?keyword=Anand&status=ACTIVE&page=0&size=10&sortBy=createdAt&direction=desc
```

Search, status filtering, pagination, and sorting are combined in one database query. The service must not load the full customer table into Java memory for filtering.

---

## 7. Pagination and Sorting Rules

Pagination rules:

- `page` must be zero or greater
- `size` must be between 1 and 100
- Collection endpoints must always return a paginated response
- Spring Data `Page` must not be exposed directly as the public API contract

Allowed sorting fields:

- `customerNumber`
- `firstName`
- `lastName`
- `email`
- `createdAt`
- `updatedAt`

Allowed directions:

- `asc`
- `desc`

Unsupported sorting fields must return `400 BAD REQUEST`. Arbitrary property names must not be passed directly to the database.

---

## 8. Response Contracts

### Customer response

```json
{
  "id": 101,
  "customerNumber": "CUS-100101",
  "firstName": "Anand",
  "lastName": "Kumar",
  "email": "anand@example.com",
  "phone": "9876543210",
  "status": "ACTIVE",
  "createdAt": "2026-10-07T10:30:00",
  "updatedAt": null
}
```

### Page response

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 35,
  "totalPages": 4,
  "first": true,
  "last": false
}
```

---

## 9. Error Handling

The service uses a global `@RestControllerAdvice`.

Example not-found response:

```json
{
  "timestamp": "2026-10-07T10:30:00",
  "status": 404,
  "error": "NOT_FOUND",
  "code": "CUSTOMER_NOT_FOUND",
  "message": "Customer not found with id: 101",
  "path": "/api/customers/101"
}
```

Common error statuses:

| Status | Meaning |
|---:|---|
| `400` | Invalid ID, status, pagination, sorting, or request parameter |
| `404` | Customer not found or hidden because it is deleted |
| `405` | Unsupported HTTP method such as POST, PUT, PATCH, or DELETE |
| `500` | Unexpected server error |

---

## 10. Non-Functional Requirements

- Use constructor injection for all dependencies.
- Use DTOs instead of exposing JPA entities.
- Use `@Transactional(readOnly = true)` for query service operations.
- Use SLF4J logging; do not use `System.out.println`.
- Keep controllers thin and place query logic in services and repositories.
- Use database-level filtering, pagination, and sorting.
- Configure Hibernate with `ddl-auto: none`.
- Do not log passwords, authentication tokens, or unnecessary personal data.
- Do not add write endpoints to this service.

---

## 11. Testing Requirements

### Service tests

- Successful lookup by ID
- Missing customer by ID
- Successful lookup by customer number
- Missing customer by customer number
- Successful lookup by email
- Missing customer by email
- Successful paginated customer retrieval
- Requested sorting behavior
- Status filtering
- Search with matching results
- Search with no results
- Combined keyword and status filtering
- Invalid page index
- Invalid page size
- Invalid sort field and direction
- Deleted customer not returned by normal lookup

### Controller tests

- `GET /api/customers/{id}` returns `200`
- Missing customer returns `404`
- Collection endpoint returns `200`
- Search endpoint returns `200`
- Status endpoint returns `200`
- Invalid page returns `400`
- Invalid size returns `400`
- Invalid status returns `400`
- Unsupported write methods are not exposed

Run the automated tests with:

```powershell
mvn test
```

Manual testing is available through Swagger UI:

```text
http://localhost:8087/swagger-ui.html
```

---

## 12. Configuration

```yaml
server:
  port: 8087

spring:
  application:
    name: customer-query-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/customer_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: none
```

The database schema is owned by the Command Service or a separate migration process. The Query Service must not create or update the schema.

---

## 13. Acceptance Criteria

The implementation is functionally complete when:

1. All documented `GET` endpoints are available on port 8087.
2. No customer POST, PUT, PATCH, or DELETE endpoint exists.
3. Customer entities are not returned directly from controllers.
4. Pagination and sorting are applied to all collection endpoints.
5. Invalid pagination and sorting parameters return `400`.
6. Missing customers return `404` with the documented error format.
7. Deleted customers are hidden by default.
8. Search filtering is performed by the repository/database layer.
9. Swagger documents the query APIs.
10. Actuator health and info endpoints are available.
11. Unit and controller tests pass with `mvn test`.

---

## 14. Relationship to Customer Command Service

```text
customer-command-service
        |
        | Owns customer writes
        v
 customer_db / write model

customer-query-service
        |
        | Owns customer reads
        v
 customer_db / read model
```

Together, the two services demonstrate the separation of command and query responsibilities:

```text
Customer Command Service + Customer Query Service = CQRS Customer Management
```

The next architectural step is to replace the shared database with a separate read database synchronized by Kafka customer events.
