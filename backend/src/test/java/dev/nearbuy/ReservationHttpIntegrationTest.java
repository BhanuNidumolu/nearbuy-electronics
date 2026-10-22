
package dev.nearbuy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservationHttpIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("nearbuy_http_test")
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
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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

    @Test
    void customerCanReserveAndCancelThroughHttpApi() throws Exception {
        // Seed retailer, shop, variant, and inventory in real MySQL.
        AppUser retailer = users.saveAndFlush(
                new AppUser("http-retailer@example.test",
                        "test-hash", "RETAILER"));

        Shop shop = shops.saveAndFlush(
                new Shop("HTTP Test Shop", "Test Address",
                        16.3, 80.4, retailer));

        PhoneVariant variant = variants.saveAndFlush(
                new PhoneVariant("TestBrand", "HTTP Model",
                        "Black", 8, 128));

        Inventory item = inventory.saveAndFlush(
                new Inventory(shop, variant,
                        new BigDecimal("1000.00"), 5));

        // Register a customer through the real HTTP endpoint.
        MvcResult registerResult = mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "http-customer@example.test",
                                          "password": "TestPass123!",
                                          "role": "CUSTOMER"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(
                registerResult.getResponse().getContentAsString());

        String token = registerJson.get("token").asText();
        assertFalse(token.isBlank());

        // Reserve two units using the JWT from registration.
        MvcResult reserveResult = mockMvc.perform(
                        post("/api/reservations")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "inventoryId": %d,
                                          "quantity": 2
                                        }
                                        """.formatted(item.id)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode reservationJson = objectMapper.readTree(
                reserveResult.getResponse().getContentAsString());

        long reservationId = reservationJson.get("id").asLong();

        Inventory afterReserve =
                inventory.findById(item.id).orElseThrow();

        assertEquals(5, afterReserve.physicalStock);
        assertEquals(2, afterReserve.reservedStock);
        assertEquals(3, afterReserve.availableStock());

        // Cancel the reservation through the real HTTP endpoint.
        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservationId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Inventory afterCancel =
                inventory.findById(item.id).orElseThrow();

        Reservation savedReservation =
                reservations.findById(reservationId).orElseThrow();

        assertEquals(5, afterCancel.physicalStock);
        assertEquals(0, afterCancel.reservedStock);
        assertEquals(5, afterCancel.availableStock());
        assertEquals("CANCELLED", savedReservation.status);
    }
    @Test
void publicRegistrationRejectsAdminRole() throws Exception {
    mockMvc.perform(
            post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "email": "attempted-admin@example.test",
                              "password": "TestPass123!",
                              "role": "ADMIN"
                            }
                            """))
            .andExpect(status().isBadRequest());

    assertTrue(
            users.findByEmail("attempted-admin@example.test").isEmpty()
    );
}

@Test
void concurrentReservationsCannotOversellInventory() throws Exception {
    // Create a shop and inventory with five physical units.
    AppUser retailer = users.saveAndFlush(
            new AppUser("concurrency-retailer@example.test",
                    "test-hash", "RETAILER"));

    Shop shop = shops.saveAndFlush(
            new Shop("Concurrency Test Shop", "Test Address",
                    16.3, 80.4, retailer));

    PhoneVariant variant = variants.saveAndFlush(
            new PhoneVariant("ConcurrencyBrand", "ConcurrencyModel",
                    "Black", 8, 128));

    Inventory item = inventory.saveAndFlush(
            new Inventory(shop, variant,
                    new BigDecimal("1000.00"), 5));

    // Register two separate customers and obtain their JWTs.
    MvcResult registerA = mockMvc.perform(
                    post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "concurrency-a@example.test",
                                      "password": "TestPass123!",
                                      "role": "CUSTOMER"
                                    }
                                    """))
            .andExpect(status().isCreated())
            .andReturn();

    MvcResult registerB = mockMvc.perform(
                    post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "concurrency-b@example.test",
                                      "password": "TestPass123!",
                                      "role": "CUSTOMER"
                                    }
                                    """))
            .andExpect(status().isCreated())
            .andReturn();

    String tokenA = objectMapper.readTree(
            registerA.getResponse().getContentAsString())
            .get("token").asText();

    String tokenB = objectMapper.readTree(
            registerB.getResponse().getContentAsString())
            .get("token").asText();

    // Coordinate the two requests so they start together.
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
        Future<MvcResult> requestA = executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start signal timed out");
            }

            return mockMvc.perform(
                    post("/api/reservations")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "inventoryId": %d,
                                      "quantity": 3
                                    }
                                    """.formatted(item.id)))
                    .andReturn();
        });

        Future<MvcResult> requestB = executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start signal timed out");
            }

            return mockMvc.perform(
                    post("/api/reservations")
                            .header("Authorization", "Bearer " + tokenB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "inventoryId": %d,
                                      "quantity": 3
                                    }
                                    """.formatted(item.id)))
                    .andReturn();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS),
                "Both requests should be ready");

        start.countDown();

        int statusA = requestA.get(30, TimeUnit.SECONDS)
                .getResponse().getStatus();

        int statusB = requestB.get(30, TimeUnit.SECONDS)
                .getResponse().getStatus();

        // Exactly one reservation should succeed.
        assertTrue(
                (statusA == 201 && statusB == 409)
                        || (statusA == 409 && statusB == 201),
                "Expected one 201 and one 409, got "
                        + statusA + " and " + statusB
        );

        Inventory savedItem =
                inventory.findById(item.id).orElseThrow();

        assertEquals(5, savedItem.physicalStock);
        assertEquals(3, savedItem.reservedStock);
        assertEquals(2, savedItem.availableStock());

        long activeReservations = reservations.findAll().stream()
                .filter(r -> r.inventory.id.equals(item.id))
                .filter(r -> "ACTIVE".equals(r.status))
                .count();

        assertEquals(1, activeReservations);

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}

}
