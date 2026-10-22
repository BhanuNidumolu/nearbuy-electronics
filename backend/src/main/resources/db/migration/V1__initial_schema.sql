CREATE TABLE app_users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL,
 CONSTRAINT chk_user_role CHECK (role IN ('CUSTOMER','RETAILER','ADMIN'))
);
CREATE TABLE shops (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(190) NOT NULL,
 address VARCHAR(500) NOT NULL,
 latitude DOUBLE NOT NULL,
 longitude DOUBLE NOT NULL,
 owner_id BIGINT NOT NULL,
 verified BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT fk_shop_owner FOREIGN KEY (owner_id) REFERENCES app_users(id)
);
CREATE TABLE phone_variants (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 brand VARCHAR(100) NOT NULL,
 model VARCHAR(190) NOT NULL,
 colour VARCHAR(100) NOT NULL,
 ram_gb INT NOT NULL,
 storage_gb INT NOT NULL,
 condition_label VARCHAR(30) NOT NULL DEFAULT 'NEW'
);
CREATE TABLE inventory (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 shop_id BIGINT NOT NULL,
 variant_id BIGINT NOT NULL,
 price DECIMAL(12,2) NOT NULL,
 physical_stock INT NOT NULL,
 reserved_stock INT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_inventory_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
 CONSTRAINT fk_inventory_variant FOREIGN KEY (variant_id) REFERENCES phone_variants(id),
 CONSTRAINT chk_inventory_stock CHECK (physical_stock >= 0 AND reserved_stock >= 0 AND reserved_stock <= physical_stock),
 INDEX idx_inventory_variant_shop (variant_id,shop_id)
);
CREATE TABLE reservations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 reservation_code VARCHAR(20) NOT NULL UNIQUE,
 customer_id BIGINT NOT NULL,
 inventory_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 completed_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_res_customer FOREIGN KEY (customer_id) REFERENCES app_users(id),
 CONSTRAINT fk_res_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id),
 INDEX idx_reservation_status_expiry (status,expires_at)
);
