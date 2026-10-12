# Customer Command Service

Customer Command Service is the write side of customer management for the E-Commerce Microservices application. It owns customer registration, updates, and soft deletion.

Authentication, login, and JWT generation are intentionally handled by `auth-service`. This service does not implement authentication or customer query APIs.

## Responsibilities

| Operation | Endpoint | Result |
|---|---|---|
| Register customer | `POST /api/customers` | Creates an `ACTIVE` customer |
| Update customer | `PUT /api/customers/{customerId}` | Updates an existing customer |
| Soft delete customer | `DELETE /api/customers/{customerId}` | Changes status to `DELETED` |

The service runs on port `8082` and uses the PostgreSQL database `customer_db`.

## Technology stack

- Java 25
- Spring Boot 3.4.5
- Spring Web
- Spring Data JPA
- Jakarta Validation
- PostgreSQL
- Springdoc OpenAPI / Swagger UI
- Spring Boot Actuator
- JUnit 5, Mockito, and MockMvc
- Lombok for boilerplate reduction

The project does not use Lombok-generated business logic, Eureka, Docker, Micrometer, OpenTelemetry, or Spring Cloud Config Server.

## Architecture

```text
Client / API Gateway
        |
        v
CustomerCommandController
        |
        v
CustomerCommandService
        |
        +-- duplicate-email validation
        +-- customer-number generation
        +-- customer status transitions
        |
        v
CustomerRepository
        |
        v
PostgreSQL: customer_db
```

The application uses DTOs at the REST boundary, a mapper between DTOs and entities, constructor injection, service-layer transactions, and centralized exception handling. JPA entities are never returned directly from the controller.

## Project structure

```text
src/main/java/com/ecommerce/customercommand/
├── config/       OpenAPI configuration
├── controller/   REST endpoints
├── dto/          Request and response records
├── entity/       Customer JPA entity
├── enums/        CustomerStatus
├── exception/    Domain exceptions and global handler
├── mapper/       Entity/DTO mapping
├── repository/   Spring Data repository
└── service/      Command service interface and implementation
```

## Customer data model

| Field | Type | Rules |
|---|---|---|
| `id` | `Long` | Database identifier |
| `customerNumber` | `String` | Unique business identifier such as `CUS-100001` |
| `firstName` | `String` | Required, maximum 100 characters |
| `lastName` | `String` | Required, maximum 100 characters |
| `email` | `String` | Required, valid format, unique |
| `phone` | `String` | Required, 7–20 phone characters |
| `status` | `CustomerStatus` | `ACTIVE`, `INACTIVE`, or `DELETED` |
| `createdAt` | `LocalDateTime` | Set during registration |
| `updatedAt` | `LocalDateTime` | Set during update/delete |

Deleted records remain in the database. A delete operation only changes their status to `DELETED`.

## Prerequisites

- Java 25 installed
- Maven installed or Maven Wrapper available
- PostgreSQL running locally
- An empty PostgreSQL database named `customer_db`

Create the database as a PostgreSQL administrator:

```sql
CREATE DATABASE customer_db;
```

The same setup is available in [`database/setup.sql`](database/setup.sql).

## Configuration

Default configuration is in [`src/main/resources/application.yml`](src/main/resources/application.yml):

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/customer_db
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:root}
```

Optional environment variables:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/customer_db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `root` |

The learning configuration uses `spring.jpa.hibernate.ddl-auto=update`. Use Flyway or Liquibase migrations for production deployments.

## Running the service

```bash
mvn spring-boot:run
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/customer-command-service-0.0.1-SNAPSHOT.jar
```

Useful URLs:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI document: `http://localhost:8082/v3/api-docs`
- Health: `http://localhost:8082/actuator/health`
- Info: `http://localhost:8082/actuator/info`

## API contract

### 1. Register customer

```http
POST /api/customers
Content-Type: application/json
```

Request:

```json
{
  "firstName": "Anand",
  "lastName": "Kumar",
  "email": "anand@example.com",
  "phone": "9876543210"
}
```

Successful response: `201 Created`

```json
{
  "id": 1,
  "customerNumber": "CUS-100001",
  "firstName": "Anand",
  "lastName": "Kumar",
  "email": "anand@example.com",
  "phone": "9876543210",
  "status": "ACTIVE",
  "createdAt": "2026-10-07T10:30:00",
  "updatedAt": "2026-10-07T10:30:00"
}
```

### 2. Update customer

```http
PUT /api/customers/{customerId}
Content-Type: application/json
```

Request:

```json
{
  "firstName": "Anand Kumar",
  "lastName": "J",
  "email": "anand.kumar@example.com",
  "phone": "9876543210"
}
```

Successful response: `200 OK` with the updated customer response. Clients cannot change `id`, `customerNumber`, `createdAt`, or `status` through this endpoint.

### 3. Soft delete customer

```http
DELETE /api/customers/{customerId}
```

Successful response: `204 No Content`. The record remains in the database with `status = DELETED`.

## Error response format

All handled errors use this structure:

```json
{
  "timestamp": "2026-10-07T10:30:00",
  "status": 409,
  "error": "CONFLICT",
  "code": "CUSTOMER_EMAIL_ALREADY_EXISTS",
  "message": "Customer already exists with email anand@example.com",
  "path": "/api/customers",
  "errors": null
}
```

Validation errors include field-level messages in `errors`:

```json
{
  "status": 400,
  "error": "BAD REQUEST",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "errors": {
    "firstName": "First name is required",
    "email": "Email must be valid"
  }
}
```

## API testing with curl

The commands below use `curl.exe`, which avoids the PowerShell alias for `curl` on Windows.

### Register: successful request

