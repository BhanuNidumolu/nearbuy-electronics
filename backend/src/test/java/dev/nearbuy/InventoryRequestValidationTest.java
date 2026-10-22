package dev.nearbuy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class InventoryRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void setUp() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void tearDown() { factory.close(); }
    private ApiController.InventoryRequest request(BigDecimal price, int stock) { return new ApiController.InventoryRequest(5L, price, stock); }
    @Test void rejectsZeroPrice() { var violations=validator.validate(request(new BigDecimal("0.00"),1)); assertTrue(violations.stream().anyMatch(v->v.getPropertyPath().toString().equals("price"))); }
    @Test void rejectsNegativeStock() { var violations=validator.validate(request(new BigDecimal("1000.00"),-1)); assertTrue(violations.stream().anyMatch(v->v.getPropertyPath().toString().equals("stock"))); }
    @Test void acceptsValidPriceAndZeroStock() { assertTrue(validator.validate(request(new BigDecimal("0.01"),0)).isEmpty()); }
}
