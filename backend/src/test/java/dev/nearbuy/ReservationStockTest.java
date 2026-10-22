
package dev.nearbuy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationStockTest {

    private UserRepository users;
    private ShopRepository shops;
    private VariantRepository variants;
    private InventoryRepository inventory;
    private ReservationRepository reservations;
    private ApiController controller;

    private AppUser customer;
    private Inventory item;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        shops = mock(ShopRepository.class);
        variants = mock(VariantRepository.class);
        inventory = mock(InventoryRepository.class);
        reservations = mock(ReservationRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        ShopAddressChangeRepository addressChanges = mock(ShopAddressChangeRepository.class);

        controller = new ApiController(
                users,
                shops,
                variants,
                inventory,
                reservations,
                notifications,
                addressChanges,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                "0123456789abcdef0123456789abcdef",
                Duration.ofMinutes(15)
        );

        customer = mock(AppUser.class);
        customer.id = 10L;
        customer.role = "CUSTOMER";

        PhoneVariant variant = new PhoneVariant(
                "Samsung", "Galaxy A55", "Black", 8, 128
        );

        AppUser owner = mock(AppUser.class);
        owner.id = 20L;

        Shop shop = new Shop(
                "Test Shop", "Test Address",
                16.3, 80.4, owner
        );

        item = new Inventory(
                shop, variant, new BigDecimal("1000.00"), 2
        );
        item.id = 5L;
        item.reservedStock = 1;

        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("customer@test.com");
when(users.findByEmail("customer@test.com"))
        .thenReturn(Optional.of(customer));

        when(inventory.lockById(5L)).thenReturn(Optional.of(item));
    }

    @Test
    void insufficientStockReturnsConflictWithoutSavingReservation() {
        ApiController.ReserveRequest request =
                new ApiController.ReserveRequest(5L, 2);

        ResponseEntity<?> response =
                controller.reserve(request, authentication);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(1, item.reservedStock);

        verify(reservations, never()).save(any(Reservation.class));
        verify(inventory).lockById(5L);
    }
    
@Test
void sufficientStockCreatesReservationAndIncreasesReservedStock() {
    // Arrange: two items are available.
    item.reservedStock = 0;

    ApiController.ReserveRequest request =
            new ApiController.ReserveRequest(5L, 2);

    when(reservations.save(any(Reservation.class)))
            .thenAnswer(invocation -> {
                Reservation saved = invocation.getArgument(0);
                saved.id = 100L;
                return saved;
            });

    // Act
    ResponseEntity<?> response =
            controller.reserve(request, authentication);

    // Assert
    assertEquals(201, response.getStatusCode().value());
    assertEquals(2, item.reservedStock);

    verify(reservations, times(1)).save(any(Reservation.class));

    // Check the saved reservation's important fields.
    var captor = org.mockito.ArgumentCaptor.forClass(Reservation.class);
    verify(reservations).save(captor.capture());

    Reservation saved = captor.getValue();
    assertEquals(100L, saved.id);
    assertEquals(2, saved.quantity);
    assertEquals("ACTIVE", saved.status);
    assertSame(customer, saved.customer);
    assertSame(item, saved.inventory);
    assertNotNull(saved.reservationCode);
    assertNotNull(saved.createdAt);
    assertNotNull(saved.expiresAt);
}

@Test
void cancellingActiveReservationReleasesStock() {
    // Arrange: one item is currently reserved.
    item.reservedStock = 1;

    Instant now = Instant.now();

    Reservation reservation = new Reservation(
            "CANCEL01",
            customer,
            item,
            1,
            now,
            now.plus(Duration.ofMinutes(15))
    );
    reservation.id = 200L;

    when(reservations.lockById(200L))
            .thenReturn(Optional.of(reservation));

    when(inventory.lockById(5L))
            .thenReturn(Optional.of(item));

    // Act
    ResponseEntity<?> response =
            controller.cancel(200L, authentication);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertEquals("CANCELLED", reservation.status);
    assertEquals(0, item.reservedStock);

    verify(reservations).lockById(200L);
    verify(inventory).lockById(5L);
}

@Test
void cancellingAlreadyCancelledReservationReturnsConflict() {
    item.reservedStock = 0;

    Instant now = Instant.now();

    Reservation reservation = new Reservation(
            "CANCEL02",
            customer,
            item,
            1,
            now,
            now.plus(Duration.ofMinutes(15))
    );
    reservation.id = 201L;
    reservation.status = "CANCELLED";

    when(reservations.lockById(201L))
            .thenReturn(Optional.of(reservation));

    ResponseEntity<?> response =
            controller.cancel(201L, authentication);

    assertEquals(409, response.getStatusCode().value());
    assertEquals("CANCELLED", reservation.status);
    assertEquals(0, item.reservedStock);

    verify(reservations).lockById(201L);
    verify(inventory, never()).lockById(5L);
}

@Test
void expiryReleasesStockAndMarksReservationExpired() {
    // Arrange
    item.reservedStock = 1;

    Instant now = Instant.now();

    Reservation reservation = new Reservation(
            "EXPIRED01",
            customer,
            item,
            1,
            now.minus(Duration.ofMinutes(20)),
            now.minus(Duration.ofMinutes(5))
    );
    reservation.id = 300L;

    when(reservations.findExpiredIds(any(Instant.class)))
            .thenReturn(java.util.List.of(300L));

    when(reservations.lockById(300L))
            .thenReturn(Optional.of(reservation));

    when(inventory.lockById(5L))
            .thenReturn(Optional.of(item));

    // Act
    controller.expireReservations();

    // Assert
    assertEquals("EXPIRED", reservation.status);
    assertEquals(0, item.reservedStock);

    verify(reservations).findExpiredIds(any(Instant.class));
    verify(reservations).lockById(300L);
    verify(inventory).lockById(5L);
}

@Test
void successfulPickupCompletesReservationAndReducesStock() {
    // Arrange
    AppUser retailer = mock(AppUser.class);
    retailer.id = 20L;
    retailer.role = "RETAILER";
    retailer.email = "retailer@test.com";

    Authentication retailerAuth = mock(Authentication.class);
    when(retailerAuth.getName()).thenReturn("retailer@test.com");
    when(users.findByEmail("retailer@test.com"))
            .thenReturn(Optional.of(retailer));

    item.physicalStock = 2;
    item.reservedStock = 1;

    Instant now = Instant.now();

    Reservation reservation = new Reservation(
            "PICKUP01",
            customer,
            item,
            1,
            now,
            now.plus(Duration.ofMinutes(15))
    );
    reservation.id = 400L;

    when(reservations.lockById(400L))
            .thenReturn(Optional.of(reservation));
    when(inventory.lockById(5L))
            .thenReturn(Optional.of(item));

    // Act
    ResponseEntity<?> response =
            controller.pickup(400L, retailerAuth);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertEquals("COMPLETED", reservation.status);
    assertNotNull(reservation.completedAt);
    assertEquals(1, item.physicalStock);
    assertEquals(0, item.reservedStock);
    assertEquals(1, item.availableStock());
}

@Test
void pickupAfterExpiryReturnsConflictAndReleasesReservedStock() {
    // Arrange
    AppUser retailer = mock(AppUser.class);
    retailer.id = 20L;
    retailer.role = "RETAILER";
    retailer.email = "retailer@test.com";

    Authentication retailerAuth = mock(Authentication.class);
    when(retailerAuth.getName()).thenReturn("retailer@test.com");
    when(users.findByEmail("retailer@test.com"))
            .thenReturn(Optional.of(retailer));

    item.physicalStock = 2;
    item.reservedStock = 1;

    Instant now = Instant.now();

    Reservation reservation = new Reservation(
            "PICKUP02",
            customer,
            item,
            1,
            now.minus(Duration.ofMinutes(20)),
            now.minus(Duration.ofMinutes(5))
    );
    reservation.id = 401L;

    when(reservations.lockById(401L))
            .thenReturn(Optional.of(reservation));
    when(inventory.lockById(5L))
            .thenReturn(Optional.of(item));

    // Act
    ResponseEntity<?> response =
            controller.pickup(401L, retailerAuth);

    // Assert
    assertEquals(409, response.getStatusCode().value());
    assertEquals("EXPIRED", reservation.status);
    assertEquals(2, item.physicalStock);
    assertEquals(0, item.reservedStock);
}

@Test
void retailerCannotPickUpAnotherShopsReservation() {
    // Arrange: another retailer owns the shop.
    AppUser otherRetailer = mock(AppUser.class);
    otherRetailer.id = 99L;
    otherRetailer.role = "RETAILER";
    otherRetailer.email = "other-retailer@test.com";

    Authentication otherRetailerAuth = mock(Authentication.class);
    when(otherRetailerAuth.getName())
            .thenReturn("other-retailer@test.com");
    when(users.findByEmail("other-retailer@test.com"))
            .thenReturn(Optional.of(otherRetailer));

    item.physicalStock = 2;
    item.reservedStock = 1;

    Instant now = Instant.now();
    Reservation reservation = new Reservation(
            "PICKUP03",
            customer,
            item,
            1,
            now,
            now.plus(Duration.ofMinutes(15))
    );
    reservation.id = 402L;

    when(reservations.lockById(402L))
            .thenReturn(Optional.of(reservation));
    when(inventory.lockById(5L))
            .thenReturn(Optional.of(item));

    // Act and assert
    org.springframework.web.server.ResponseStatusException exception =
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> controller.pickup(402L, otherRetailerAuth)
            );

    assertEquals(403, exception.getStatusCode().value());
    assertEquals("ACTIVE", reservation.status);
    assertEquals(2, item.physicalStock);
    assertEquals(1, item.reservedStock);
}

@Test
void customerCannotCancelAnotherCustomersReservation() {
    // Arrange: authenticate as a different customer.
    AppUser otherCustomer = mock(AppUser.class);
    otherCustomer.id = 11L;
    otherCustomer.role = "CUSTOMER";
    otherCustomer.email = "other-customer@test.com";

    Authentication otherCustomerAuth = mock(Authentication.class);
    when(otherCustomerAuth.getName())
            .thenReturn("other-customer@test.com");
    when(users.findByEmail("other-customer@test.com"))
            .thenReturn(Optional.of(otherCustomer));

    item.reservedStock = 1;

    Instant now = Instant.now();
    Reservation reservation = new Reservation(
            "OWNER01",
            customer,
            item,
            1,
            now,
            now.plus(Duration.ofMinutes(15))
    );
    reservation.id = 500L;

    when(reservations.lockById(500L))
            .thenReturn(Optional.of(reservation));

    // Act and assert
    org.springframework.web.server.ResponseStatusException exception =
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> controller.cancel(500L, otherCustomerAuth)
            );

    assertEquals(403, exception.getStatusCode().value());
    assertEquals("ACTIVE", reservation.status);
    assertEquals(1, item.reservedStock);

    verify(inventory, never()).lockById(5L);
}


}
