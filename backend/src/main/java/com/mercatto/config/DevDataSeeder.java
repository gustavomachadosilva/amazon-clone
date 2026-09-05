package com.mercatto.config;

import com.mercatto.catalog.service.DummyJsonSeeder;
import com.mercatto.users.domain.User;
import com.mercatto.users.service.UserSeeder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates local-development seeding across modules: users first, then catalog products
 * attached to the seed seller. Only active on the {@code dev} profile — never runs in
 * production.
 *
 * <p>Lives outside the {@code users}/{@code catalog} module packages (rather than inside either
 * one) because it depends on both of their public {@code service} contracts — a cross-module
 * concern belongs in the neutral {@code config} package, not inside either module. This does not
 * violate the "no transaction spans two modules" rule: {@link #seedDevData()} itself is not
 * {@code @Transactional} — each call it makes ({@code userSeeder.seed()}, which registers users
 * one at a time within {@code users}' own transactions, and
 * {@code dummyJsonSeeder.seedProducts(..)}, transactional within {@code catalog}) opens and
 * commits its own transaction, sequentially.
 */
@Component
@Profile("dev")
public class DevDataSeeder {

    private final UserSeeder userSeeder;
    private final DummyJsonSeeder dummyJsonSeeder;

    public DevDataSeeder(UserSeeder userSeeder, DummyJsonSeeder dummyJsonSeeder) {
        this.userSeeder = userSeeder;
        this.dummyJsonSeeder = dummyJsonSeeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDevData() {
        List<User> users = userSeeder.seed();
        Long sellerId = users.stream()
                .filter(u -> u.getEmail().equals(UserSeeder.DEFAULT_SELLER_EMAIL))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seller de seed padrão não encontrado após seedDevData"))
                .getId();
        dummyJsonSeeder.seedProducts(sellerId);
    }
}
