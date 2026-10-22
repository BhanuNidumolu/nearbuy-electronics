package dev.nearbuy;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="app_users")
class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false, unique=true) String email;
 @Column(nullable=false) String passwordHash;
 @Column(nullable=false) String role;
 @Column(name="profile_address", length=500) String profileAddress;
 protected AppUser() {}
 AppUser(String email, String passwordHash, String role) { this.email=email; this.passwordHash=passwordHash; this.role=role; }
}

@Entity
@Table(name="shops")
class Shop {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) String name;
 @Column(nullable=false) String address;
 @Column(nullable=false) double latitude;
 @Column(nullable=false) double longitude;
 @ManyToOne(optional=false) AppUser owner;
 @Column(nullable=false) boolean verified=true;
 @Column(name="verification_note", length=500) String verificationNote;
 protected Shop() {}
 Shop(String name, String address, double latitude, double longitude, AppUser owner) { this.name=name; this.address=address; this.latitude=latitude; this.longitude=longitude; this.owner=owner; }
}

@Entity
@Table(name="phone_variants")
class PhoneVariant {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) String brand;
 @Column(nullable=false) String model;
 @Column(nullable=false) String colour;
 @Column(nullable=false) Integer ramGb;
 @Column(nullable=false) Integer storageGb;
 @Column(nullable=false) String conditionLabel;
 protected PhoneVariant() {}
 PhoneVariant(String brand,String model,String colour,Integer ramGb,Integer storageGb) { this.brand=brand; this.model=model; this.colour=colour; this.ramGb=ramGb; this.storageGb=storageGb; this.conditionLabel="NEW"; }
}

@Entity
@Table(name="inventory", indexes=@Index(name="idx_inventory_variant_shop", columnList="variant_id,shop_id"))
class Inventory {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @ManyToOne(optional=false) Shop shop;
 @ManyToOne(optional=false) PhoneVariant variant;
 @Column(nullable=false, precision=12, scale=2) BigDecimal price;
 @Column(nullable=false) int physicalStock;
 @Column(nullable=false) int reservedStock=0;
 @Column(nullable=false) Instant updatedAt=Instant.now();
 @Column(nullable=false) boolean active=true;
 @Version Long version;
 protected Inventory() {}
 Inventory(Shop shop,PhoneVariant variant,BigDecimal price,int stock) { this.shop=shop;this.variant=variant;this.price=price;this.physicalStock=stock;this.updatedAt=Instant.now(); }
 int availableStock() { return physicalStock-reservedStock; }
}

@Entity
@Table(name="reservations", indexes=@Index(name="idx_reservation_status_expiry", columnList="status,expires_at"))
class Reservation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false, unique=true) String reservationCode;
 @ManyToOne(optional=false) AppUser customer;
 @ManyToOne(optional=false) Inventory inventory;
 @Column(nullable=false) int quantity;
 @Column(nullable=false) String status;
 @Column(nullable=false) Instant createdAt;
 @Column(nullable=false) Instant expiresAt;
 Instant completedAt;
 protected Reservation() {}
 Reservation(String code,AppUser customer,Inventory inventory,int quantity,Instant createdAt,Instant expiresAt) {
  this.reservationCode=code;this.customer=customer;this.inventory=inventory;this.quantity=quantity;this.status="ACTIVE";this.createdAt=createdAt;this.expiresAt=expiresAt;
 }
}


@Entity
@Table(name="notifications", indexes=@Index(name="idx_notification_recipient_read_created", columnList="recipient_id,is_read,created_at"))
class Notification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @ManyToOne(optional=false) @JoinColumn(name="recipient_id", nullable=false) AppUser recipient;
 @Column(name="event_type", nullable=false, length=40) String eventType;
 @Column(nullable=false, length=180) String title;
 @Column(nullable=false, length=1000) String message;
 @Column(name="is_read", nullable=false) boolean read=false;
 @Column(name="created_at", nullable=false) Instant createdAt=Instant.now();
 protected Notification() {}
 Notification(AppUser recipient, String eventType, String title, String message) {
  this.recipient=recipient; this.eventType=eventType; this.title=title; this.message=message; this.read=false; this.createdAt=Instant.now();
 }
}


@Entity
@Table(name="shop_address_change_requests", indexes=@Index(name="idx_shop_address_change_status", columnList="status,requested_at"))
class ShopAddressChangeRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @ManyToOne(optional=false) @JoinColumn(name="shop_id", nullable=false) Shop shop;
 @Column(name="proposed_address", nullable=false, length=500) String proposedAddress;
 @Column(name="proposed_latitude", nullable=false) double proposedLatitude;
 @Column(name="proposed_longitude", nullable=false) double proposedLongitude;
 @Column(nullable=false, length=20) String status="PENDING";
 @Column(name="requested_at", nullable=false) Instant requestedAt=Instant.now();
 @Column(name="reviewed_at") Instant reviewedAt;
 @Column(name="review_note", length=500) String reviewNote;
 protected ShopAddressChangeRequest() {}
 ShopAddressChangeRequest(Shop shop, String address, double latitude, double longitude) { this.shop=shop; this.proposedAddress=address; this.proposedLatitude=latitude; this.proposedLongitude=longitude; this.status="PENDING"; this.requestedAt=Instant.now(); }
}
