package dev.nearbuy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    AdminBootstrap(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String adminEmail,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            return;
        }

        if (adminEmail.isBlank()
                || adminPassword.isBlank()
                || adminPassword.length() < 16
                || adminPassword.length() > 72
                || !adminEmail.matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException(
                    "Admin bootstrap configuration is invalid. Set a valid email and a 16-72 character password.");
        }

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);

        if (users.findByEmail(normalizedEmail).isPresent()) {
            return;
        }

        AppUser admin = new AppUser(
                normalizedEmail,
                passwordEncoder.encode(adminPassword),
                "ADMIN");

        users.save(admin);
    }
}
