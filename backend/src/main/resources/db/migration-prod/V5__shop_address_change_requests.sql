CREATE TABLE shop_address_change_requests (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 shop_id BIGINT NOT NULL,
 proposed_address VARCHAR(500) NOT NULL,
 proposed_latitude DOUBLE NOT NULL,
 proposed_longitude DOUBLE NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 requested_at TIMESTAMP(6) NOT NULL,
 reviewed_at TIMESTAMP(6) NULL,
 review_note VARCHAR(500) NULL,
 CONSTRAINT fk_shop_address_change_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
 INDEX idx_shop_address_change_status (status, requested_at),
 INDEX idx_shop_address_change_shop_status (shop_id, status)
);
