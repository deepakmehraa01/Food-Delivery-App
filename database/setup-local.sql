-- Run with psql as a PostgreSQL administrator while connected to the postgres database.
-- Example: psql -U postgres -d postgres -f database/setup-local.sql
-- Existing databases are left untouched.
SELECT format('CREATE DATABASE %I', database_name)
FROM (VALUES ('customer_db'), ('product_db'), ('order_db'), ('inventorydb'), ('product_test')) AS databases(database_name)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = database_name
)
\gexec
