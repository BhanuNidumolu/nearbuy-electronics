
package dev.nearbuy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class ReservationDatabaseIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("nearbuy_test")
                    .withUsername("nearbuy")
                    .withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.jwt-secret",
                () -> "0123456789abcdef0123456789abcdef");
        registry.add("app.reservation-hold-duration", () -> "PT15M");
    }

    @Autowired
    UserRepository users;

    @Autowired
    ShopRepository shops;

    @Autowired
    VariantRepository variants;

    @Autowired
    InventoryRepository inventory;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    NotificationRepository notifications;

    @Test
    void persistsInventoryAndReservationWithCorrectStock() {
        AppUser customer = users.saveAndFlush(
                new AppUser("integration-customer@example.test",
                        "test-hash", "CUSTOMER"));

        AppUser retailer = users.saveAndFlush(
                new AppUser("integration-retailer@example.test",
                        "test-hash", "RETAILER"));

        Shop shop = shops.saveAndFlush(
                new Shop("Integration Test Shop",
                        "Test Address", 16.3, 80.4, retailer));

        PhoneVariant variant = variants.saveAndFlush(
                new PhoneVariant("TestBrand", "TestModel",
                        "Black", 8, 128));

        Inventory item = inventory.saveAndFlush(
                new Inventory(shop, variant,
                        new BigDecimal("1000.00"), 5));

        assertEquals(5, item.availableStock());

        item.reservedStock += 2;
        inventory.saveAndFlush(item);

        Reservation reservation = reservations.saveAndFlush(
                new Reservation("INTEGRATION01", customer, item, 2,
                        java.time.Instant.now(),
                        java.time.Instant.now()
                                .plus(java.time.Duration.ofMinutes(15))));

        Inventory reloadedItem =
                inventory.findById(item.id).orElseThrow();

        Reservation reloadedReservation =
                reservations.findById(reservation.id).orElseThrow();

        assertEquals(5, reloadedItem.physicalStock);
        assertEquals(2, reloadedItem.reservedStock);
        assertEquals(3, reloadedItem.availableStock());
        assertEquals("ACTIVE", reloadedReservation.status);
        assertEquals(2, reloadedReservation.quantity);
        assertEquals(customer.id,
                reloadedReservation.customer.id);
    }
    
@Test
void cancellationReleasesReservedStockAndPersistsStatus() {
    AppUser customer = users.saveAndFlush(
            new AppUser("cancel-customer@example.test",
                    "test-hash", "CUSTOMER"));

    AppUser retailer = users.saveAndFlush(
            new AppUser("cancel-retailer@example.test",
                    "test-hash", "RETAILER"));

    Shop shop = shops.saveAndFlush(
            new Shop("Cancellation Test Shop",
                    "Test Address", 16.3, 80.4, retailer));

    PhoneVariant variant = variants.saveAndFlush(
            new PhoneVariant("TestBrand", "CancelModel",
                    "Black", 8, 128));

    Inventory item = inventory.saveAndFlush(
            new Inventory(shop, variant,
                    new BigDecimal("1000.00"), 5));

    item.reservedStock = 2;
    item = inventory.saveAndFlush(item);

    Instant now = Instant.now();
    Reservation reservation = reservations.saveAndFlush(
            new Reservation("CANCELTEST01", customer, item, 2,
                    now, now.plus(Duration.ofMinutes(15))));

    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(customer.email);

    ApiController controller = new ApiController(
            users, shops, variants, inventory, reservations,
            notifications, mock(ShopAddressChangeRepository.class),
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
            "0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(15));

    var response = controller.cancel(reservation.id, authentication);

    assertEquals(200, response.getStatusCode().value());

    Inventory reloadedItem =
            inventory.findById(item.id).orElseThrow();
    Reservation reloadedReservation =
            reservations.findById(reservation.id).orElseThrow();

    assertEquals(5, reloadedItem.physicalStock);
    assertEquals(0, reloadedItem.reservedStock);
    assertEquals(5, reloadedItem.availableStock());
    assertEquals("CANCELLED", reloadedReservation.status);
}

}
