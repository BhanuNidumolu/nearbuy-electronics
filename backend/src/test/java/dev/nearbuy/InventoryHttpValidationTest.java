
package dev.nearbuy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class InventoryHttpValidationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean UserRepository users;
    @MockBean ShopRepository shops;
    @MockBean VariantRepository variants;
    @MockBean InventoryRepository inventory;
    @MockBean ReservationRepository reservations;
    @MockBean NotificationRepository notifications;
    @MockBean ShopAddressChangeRepository addressChanges;

    @MockBean
    org.springframework.security.crypto.password.PasswordEncoder encoder;

    @Test
    void invalidPriceReturnsBadRequestAndDoesNotSaveInventory()
            throws Exception {

        String request = """
                {
                  "variantId": 5,
                  "shopName": "Test Shop",
                  "address": "Test Address",
                  "price": 0,
                  "stock": 1
                }
                """;

        mockMvc.perform(post("/api/retailer/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.price")
                        .value("must be greater than or equal to 0.01"));

        verifyNoInteractions(inventory);
        verifyNoInteractions(variants);
        verifyNoInteractions(shops);
    }
}