```powershell
curl.exe -i -X POST http://localhost:8082/api/customers `
  -H "Content-Type: application/json" `
  -d '{"firstName":"Anand","lastName":"Kumar","email":"anand@example.com","phone":"9876543210"}'
```

Expected: `201 Created`, `status = ACTIVE`, and a generated `customerNumber`.

### Register: duplicate email

Run the registration request again with the same email.

Expected: `409 Conflict`, code `CUSTOMER_EMAIL_ALREADY_EXISTS`.

### Register: invalid request

```powershell
curl.exe -i -X POST http://localhost:8082/api/customers `
  -H "Content-Type: application/json" `
  -d '{"firstName":"","lastName":"Kumar","email":"not-an-email","phone":"12"}'
```

Expected: `400 Bad Request`, code `VALIDATION_ERROR`, with field errors for `firstName`, `email`, and `phone`.

### Update: successful request

Replace `1` with the ID returned by registration:

```powershell
curl.exe -i -X PUT http://localhost:8082/api/customers/1 `
  -H "Content-Type: application/json" `
  -d '{"firstName":"Anand Kumar","lastName":"J","email":"anand.kumar@example.com","phone":"9876543210"}'
```

Expected: `200 OK`; `id`, `customerNumber`, and `createdAt` remain unchanged.

### Update: customer not found

```powershell
curl.exe -i -X PUT http://localhost:8082/api/customers/999999 `
  -H "Content-Type: application/json" `
  -d '{"firstName":"Anand","lastName":"Kumar","email":"unknown@example.com","phone":"9876543210"}'
```

Expected: `404 Not Found`, code `CUSTOMER_NOT_FOUND`.

### Update: duplicate email

Create two customers, then update the second customer with the first customer's email.

Expected: `409 Conflict`, code `CUSTOMER_EMAIL_ALREADY_EXISTS`; the second customer remains unchanged.

### Update: invalid request

Send an empty first name, malformed email, missing last name, or invalid phone.

Expected: `400 Bad Request`, code `VALIDATION_ERROR`; the service must not update the record.

### Delete: successful soft delete

```powershell
curl.exe -i -X DELETE http://localhost:8082/api/customers/1
```

Expected: `204 No Content`. Verify the database row remains and has `status = DELETED`.

### Delete: customer not found

```powershell
curl.exe -i -X DELETE http://localhost:8082/api/customers/999999
```

Expected: `404 Not Found`, code `CUSTOMER_NOT_FOUND`.

### Delete: already deleted

Run the successful delete request twice for the same customer.

Expected on the second request: `409 Conflict`, code `INVALID_CUSTOMER_OPERATION`.

### Delete: invalid path variable

```powershell
curl.exe -i -X DELETE http://localhost:8082/api/customers/0
curl.exe -i -X DELETE http://localhost:8082/api/customers/not-a-number
```

Expected: a client error; `0` fails positive-ID validation and a non-numeric ID fails path conversion. These malformed path cases are rejected before service execution.

## Complete API scenario matrix

| # | Scenario | Request | Expected status | Expected result |
|---:|---|---|---:|---|
| 1 | Register valid customer | Valid `POST` body | 201 | Customer created as `ACTIVE` |
| 2 | Register duplicate email | Existing email | 409 | Duplicate email error |
| 3 | Register blank first name | `firstName = ""` | 400 | Validation error |
| 4 | Register blank last name | `lastName = ""` | 400 | Validation error |
| 5 | Register missing email | No `email` field | 400 | Validation error |
| 6 | Register malformed email | `email = "invalid"` | 400 | Validation error |
| 7 | Register invalid phone | Too short or invalid characters | 400 | Validation error |
| 8 | Update valid customer | Existing ID and valid body | 200 | Customer updated |
| 9 | Update unknown customer | Non-existing ID | 404 | Customer not found |
| 10 | Update with same email | Existing customer's own email | 200 | Update allowed |
| 11 | Update with another customer's email | Email owned by another ID | 409 | Duplicate email error |
| 12 | Update deleted customer | ID with `DELETED` status | 409 | Operation rejected |
| 13 | Update invalid body | Missing or malformed fields | 400 | Validation error |
| 14 | Delete active customer | Existing active ID | 204 | Status changed to `DELETED` |
| 15 | Delete unknown customer | Non-existing ID | 404 | Customer not found |
| 16 | Delete already deleted customer | Existing deleted ID | 409 | Invalid operation error |
| 17 | Delete zero ID | `/api/customers/0` | 4xx | Positive-ID validation failure |
| 18 | Delete non-numeric ID | `/api/customers/not-a-number` | 4xx | Path conversion failure |
| 19 | Unsupported GET | `GET /api/customers/1` | 405 | Read operations are outside this service |
| 20 | Health check | `GET /actuator/health` | 200 | Service health response |

## Automated tests

Run all tests:

```bash
mvn test
```

The test suite covers successful registration, duplicate email, successful update, missing customers, duplicate email during update, successful soft delete, already deleted customers, and controller responses for `201`, `200`, `204`, `400`, `404`, and `409`.

## Postman setup

Create a Postman environment with:

```text
baseUrl = http://localhost:8082
customerId = 1
```

Use these requests:

```text
POST   {{baseUrl}}/api/customers
PUT    {{baseUrl}}/api/customers/{{customerId}}
DELETE {{baseUrl}}/api/customers/{{customerId}}
GET    {{baseUrl}}/actuator/health
```

Set `Content-Type: application/json` on POST and PUT requests. Save the ID returned by registration into `customerId` before running update and delete scenarios.

## Design notes

- Customer email and customer number are unique at the application and database levels.
- Write operations use `@Transactional` in the service layer.
- Controllers contain no business logic.
- Authentication and JWT concerns belong to `auth-service`.
- `customer-query-service` owns `GET` operations; the API Gateway routes requests by HTTP method.
