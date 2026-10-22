
package dev.nearbuy;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.validation.constraints.DecimalMax;
import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
class ApiController {

    private final UserRepository users;
    private final ShopRepository shops;
    private final VariantRepository variants;
    private final InventoryRepository inventory;
    private final ReservationRepository reservations;
    private final NotificationRepository notifications;
    private final ShopAddressChangeRepository addressChanges;
    private final PasswordEncoder encoder;
    private final SecretKey key;
    private final Duration hold;

    ApiController(
            UserRepository users,
            ShopRepository shops,
            VariantRepository variants,
            InventoryRepository inventory,
            ReservationRepository reservations,
            NotificationRepository notifications,
            ShopAddressChangeRepository addressChanges,
            PasswordEncoder encoder,
            @Value("${app.jwt-secret}") String secret,
            @Value("${app.reservation-hold-duration:PT15M}") Duration hold
    ) {
        this.users = users;
        this.shops = shops;
        this.variants = variants;
        this.inventory = inventory;
        this.reservations = reservations;
        this.notifications = notifications;
        this.addressChanges = addressChanges;
        this.encoder = encoder;
        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.hold = hold;
    }

    record AuthRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {}

    record RetailerRegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 190) String shopName,
            @NotBlank @Size(max = 500) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude
    ) {}

    record InventoryUpdateRequest(@DecimalMin("0.01") BigDecimal price, @Min(0) int stock) {}

    record InventoryRequest(
            @NotNull Long variantId,
            @DecimalMin("0.01") BigDecimal price,
            @Min(0) int stock
    ) {}

    record VariantRequest(
            @NotBlank String brand,
            @NotBlank String model,
            @NotBlank String colour,
            @Min(1) int ramGb,
            @Min(1) int storageGb
    ) {}

    record ReserveRequest(
            @NotNull Long inventoryId,
            @Min(1) @Max(3) int quantity
    ) {}

    record Message(String message) {}

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }

    // -------------------- Authentication --------------------


