# Customer Query Service

Read side of the E-Commerce customer CQRS example, built with Java 25 and Spring Boot 3.

## Responsibility

This service exposes customer `GET` APIs only. It does not register, update, delete, authenticate, generate JWTs, or modify customer data. The entity is marked Hibernate `@Immutable`, the service uses `@Transactional(readOnly = true)`, and the datasource uses `ddl-auto: none`.

The first learning phase uses the shared `customer_db` for simplicity:

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

The command service owns writes; this service only reads. The query service never changes customer rows or customer status.

### Future true CQRS flow

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

The future design separates the write and read databases. Kafka synchronization is intentionally not implemented in this learning phase.

## Run

Requirements: Java 25, Maven, and PostgreSQL with the existing `customers` table.

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
mvn spring-boot:run
```

The service starts on `http://localhost:8087`. Both customer services support `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; use the same database configuration for both. The default URL is `jdbc:postgresql://localhost:5432/customer_db`, username is `postgres`, and password is `root`.

Swagger UI: `http://localhost:8087/swagger-ui.html`  
Health: `http://localhost:8087/actuator/health`  
OpenAPI JSON: `http://localhost:8087/v3/api-docs`

## API examples

```text
GET /api/customers/101
GET /api/customers/customer-number/CUS-100101
GET /api/customers/email?email=anand@example.com
GET /api/customers?page=0&size=10&sortBy=createdAt&direction=desc
GET /api/customers/status/ACTIVE?page=0&size=10
GET /api/customers/search?keyword=Anand&status=ACTIVE&page=0&size=10&sortBy=createdAt&direction=desc
```

Allowed sort fields are `customerNumber`, `firstName`, `lastName`, `email`, `createdAt`, and `updatedAt`. Page size must be 1–100. Normal lookups, list queries, and searches hide `DELETED` customers unless `status=DELETED` is explicitly requested.

## Testing scenarios

### Service-layer scenarios

- Get a customer successfully by ID, customer number, and email.
- Return `CustomerNotFoundException` when a customer does not exist.
- Treat a `DELETED` customer as not found for normal lookup methods.
- Return paginated customers with default and requested sorting.
- Filter customers by `ACTIVE`, `INACTIVE`, and explicitly requested `DELETED` status.
- Search case-insensitively across first name, last name, email, customer number, and phone.
- Combine keyword search, status filtering, pagination, and sorting.
- Return an empty page when no search results match.
- Reject negative page indexes, sizes below 1, and sizes above 100.
- Reject unsupported sort fields and invalid sort directions.

### Controller/API scenarios

```text
GET /api/customers/101                         -> 200 OK
GET /api/customers/999                         -> 404 NOT FOUND
GET /api/customers?page=0&size=10              -> 200 OK
GET /api/customers?page=-1                     -> 400 BAD REQUEST
GET /api/customers?size=101                    -> 400 BAD REQUEST
GET /api/customers/status/ACTIVE               -> 200 OK
GET /api/customers/status/UNKNOWN              -> 400 BAD REQUEST
GET /api/customers/search?keyword=Anand        -> 200 OK
GET /api/customers/search?keyword=NoMatch      -> 200 OK with empty content
POST /api/customers                            -> 405 METHOD NOT ALLOWED
PUT /api/customers/101                         -> 405 METHOD NOT ALLOWED
DELETE /api/customers/101                      -> 405 METHOD NOT ALLOWED
```

### Test commands

```powershell
mvn test
```

The automated tests use JUnit 5, Mockito, and MockMvc. They cover service lookup behavior, deleted-customer handling, pagination validation, controller success responses, not-found handling, invalid pagination, and search routing. Manual API testing can be performed with Swagger UI or Postman using the endpoints above.
