package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void listCategoriesDelegatesToRepositoryAndReturnsItsValue() {
        List<String> categories = List.of("eletronicos", "livros");
        when(productRepository.findDistinctCategories()).thenReturn(categories);

        List<String> result = productService.listCategories();

        assertThat(result).isEqualTo(categories);
        verify(productRepository).findDistinctCategories();
    }

    @Test
    void listCategoriesReturnsEmptyListWithoutThrowing() {
        when(productRepository.findDistinctCategories()).thenReturn(List.of());

        List<String> result = productService.listCategories();

        assertThat(result).isEmpty();
    }

    @Test
    void searchDelegatesToRepository() {
        Pageable pageable = Pageable.unpaged();
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.search("query", "category", pageable)).thenReturn(page);

        Page<Product> result = productService.search("query", "category", pageable);

        assertThat(result).isSameAs(page);
        verify(productRepository).search("query", "category", pageable);
    }

    @Test
    void findByIdDelegatesToRepository() {
        Product product = Product.builder().id(1L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Optional<Product> result = productService.findById(1L);

        assertThat(result).contains(product);
    }

    @Test
    void createSavesProduct() {
        Product product = Product.builder().name("Widget").build();
        Product saved = Product.builder().id(1L).name("Widget").build();
        when(productRepository.save(product)).thenReturn(saved);

        Product result = productService.create(product);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void findBySellerDelegatesToRepository() {
        Pageable pageable = Pageable.unpaged();
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findBySellerId(10L, pageable)).thenReturn(page);

        Page<Product> result = productService.findBySeller(10L, pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    void decreaseStockReducesStockWhenSufficient() {
        Product product = Product.builder().id(1L).stockQuantity(5).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.decreaseStock(1L, 3);

        assertThat(product.getStockQuantity()).isEqualTo(2);
        verify(productRepository).save(product);
    }

    @Test
    void decreaseStockThrowsWhenInsufficient() {
        Product product = Product.builder().id(1L).stockQuantity(1).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decreaseStockThrowsWhenProductNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.decreaseStock(1L, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