@PostMapping("/auth/register")
ResponseEntity<?> register(@RequestBody JsonNode body) {
    if (body == null || !body.isObject()) {
        return ResponseEntity.badRequest()
                .body(new Message("Invalid request body"));
    }

    if (body.has("role")) {
    String requestedRole = body.path("role").asText("");

    if (!"CUSTOMER".equals(requestedRole)) {
        return ResponseEntity.badRequest()
                .body(new Message("Only customer registration is allowed"));
    }
}

    String email = body.path("email").asText("").trim()
            .toLowerCase(Locale.ROOT);
    String password = body.path("password").asText("");

    if (email.isBlank()
            || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            || password.length() < 8
            || password.length() > 100) {
        return ResponseEntity.badRequest()
                .body(new Message("Invalid email or password"));
    }

    if (users.findByEmail(email).isPresent()) {
        return ResponseEntity.status(409)
                .body(new Message("Email already registered"));
    }

    AppUser user = users.save(
            new AppUser(email, encoder.encode(password), "CUSTOMER")
    );

    return ResponseEntity.status(201).body(
            Map.of(
                    "token", token(user),
                    "email", user.email,
                    "role", user.role
            )
    );
}


    @PostMapping("/auth/register-retailer")
    @Transactional
    ResponseEntity<?> registerRetailer(@Valid @RequestBody RetailerRegisterRequest req) {
        String email = req.email().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) return ResponseEntity.status(409).body(new Message("Email already registered"));
        AppUser user = users.save(new AppUser(email, encoder.encode(req.password()), "RETAILER"));
        Shop shop = new Shop(req.shopName().trim(), req.address().trim(), req.latitude(), req.longitude(), user);
        shop.verified = false;
        shop.verificationNote = "Awaiting admin review";
        shops.save(shop);
        users.findAll().stream().filter(u -> "ADMIN".equals(u.role)).forEach(admin -> notify(admin, "RETAILER_APPLICATION", "Retailer application received", email + " applied to list products from " + shop.name + "."));
        return ResponseEntity.status(201).body(Map.of("message", "Application submitted. Your shop address must be verified before listings become public.", "token", token(user), "email", user.email, "role", user.role, "shopVerified", false));
    }

    @PostMapping("/auth/login")
    ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        AppUser user = users.findByEmail(
                req.email().toLowerCase(Locale.ROOT)
        ).orElse(null);

        if (user == null ||
                !encoder.matches(req.password(), user.passwordHash)) {
            return ResponseEntity.status(401)
                    .body(new Message("Invalid email or password"));
        }

        return ResponseEntity.ok(
                Map.of(
                        "token", token(user),
                        "email", user.email,
                        "role", user.role
                )
        );
    }

    private String token(AppUser user) {
        return Jwts.builder()
                .subject(user.email)
                .claim("uid", user.id.toString())
                .claim("role", user.role)
                .issuedAt(new Date())
                .expiration(
                        Date.from(Instant.now().plus(Duration.ofHours(12)))
                )
                .signWith(key)
                .compact();
    }

    private AppUser current(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow();
    }

    private void requireRole(AppUser user, String role) {
        if (!user.role.equals(role) && !user.role.equals("ADMIN")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Insufficient role"
            );
        }
    }

    // -------------------- Customer Offers --------------------

    @GetMapping("/offers")
    List<Map<String, Object>> offers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "16.3067") double latitude,
            @RequestParam(defaultValue = "80.4365") double longitude,
            @RequestParam(defaultValue = "25") double radiusKm
    ) {
        String query = q == null ? "" : q.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> output = new ArrayList<>();

        for (Inventory item : inventory.findAll()) {
            if (item.availableStock() <= 0 || !item.active || !item.shop.verified) {
                continue;
            }

            String label = (
                    item.variant.brand + " " +
                    item.variant.model + " " +
                    item.variant.colour + " " +
                    item.variant.ramGb + "GB " +
                    item.variant.storageGb + "GB"
            ).toLowerCase(Locale.ROOT);

            if (!label.contains(query)) {
                continue;
            }

            double distance = distance(
                    latitude,
                    longitude,
                    item.shop.latitude,
                    item.shop.longitude
            );

            if (distance > Math.max(1, Math.min(radiusKm, 100))) {
                continue;
            }

            Map<String, Object> offer = new LinkedHashMap<>();

            offer.put("inventoryId", item.id);
            offer.put("brand", item.variant.brand);
            offer.put("model", item.variant.model);
            offer.put("colour", item.variant.colour);
            offer.put("ramGb", item.variant.ramGb);
            offer.put("storageGb", item.variant.storageGb);
            offer.put("condition", item.variant.conditionLabel);
            offer.put("price", item.price);
            offer.put("availableStock", item.availableStock());
            offer.put("shopId", item.shop.id);
            offer.put("shopName", item.shop.name);
            offer.put("address", item.shop.address);
            offer.put("distanceKm", Math.round(distance * 10.0) / 10.0);
            offer.put("updatedAt", item.updatedAt);

            output.add(offer);
        }

        output.sort(
                Comparator.comparing(
                        offer -> (BigDecimal) offer.get("price")
                )
        );

        return output;
    }

    private double distance(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        double p = Math.toRadians(lat2 - lat1);
        double l = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(p / 2) * Math.sin(p / 2) +
                Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) *
                Math.sin(l / 2) * Math.sin(l / 2);

        return 6371 * 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );
    }

    // -------------------- Retailer Inventory --------------------

    @PostMapping("/retailer/variants")
    ResponseEntity<?> createVariant(
            @Valid @RequestBody VariantRequest req,
            Authentication authentication
    ) {
        AppUser user = current(authentication);
        requireRole(user, "RETAILER");
        Shop profile = shops.findByOwnerId(user.id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Submit a shop registration first"));
        if (!profile.verified) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YET_VERIFIED: Your shop is not verified yet. An admin must approve your shop address before you can list products.");
        if (addressChanges.findFirstByShopIdAndStatusOrderByRequestedAtDesc(profile.id, "PENDING").isPresent()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "ADDRESS_CHANGE_PENDING: Your address change is awaiting admin review. You can list products after the review is complete.");

        PhoneVariant variant = variants.save(
                new PhoneVariant(
                        req.brand(),
                        req.model(),
                        req.colour(),
                        req.ramGb(),
                        req.storageGb()
                )
        );

        return ResponseEntity.status(201).body(
                Map.of(
                        "id", variant.id,
                        "brand", variant.brand,
                        "model", variant.model,
                        "colour", variant.colour,
                        "ramGb", variant.ramGb,
                        "storageGb", variant.storageGb
                )
        );
    }

    @PostMapping("/retailer/inventory")
    @Transactional
    ResponseEntity<?> createInventory(
            @Valid @RequestBody InventoryRequest req,
            Authentication authentication
    ) {
        AppUser user = current(authentication);
        requireRole(user, "RETAILER");

        PhoneVariant variant = variants.findById(req.variantId())
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Variant not found"
                        )
                );

        Shop shop = shops.findByOwnerId(user.id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YET_VERIFIED: Complete shop registration and wait for admin verification before listing products."));
        if (!shop.verified) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YET_VERIFIED: Your shop address is not verified yet. An admin must approve it before you can list products.");
        if (addressChanges.findFirstByShopIdAndStatusOrderByRequestedAtDesc(shop.id, "PENDING").isPresent()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "ADDRESS_CHANGE_PENDING: Your address change is awaiting admin review. Listing changes are paused until the review is complete.");
        if (req.price() == null || req.price().signum() <= 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than zero");
        Inventory item = inventory.save(new Inventory(shop, variant, req.price(), req.stock()));

        return ResponseEntity.status(201).body(
                Map.of(
                        "inventoryId", item.id,
                        "shopId", shop.id,
                        "availableStock", item.availableStock()
                )
        );
    }

    @GetMapping("/retailer/inventory")
    List<Map<String, Object>> myInventory(Authentication authentication) {
        AppUser user = current(authentication);
        requireRole(user, "RETAILER");

        return inventory.findByOwner(user.id)
                .stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("inventoryId", item.id);
                    row.put("brand", item.variant.brand);
                    row.put("model", item.variant.model);
                    row.put("colour", item.variant.colour);
                    row.put("ramGb", item.variant.ramGb);
                    row.put("storageGb", item.variant.storageGb);
                    row.put("price", item.price);
                    row.put("physicalStock", item.physicalStock);
                    row.put("reservedStock", item.reservedStock);
                    row.put("availableStock", item.availableStock());
                    row.put("active", item.active);
                    row.put("shopVerified", item.shop.verified);
                    return row;
                })
                .toList();
    }

    // -------------------- Reservation Creation --------------------

    @PostMapping("/reservations")
    @Transactional
    ResponseEntity<?> reserve(
            @Valid @RequestBody ReserveRequest req,
            Authentication authentication
    ) {
        AppUser customer = current(authentication);
        requireRole(customer, "CUSTOMER");

        Inventory item = inventory.lockById(req.inventoryId())
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Offer not found"
                        )
                );

        if (item.availableStock() < req.quantity()) {
            return ResponseEntity.status(409)
                    .body(new Message("Not enough available stock"));
        }

        item.reservedStock += req.quantity();
        item.updatedAt = Instant.now();

        Instant now = Instant.now();

        Reservation reservation = reservations.save(
                new Reservation(
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase(Locale.ROOT),
                        customer,
                        item,
                        req.quantity(),
                        now,
                        now.plus(hold)
                )
        );

        notify(item.shop.owner, "RESERVATION_CREATED", "New reservation", "Reservation " + reservation.reservationCode + " was placed for " + item.variant.brand + " " + item.variant.model + ".");
        notify(customer, "RESERVATION_CREATED", "Reservation confirmed", "Your reservation " + reservation.reservationCode + " is confirmed until " + reservation.expiresAt + ".");
        return ResponseEntity.status(201).body(reservationDto(reservation));
    }

    @GetMapping("/reservations")
    List<Map<String, Object>> myReservations(
            Authentication authentication
    ) {
        AppUser user = current(authentication);

        return reservations.findByCustomerIdOrderByCreatedAtDesc(user.id)
                .stream()
                .map(this::reservationDto)
                .toList();
    }

    // -------------------- Reservation Cancellation --------------------

    @PostMapping("/reservations/{id}/cancel")
    @Transactional
    ResponseEntity<?> cancel(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser user = current(authentication);

        // Lock reservation first.
        Reservation reservation = reservations.lockById(id)
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!reservation.customer.id.equals(user.id) &&
                !user.role.equals("ADMIN")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN
            );
        }

        if (!reservation.status.equals("ACTIVE")) {
            return ResponseEntity.status(409)
                    .body(new Message("Reservation is no longer active"));
        }

        // Consistent lock order: reservation, then inventory.
        Inventory item = inventory.lockById(reservation.inventory.id)
                .orElseThrow();

        if (!reservation.expiresAt.isAfter(Instant.now())) {
            expireOne(reservation, item);

            return ResponseEntity.status(409)
                    .body(new Message("Reservation expired"));
        }

        item.reservedStock -= reservation.quantity;
        item.updatedAt = Instant.now();
        reservation.status = "CANCELLED";

        return ResponseEntity.ok(reservationDto(reservation));
    }

    // -------------------- Retailer Pickup --------------------

    @PostMapping("/retailer/reservations/{id}/pickup")
    @Transactional
    ResponseEntity<?> pickup(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser user = current(authentication);
        requireRole(user, "RETAILER");

        // Lock reservation before checking or changing its status.
        Reservation reservation = reservations.lockById(id)
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.NOT_FOUND
                        )
                );

        // Consistent lock order: reservation, then inventory.
        Inventory item = inventory.lockById(reservation.inventory.id)
                .orElseThrow();

        if (!item.shop.owner.id.equals(user.id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Not your shop"
            );
        }

        if (!reservation.status.equals("ACTIVE")) {
            return ResponseEntity.status(409)
                    .body(new Message("Reservation is no longer active"));
        }

        if (!reservation.expiresAt.isAfter(Instant.now())) {
            expireOne(reservation, item);

            return ResponseEntity.status(409)
                    .body(new Message("Reservation expired"));
        }

        item.reservedStock -= reservation.quantity;
        item.physicalStock -= reservation.quantity;
        item.updatedAt = Instant.now();

        reservation.status = "COMPLETED";
        reservation.completedAt = Instant.now();

        return ResponseEntity.ok(reservationDto(reservation));
    }

    // -------------------- Admin operations --------------------

    @PutMapping("/profile/address")
    @Transactional
    Map<String,Object> updateCustomerAddress(@RequestBody Map<String,String> body, Authentication authentication) {
        AppUser user = current(authentication); requireRole(user, "CUSTOMER");
        String address = body == null ? null : body.get("address");
        if (address == null || address.isBlank() || address.trim().length() > 500) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Address is required and must be at most 500 characters");
        user.profileAddress = address.trim();
        return Map.of("address", user.profileAddress);
    }

    @GetMapping("/retailer/shop")
    Map<String,Object> retailerShop(Authentication authentication) {
        AppUser user = current(authentication); requireRole(user, "RETAILER");
        Shop shop = shops.findByOwnerId(user.id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Shop profile not found"));
        return shopDto(shop);
    }

    @GetMapping("/retailer/shop/address-change")
    Map<String,Object> retailerAddressChange(Authentication authentication) {
        AppUser user = current(authentication); requireRole(user, "RETAILER");
        Shop shop = shops.findByOwnerId(user.id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Shop profile not found"));
        ShopAddressChangeRequest req = addressChanges.findFirstByShopIdAndStatusOrderByRequestedAtDesc(shop.id, "PENDING").orElse(null);
        return addressChangeDto(req);
    }

    @PostMapping("/retailer/shop/address-change")
    @Transactional
    ResponseEntity<?> requestShopAddressChange(@RequestBody Map<String,Object> body, Authentication authentication) {
        AppUser user = current(authentication); requireRole(user, "RETAILER");
        Shop shop = shops.findByOwnerId(user.id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Shop profile not found"));
        if (addressChanges.findFirstByShopIdAndStatusOrderByRequestedAtDesc(shop.id, "PENDING").isPresent()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "You already have an address change awaiting admin review.");
        String proposedAddress = body == null ? null : Objects.toString(body.get("address"), "").trim();
        double lat, lon;
        try { lat = Double.parseDouble(Objects.toString(body == null ? null : body.get("latitude"), "")); lon = Double.parseDouble(Objects.toString(body == null ? null : body.get("longitude"), "")); }
        catch (Exception ex) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide valid latitude and longitude for the new shop address."); }
        if (proposedAddress.isBlank() || proposedAddress.length() > 500) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "New shop address is required and must be at most 500 characters.");
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordinates are outside valid latitude/longitude ranges.");
        if (shop.address.equalsIgnoreCase(proposedAddress) && shop.latitude == lat && shop.longitude == lon) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "The new address is the same as your current verified address.");
        ShopAddressChangeRequest req = addressChanges.save(new ShopAddressChangeRequest(shop, proposedAddress, lat, lon));
        users.findAll().stream().filter(u -> "ADMIN".equals(u.role)).forEach(admin -> notify(admin, "SHOP_ADDRESS_CHANGE", "Shop address change requested", shop.name + " requested admin review for a new shop address."));
        notify(user, "SHOP_ADDRESS_CHANGE", "Address change submitted", "Your new shop address is pending admin review. Your current verified address remains on file until approval.");
        return ResponseEntity.status(HttpStatus.CREATED).body(addressChangeDto(req));
    }

    @GetMapping("/admin/shop-address-changes/pending")
    List<Map<String,Object>> pendingAddressChanges(Authentication authentication) {
        requireRole(current(authentication), "ADMIN");
        return addressChanges.findByStatusOrderByRequestedAtAsc("PENDING").stream().map(this::addressChangeDto).toList();
    }

    @PostMapping("/admin/shop-address-changes/{id}/approve")
    @Transactional
    Map<String,Object> approveAddressChange(@PathVariable Long id, Authentication authentication) {
        requireRole(current(authentication), "ADMIN");
        ShopAddressChangeRequest req = addressChanges.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Address change request not found"));
        if (!"PENDING".equals(req.status)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "This address change has already been reviewed.");
        req.shop.address = req.proposedAddress; req.shop.latitude = req.proposedLatitude; req.shop.longitude = req.proposedLongitude; req.shop.verified = true; req.shop.verificationNote = "Address change approved by admin";
        req.status = "APPROVED"; req.reviewedAt = Instant.now(); req.reviewNote = "Approved by admin";
        notify(req.shop.owner, "SHOP_ADDRESS_APPROVED", "New shop address verified", "Your new shop address has been approved. Your shop profile has been updated.");
        return addressChangeDto(req);
    }

    @PostMapping("/admin/shop-address-changes/{id}/reject")
    @Transactional
    Map<String,Object> rejectAddressChange(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, Authentication authentication) {
        requireRole(current(authentication), "ADMIN");
        ShopAddressChangeRequest req = addressChanges.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Address change request not found"));
        if (!"PENDING".equals(req.status)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "This address change has already been reviewed.");
        String note = body == null ? "Please contact support for details." : body.getOrDefault("reason", "Please contact support for details.").trim();
        if (note.isBlank() || note.length() > 500) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason of 1â€“500 characters is required.");
        req.status = "REJECTED"; req.reviewedAt = Instant.now(); req.reviewNote = note;
        notify(req.shop.owner, "SHOP_ADDRESS_REJECTED", "Address change needs correction", note);
        return addressChangeDto(req);
    }

    @GetMapping("/admin/shops/pending")
    List<Map<String,Object>> pendingShops(Authentication authentication) {
        requireRole(current(authentication), "ADMIN");
        return shops.findByVerifiedFalse().stream().map(this::shopDto).toList();
    }

    @PostMapping("/admin/shops/{id}/approve")
    @Transactional
    Map<String,Object> approveShop(@PathVariable Long id, Authentication authentication) {
        requireRole(current(authentication), "ADMIN");
        Shop shop = shops.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        shop.verified = true; shop.verificationNote = "Verified by admin";
        notify(shop.owner, "SHOP_APPROVED", "Shop verified", "Your shop address has been approved. You can now publish inventory.");
        return shopDto(shop);
    }

    @PostMapping("/admin/shops/{id}/reject")
    @Transactional
    Map<String,Object> rejectShop(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, Authentication authentication) {
        requireRole(current(authentication), "ADMIN");
        Shop shop = shops.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        String note = body == null ? "Please contact support to correct your shop details." : body.getOrDefault("reason", "Please contact support to correct your shop details.");
        if (note.isBlank() || note.length() > 500) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason of 1â€“500 characters is required");
        shop.verified = false; shop.verificationNote = note;
        notify(shop.owner, "SHOP_REJECTED", "Shop verification needs attention", note);
        return shopDto(shop);
    }

    @PutMapping("/retailer/inventory/{id}")
    @Transactional
    Map<String,Object> updateInventory(@PathVariable Long id, @Valid @RequestBody InventoryUpdateRequest req, Authentication authentication) {
        AppUser user = current(authentication); requireRole(user, "RETAILER");
        Shop shop = shops.findByOwnerId(user.id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Shop profile required"));
        if (!shop.verified) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YET_VERIFIED: Your shop address is pending admin verification.");
        if (addressChanges.findFirstByShopIdAndStatusOrderByRequestedAtDesc(shop.id, "PENDING").isPresent()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "ADDRESS_CHANGE_PENDING: Your address change is awaiting admin review.");
        Inventory item = inventory.lockById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!item.shop.id.equals(shop.id)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own listings");
        if (req.price() == null || req.price().signum() <= 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than zero");
        if (req.stock() < item.reservedStock) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Stock cannot be lower than currently reserved units");
        item.price = req.price(); item.physicalStock = req.stock(); item.updatedAt = Instant.now();
        notify(users.findAll().stream().filter(u -> "ADMIN".equals(u.role)).findFirst().orElse(user), "INVENTORY_UPDATED", "Inventory updated", shop.name + " updated a listing.");
        return inventoryDto(item);
    }

    @GetMapping("/notifications")
    Map<String,Object> myNotifications(Authentication authentication) {
        AppUser user = current(authentication);
        return Map.of("unreadCount", notifications.countByRecipientIdAndReadFalse(user.id), "items", notifications.findTop100ByRecipientIdOrderByCreatedAtDesc(user.id).stream().map(n -> Map.<String,Object>of("id",n.id,"eventType",n.eventType,"title",n.title,"message",n.message,"read",n.read,"createdAt",n.createdAt)).toList());
    }

    @PostMapping("/notifications/{id}/read")
    @Transactional
    ResponseEntity<?> readNotification(@PathVariable Long id, Authentication authentication) {
        AppUser user = current(authentication);
        Notification n = notifications.findByIdAndRecipientId(id,user.id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.read = true; return ResponseEntity.ok(Map.of("read",true));
    }

    private void notify(AppUser recipient, String type, String title, String message) { if (recipient != null) notifications.save(new Notification(recipient,type,title,message)); }
    private Map<String,Object> addressChangeDto(ShopAddressChangeRequest req) { Map<String,Object> m=new LinkedHashMap<>(); if(req==null){m.put("pending",false);return m;} m.put("id",req.id);m.put("shopId",req.shop.id);m.put("shopName",req.shop.name);m.put("ownerEmail",req.shop.owner.email);m.put("currentAddress",req.shop.address);m.put("proposedAddress",req.proposedAddress);m.put("proposedLatitude",req.proposedLatitude);m.put("proposedLongitude",req.proposedLongitude);m.put("status",req.status);m.put("requestedAt",req.requestedAt);m.put("reviewNote",req.reviewNote);m.put("pending", "PENDING".equals(req.status));return m; }
    private Map<String,Object> shopDto(Shop shop) { Map<String,Object> m=new LinkedHashMap<>(); m.put("id",shop.id);m.put("ownerEmail",shop.owner.email);m.put("name",shop.name);m.put("address",shop.address);m.put("latitude",shop.latitude);m.put("longitude",shop.longitude);m.put("verified",shop.verified);m.put("verificationNote",shop.verificationNote);return m; }
    private Map<String,Object> inventoryDto(Inventory item) { Map<String,Object> m=new LinkedHashMap<>();m.put("inventoryId",item.id);m.put("price",item.price);m.put("physicalStock",item.physicalStock);m.put("reservedStock",item.reservedStock);m.put("availableStock",item.availableStock());m.put("active",item.active);return m; }

    @GetMapping("/admin/overview")
    Map<String, Object> adminOverview(Authentication authentication) {
        AppUser user = current(authentication);
        requireRole(user, "ADMIN");
        long activeReservations = reservations.findAll().stream()
                .filter(r -> r.status.equals("ACTIVE") && r.expiresAt.isAfter(Instant.now()))
                .count();
        return Map.of(
                "users", users.count(),
                "shops", shops.count(),
                "inventoryItems", inventory.count(),
                "reservations", reservations.count(),
                "activeReservations", activeReservations
        );
    }

@GetMapping("/admin/inventory")
List<Map<String, Object>> adminInventory(Authentication authentication) {
    AppUser user = current(authentication);
    requireRole(user, "ADMIN");

    return inventory.findAll().stream()
            .<Map<String, Object>>map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("inventoryId", item.id);
                row.put("brand", item.variant.brand);
                row.put("model", item.variant.model);
                row.put("colour", item.variant.colour);
                row.put("ramGb", item.variant.ramGb);
                row.put("storageGb", item.variant.storageGb);
                row.put("price", item.price);
                row.put("physicalStock", item.physicalStock);
                row.put("reservedStock", item.reservedStock);
                row.put("availableStock", item.availableStock());
                row.put("shopName", item.shop.name);
                row.put("address", item.shop.address);
                row.put("verified", item.shop.verified);
                return row;
            })
            .toList();
}


    @GetMapping("/admin/reservations")
    List<Map<String, Object>> adminReservations(Authentication authentication) {
        AppUser user = current(authentication);
        requireRole(user, "ADMIN");
        return reservations.findAll().stream()
                .sorted(Comparator.comparing((Reservation r) -> r.createdAt).reversed())
                .map(r -> {
                    Map<String, Object> dto = new LinkedHashMap<>(reservationDto(r));
                    dto.put("customerEmail", r.customer.email);
                    dto.put("shopName", r.inventory.shop.name);
                    return dto;
                }).toList();
    }

    // -------------------- Reservation Helpers --------------------

    private Map<String, Object> reservationDto(Reservation reservation) {
        return Map.of(
                "id", reservation.id,
                "code", reservation.reservationCode,
                "inventoryId", reservation.inventory.id,
                "product",
                reservation.inventory.variant.brand + " " +
                reservation.inventory.variant.model + " " +
                reservation.inventory.variant.colour + " " +
                reservation.inventory.variant.storageGb + "GB",
                "quantity", reservation.quantity,
                "status", reservation.status,
                "createdAt", reservation.createdAt,
                "expiresAt", reservation.expiresAt
        );
    }

    private void expireOne(Reservation reservation, Inventory item) {
        if (reservation.status.equals("ACTIVE")) {
            item.reservedStock -= reservation.quantity;
            item.updatedAt = Instant.now();
            reservation.status = "EXPIRED";
            notify(reservation.customer, "RESERVATION_EXPIRED", "Reservation expired", "Reservation " + reservation.reservationCode + " expired and the stock hold was released.");
            notify(reservation.inventory.shop.owner, "RESERVATION_EXPIRED", "Reservation expired", "Reservation " + reservation.reservationCode + " expired and stock is available again.");
        }
    }

    // -------------------- Automatic Expiry --------------------

    @Scheduled(fixedDelay = 15000)
    @Transactional
    void expireReservations() {
        // Fetch candidate IDs, then lock and recheck each reservation.
        List<Long> expiredIds = reservations.findExpiredIds(Instant.now());

        for (Long id : expiredIds) {
            Reservation reservation = reservations.lockById(id)
                    .orElse(null);

            if (reservation == null ||
                    !reservation.status.equals("ACTIVE")) {
                continue;
            }

            // Recheck after acquiring the reservation lock.
            if (reservation.expiresAt.isAfter(Instant.now())) {
                continue;
            }

            Inventory item = inventory.lockById(
                    reservation.inventory.id
            ).orElse(null);

            if (item != null) {
                expireOne(reservation, item);
            }
        }
    }

    // -------------------- Admin --------------------

    @GetMapping("/admin/variants")
    List<Map<String, Object>> allVariants(
            Authentication authentication
    ) {
        requireRole(current(authentication), "ADMIN");

        return variants.findAll()
                .stream()
                .map(variant -> Map.<String, Object>of(
                        "id", variant.id,
                        "brand", variant.brand,
                        "model", variant.model,
                        "colour", variant.colour,
                        "ramGb", variant.ramGb,
                        "storageGb", variant.storageGb
                ))
                .toList();
    }
}
