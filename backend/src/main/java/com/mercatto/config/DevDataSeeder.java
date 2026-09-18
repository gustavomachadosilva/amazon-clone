package com.mercatto.config;

import com.mercatto.catalog.service.AmazonProductSeeder;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.UserSeeder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates local-development seeding across modules: users first, then catalog products
 * distributed across the seed sellers. Only active on the {@code dev} profile — never runs in
 * production.
 *
 * <p>Lives outside the {@code users}/{@code catalog} module packages (rather than inside either
 * one) because it depends on both of their public {@code service} contracts — a cross-module
 * concern belongs in the neutral {@code config} package, not inside either module. This does not
 * violate the "no transaction spans two modules" rule: {@link #seedDevData()} itself is not
 * {@code @Transactional} — each call it makes ({@code userSeeder.seed()}, which registers users
 * one at a time within {@code users}' own transactions, and
 * {@code amazonProductSeeder.seedProducts(..)}, transactional within {@code catalog}) opens and
 * commits its own transaction, sequentially.
 */
@Component
@Profile("dev")
public class DevDataSeeder {

    private final UserSeeder userSeeder;
    private final AmazonProductSeeder amazonProductSeeder;

    public DevDataSeeder(UserSeeder userSeeder, AmazonProductSeeder amazonProductSeeder) {
        this.userSeeder = userSeeder;
        this.amazonProductSeeder = amazonProductSeeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDevData() {
        List<User> users = userSeeder.seed();
        List<Long> sellerIds = users.stream()
                .filter(u -> u.getRole() == UserRole.SELLER)
                .map(User::getId)
                .toList();
        if (sellerIds.isEmpty()) {
            throw new IllegalStateException("Nenhum seller de seed encontrado após seedDevData");
        }
        amazonProductSeeder.seedProducts(sellerIds);
    }
}
