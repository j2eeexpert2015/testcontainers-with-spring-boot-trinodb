DROP TABLE IF EXISTS products CASCADE;
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    price DOUBLE PRECISION,
    stock_quantity INT
);