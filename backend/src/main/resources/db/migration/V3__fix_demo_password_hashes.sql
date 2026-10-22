UPDATE app_users
SET password_hash = '$2a$10$RH3jxb2dTWm4ZflfckQmKe/0xKuwcXc5AXvFBNpKceLWAHy8ub9zi'
WHERE email IN (
    'admin@nearbuy.local',
    'retailer@nearbuy.local',
    'customer@nearbuy.local'
);
