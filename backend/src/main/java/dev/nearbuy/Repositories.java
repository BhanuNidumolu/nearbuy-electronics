package dev.nearbuy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

interface UserRepository extends JpaRepository<AppUser,Long> { Optional<AppUser> findByEmail(String email); }
interface ShopRepository extends JpaRepository<Shop,Long> { List<Shop> findByVerifiedTrue(); List<Shop> findByVerifiedFalse(); Optional<Shop> findByOwnerId(Long ownerId); }
interface VariantRepository extends JpaRepository<PhoneVariant,Long> {}
interface InventoryRepository extends JpaRepository<Inventory,Long> {
 @EntityGraph(attributePaths={"shop","variant"})
 List<Inventory> findAll();
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Inventory i where i.id=:id")
 Optional<Inventory> lockById(@Param("id") Long id);
 @Query("select i from Inventory i where i.shop.owner.id=:ownerId")
 List<Inventory> findByOwner(@Param("ownerId") Long ownerId);
}

interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id=:id")
    Optional<Reservation> lockById(@Param("id") Long id);

    @Query("select r.id from Reservation r where r.status='ACTIVE' and r.expiresAt <= :now")
    List<Long> findExpiredIds(@Param("now") Instant now);
}


interface ShopAddressChangeRepository extends JpaRepository<ShopAddressChangeRequest,Long> { List<ShopAddressChangeRequest> findByStatusOrderByRequestedAtAsc(String status); Optional<ShopAddressChangeRequest> findFirstByShopIdAndStatusOrderByRequestedAtDesc(Long shopId, String status); }
interface NotificationRepository extends JpaRepository<Notification,Long> { List<Notification> findTop100ByRecipientIdOrderByCreatedAtDesc(Long recipientId); long countByRecipientIdAndReadFalse(Long recipientId); Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId); }
