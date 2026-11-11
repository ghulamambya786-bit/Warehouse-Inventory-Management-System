-- Optional: the app creates all of this automatically on first run (DBConnection.initialize()).
-- Run this file manually only if you prefer to set the database up yourself.

CREATE DATABASE IF NOT EXISTS warehouse_db CHARACTER SET utf8mb4;
USE warehouse_db;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash CHAR(64) NOT NULL,          -- SHA-256 hex
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN'   -- ADMIN or STAFF
);

CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    price DECIMAL(10,2) NOT NULL DEFAULT 0,
    description VARCHAR(255) NULL,
    min_stock INT NOT NULL DEFAULT 30,        -- low-stock limit for this product
    image_path VARCHAR(500) NULL              -- picture chosen in Add Product
);

CREATE TABLE IF NOT EXISTS stock_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    event_time TIMESTAMP NULL,
    product_name VARCHAR(100),
    action VARCHAR(20),                       -- Added, Updated, Removed, Stock In, Stock Out, PUSH, POP
    quantity INT,
    performed_by VARCHAR(50),
    note VARCHAR(100),                        -- supplier/price (PUSH) or reason (POP)
    batch_id VARCHAR(20) NULL                 -- batch of a PUSH / POP
);

-- The warehouse stack: one row per batch. position 0 = BOTTOM, highest position = TOP.
CREATE TABLE IF NOT EXISTS batches (
    batch_id VARCHAR(20) PRIMARY KEY,
    product_id VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    batch_date DATE NOT NULL,
    supplier VARCHAR(100) NULL,
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    position INT NOT NULL,
    created_by VARCHAR(50) NULL
);

CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255) NULL
);

-- Default admin account: username admin / password admin123
INSERT IGNORE INTO users (username, password_hash, full_name, role) VALUES
 ('admin', SHA2('admin123', 256), 'System Administrator', 'ADMIN'),
 ('staff', SHA2('staff123', 256), 'Warehouse Staff', 'STAFF');
