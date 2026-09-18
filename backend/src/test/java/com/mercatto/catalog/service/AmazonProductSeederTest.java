package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmazonProductSeederTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void seedProductsSkipsWhenDatabaseAlreadyPopulated() {
        AmazonProductSeeder seeder = new AmazonProductSeeder(productRepository);
        when(productRepository.count()).thenReturn(1L);

        seeder.seedProducts(List.of(1L));

        verify(productRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void seedProductsThrowsWhenNoSellerIsGiven() {
        AmazonProductSeeder seeder = new AmazonProductSeeder(productRepository);
        when(productRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> seeder.seedProducts(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seedProductsLoadsTheBundledSampleAndDistributesSellersRoundRobin() {
        AmazonProductSeeder seeder = new AmazonProductSeeder(productRepository);
        when(productRepository.count()).thenReturn(0L);

        seeder.seedProducts(List.of(10L, 20L));

        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());
        List<Product> products = captor.getValue();

        assertThat(products).hasSize(500);
        assertThat(products).allSatisfy(p -> {
            assertThat(p.getName()).isNotBlank();
            assertThat(p.getPrice()).isPositive();
            assertThat(p.getStockQuantity()).isPositive();
            assertThat(p.getCategory()).isNotBlank();
            assertThat(p.getImageUrl()).isNotBlank();
            assertThat(p.getSellerId()).isIn(10L, 20L);
        });
        assertThat(products).extracting(Product::getSellerId).contains(10L, 20L);
    }
}
