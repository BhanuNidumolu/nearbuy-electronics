package dev.nearbuy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoPasswordDiagnosticTest {
    @Test
    void inspectDemoPasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String existingHash =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        System.out.println("Matches 'password': "
            + encoder.matches("password", existingHash));
        System.out.println("Matches 'ChangeMe123!': "
            + encoder.matches("ChangeMe123!", existingHash));

        String newHash = encoder.encode("ChangeMe123!");
        System.out.println("Verified new hash: "
            + encoder.matches("ChangeMe123!", newHash));
        System.out.println("Generated hash: " + newHash);
    }
}
