package com.mercatto.users.service;

import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds a fixed, varied set of buyer and seller accounts for local development, so
 * {@code Home.tsx} and {@code SellerDashboard.tsx} never render empty against a freshly
 * created database. Only active on the {@code dev} profile — never runs in production.
 */
@Component
@Profile("dev")
public class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    /** Email of the anchor seller account that owns the seeded catalog products. */
    public static final String DEFAULT_SELLER_EMAIL = "seller.demo@mercatto.dev";

    private record SeedUser(String name, String email, String password, UserRole role) {}

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("Loja Demo Mercatto", DEFAULT_SELLER_EMAIL, "Seller123!", UserRole.SELLER),
            new SeedUser("Eletronicos Silva", "seller.silva@mercatto.dev", "Seller123!", UserRole.SELLER),
            new SeedUser("Casa & Cia Store", "seller.casaecia@mercatto.dev", "Seller123!", UserRole.SELLER),
            new SeedUser("Ana Souza", "ana.souza@mercatto.dev", "Buyer123!", UserRole.BUYER),
            new SeedUser("Bruno Costa", "bruno.costa@mercatto.dev", "Buyer123!", UserRole.BUYER),
            new SeedUser("Carla Oliveira", "carla.oliveira@mercatto.dev", "Buyer123!", UserRole.BUYER),
            new SeedUser("Diego Pereira", "diego.pereira@mercatto.dev", "Buyer123!", UserRole.BUYER),
            new SeedUser("Elisa Fernandes", "elisa.fernandes@mercatto.dev", "Buyer123!", UserRole.BUYER),
            new SeedUser("Felipe Almeida", "felipe.almeida@mercatto.dev", "Buyer123!", UserRole.BUYER)
    );

    private final UserService userService;

    public UserSeeder(UserService userService) {
        this.userService = userService;
    }

    /**
     * Ensures every seed user exists, creating the ones that don't yet and reusing the ones
     * that already do (idempotent — safe to run on every application start).
     *
     * @return the seed users, in the same order as the fixed seed list.
     */
    public List<User> seed() {
        List<User> result = new ArrayList<>(SEED_USERS.size());
        int created = 0;
        int existing = 0;

        for (SeedUser seedUser : SEED_USERS) {
            try {
                User user = userService.register(seedUser.name(), seedUser.email(), seedUser.password(), seedUser.role());
                result.add(user);
                created++;
            } catch (EmailAlreadyExistsException e) {
                User user = userService.findByEmail(seedUser.email())
                        .orElseThrow(() -> new IllegalStateException(
                                "Usuário de seed '" + seedUser.email() + "' reportado como existente, mas não encontrado", e));
                result.add(user);
                existing++;
            }
        }

        log.info("User seed: {} created, {} already existing (total {}).", created, existing, result.size());
        return result;
    }
}
