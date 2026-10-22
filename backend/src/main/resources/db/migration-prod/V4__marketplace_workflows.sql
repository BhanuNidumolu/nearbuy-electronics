ALTER TABLE app_users ADD COLUMN profile_address VARCHAR(500) NULL;
ALTER TABLE shops ADD COLUMN verification_note VARCHAR(500) NULL;
ALTER TABLE inventory ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE notifications (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 recipient_id BIGINT NOT NULL,
 event_type VARCHAR(40) NOT NULL,
 title VARCHAR(180) NOT NULL,
 message VARCHAR(1000) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_users(id),
 INDEX idx_notification_recipient_read_created (recipient_id,is_read,created_at)
);
