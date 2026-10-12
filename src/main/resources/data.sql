-- Spring runs this after Hibernate creates the table; inserts are safe to repeat.
INSERT INTO inventory (product_code, product_name, quantity)
VALUES ('P1001', 'Laptop', 10)
ON CONFLICT (product_code) DO NOTHING;

INSERT INTO inventory (product_code, product_name, quantity)
VALUES ('P1004', 'Shoes', 10)
ON CONFLICT (product_code) DO NOTHING;

INSERT INTO inventory (product_code, product_name, quantity)
VALUES ('P1005', 'Tshirts', 20)
ON CONFLICT (product_code) DO NOTHING;

INSERT INTO inventory (product_code, product_name, quantity)
VALUES ('P1006', 'Electronics', 5)
ON CONFLICT (product_code) DO NOTHING;
