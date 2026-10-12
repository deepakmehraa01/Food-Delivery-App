-- Run this as a PostgreSQL administrator before starting the service.
-- Do not run CREATE DATABASE inside an existing transaction.
CREATE DATABASE customer_db;

-- The application creates/updates the customers table for the learning setup
-- using spring.jpa.hibernate.ddl-auto=update.
