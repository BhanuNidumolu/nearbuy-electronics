INSERT INTO app_users(email,password_hash,role) VALUES
('admin@nearbuy.local','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','ADMIN'),
('retailer@nearbuy.local','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','RETAILER'),
('customer@nearbuy.local','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','CUSTOMER');

INSERT INTO shops(name,address,latitude,longitude,owner_id,verified)
VALUES ('Guntur Mobile Hub','Arundelpet, Guntur, Andhra Pradesh',16.3067,80.4365,2,TRUE);

INSERT INTO phone_variants(brand,model,colour,ram_gb,storage_gb,condition_label) VALUES
('Samsung','Galaxy A55','Awesome Navy',8,128,'NEW'),
('Apple','iPhone 15','Black',6,128,'NEW'),
('OnePlus','Nord 4','Mercurial Silver',8,256,'NEW'),
('Xiaomi','Redmi Note 14 Pro','Titan Black',8,128,'NEW');

INSERT INTO inventory(shop_id,variant_id,price,physical_stock,reserved_stock,updated_at,version) VALUES
(1,1,36999.00,4,0,CURRENT_TIMESTAMP(6),0),
(1,2,54999.00,2,0,CURRENT_TIMESTAMP(6),0),
(1,3,29999.00,3,0,CURRENT_TIMESTAMP(6),0),
(1,4,24999.00,5,0,CURRENT_TIMESTAMP(6),0);
