package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.catalog.service.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    void findByIdDelegatesToRepository() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Product product = Product.builder()
                .id(1L)
                .name("Widget")
                .description("A widget")
                .price(BigDecimal.TEN)
                .stockQuantity(5)
                .category("tools")
                .imageUrl("http://image")
                .brand("Acme")
                .warrantyMonths(12)
                .modelNumber("MDL-1")
                .listPrice(BigDecimal.valueOf(15))
                .sellerId(10L)
                .createdAt(createdAt)
                .build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Optional<ProductService.ProductSummary> result = productService.findById(1L);

        assertThat(result).contains(new ProductService.ProductSummary(
                1L, "Widget", "A widget", BigDecimal.TEN, 5, "tools", "http://image", "Acme", 12,
                "MDL-1", BigDecimal.valueOf(15), 10L, createdAt));
    }

    @Test
    void createSavesProduct() {
        Product product = Product.builder().name("Widget").price(BigDecimal.TEN).warrantyMonths(24).build();
        Product saved = Product.builder().id(1L).name("Widget").build();
        when(productRepository.save(product)).thenReturn(saved);

        Product result = productService.create(product);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void createDefaultsWarrantyMonthsTo12WhenOmitted() {
        Product product = Product.builder().name("Widget").price(BigDecimal.TEN).build();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.create(product);

        assertThat(result.getWarrantyMonths()).isEqualTo(12);
    }

    @Test
    void createKeepsExplicitWarrantyMonths() {
        Product product = Product.builder().name("Widget").price(BigDecimal.TEN).warrantyMonths(36).build();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.create(product);

        assertThat(result.getWarrantyMonths()).isEqualTo(36);
    }

    @Test
    void createThrowsWhenListPriceIsNotGreaterThanPrice() {
        Product product = Product.builder().name("Widget").price(BigDecimal.TEN).listPrice(BigDecimal.TEN).build();

        assertThatThrownBy(() -> productService.create(product))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void createAllowsListPriceGreaterThanPrice() {
        Product product = Product.builder().name("Widget").price(BigDecimal.TEN)
                .listPrice(BigDecimal.valueOf(15)).build();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.create(product);

        assertThat(result.getListPrice()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    @Test
    void findBySellerDelegatesToRepository() {
        Pageable pageable = Pageable.unpaged();
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Product product = Product.builder()
                .id(1L)
                .name("Widget")
                .description("A widget")
                .price(BigDecimal.TEN)
                .stockQuantity(5)
                .category("tools")
                .imageUrl("http://image")
                .brand("Acme")
                .warrantyMonths(12)
                .modelNumber("MDL-1")
                .listPrice(BigDecimal.valueOf(15))
                .sellerId(10L)
                .createdAt(createdAt)
                .build();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findBySellerId(10L, pageable)).thenReturn(page);

        Page<ProductService.ProductSummary> result = productService.findBySeller(10L, pageable);

        assertThat(result.getContent()).containsExactly(new ProductService.ProductSummary(
                1L, "Widget", "A widget", BigDecimal.TEN, 5, "tools", "http://image", "Acme", 12,
                "MDL-1", BigDecimal.valueOf(15), 10L, createdAt));
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
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateSavesChangesAndReturnsUpdatedProduct() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Product existing = Product.builder()
                .id(1L)
                .sellerId(10L)
                .createdAt(createdAt)
                .name("Old name")
                .description("Old description")
                .price(BigDecimal.ONE)
                .stockQuantity(1)
                .category("old-category")
                .imageUrl("http://old")
                .build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product changes = Product.builder()
                .name("New name")
                .description("New description")
                .price(BigDecimal.TEN)
                .stockQuantity(5)
                .category("new-category")
                .imageUrl("http://new")
                .brand("NewBrand")
                .warrantyMonths(24)
                .modelNumber("MDL-2")
                .listPrice(BigDecimal.valueOf(15))
                .build();

        Product result = productService.update(1L, changes);

        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getDescription()).isEqualTo("New description");
        assertThat(result.getPrice()).isEqualTo(BigDecimal.TEN);
        assertThat(result.getStockQuantity()).isEqualTo(5);
        assertThat(result.getCategory()).isEqualTo("new-category");
        assertThat(result.getImageUrl()).isEqualTo("http://new");
        assertThat(result.getBrand()).isEqualTo("NewBrand");
        assertThat(result.getWarrantyMonths()).isEqualTo(24);
        assertThat(result.getModelNumber()).isEqualTo("MDL-2");
        assertThat(result.getListPrice()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSellerId()).isEqualTo(10L);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);

        verify(productRepository).save(existing);
    }

    @Test
    void updateDefaultsWarrantyMonthsWhenChangesOmitIt() {
        Product existing = Product.builder().id(1L).name("Old name").price(BigDecimal.ONE).warrantyMonths(24).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product changes = Product.builder().name("New name").price(BigDecimal.TEN).build();

        Product result = productService.update(1L, changes);

        assertThat(result.getWarrantyMonths()).isEqualTo(12);
    }

    @Test
    void updateThrowsWhenProductNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        Product changes = Product.builder().name("New name").build();

        assertThatThrownBy(() -> productService.update(1L, changes))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateThrowsWhenListPriceIsNotGreaterThanPrice() {
        Product changes = Product.builder().name("New name").price(BigDecimal.TEN)
                .listPrice(BigDecimal.valueOf(9)).build();

        assertThatThrownBy(() -> productService.update(1L, changes))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteRemovesProductWhenExists() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.delete(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteThrowsWhenProductNotFound() {
        when(productRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void searchWithRatingBuildsViewsFromTheDenormalizedRatingWithoutCallingReviews() {
        Product product1 = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(5)
                .category("tools").averageRating(4.5).reviewCount(2L).build();
        Product product2 = Product.builder().id(2L).name("Gadget").price(BigDecimal.ONE).stockQuantity(3)
                .category("tools").build();
        when(productRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product1, product2)));

        Page<ProductService.ProductView> result = productService.searchWithRating(
                criteria(ProductSort.RELEVANCE), 0, 10);

        assertThat(result.getContent()).hasSize(2);
        ProductService.ProductView view1 = result.getContent().get(0);
        assertThat(view1.id()).isEqualTo(1L);
        assertThat(view1.averageRating()).isEqualTo(4.5);
        assertThat(view1.reviewCount()).isEqualTo(2L);

        ProductService.ProductView view2 = result.getContent().get(1);
        assertThat(view2.averageRating()).isEqualTo(0.0);
        assertThat(view2.reviewCount()).isEqualTo(0L);
    }

    @ParameterizedTest
    @EnumSource(ProductSort.class)
    void searchWithRatingPassesPageSizeAndSortToRepository(ProductSort sort) {
        when(productRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());

        productService.searchWithRating(criteria(sort), 2, 25);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(anySpecification(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort()).isEqualTo(sort.toSort());
    }

    @Test
    void findByIdWithRatingReturnsEmptyWhenProductNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(productService.findByIdWithRating(1L)).isEmpty();
    }

    @Test
    void findByIdWithRatingUsesTheDenormalizedRating() {
        Product product = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(5)
                .category("tools").averageRating(3.0).reviewCount(4L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Optional<ProductService.ProductView> result = productService.findByIdWithRating(1L);

        assertThat(result).isPresent();
        assertThat(result.get().averageRating()).isEqualTo(3.0);
        assertThat(result.get().reviewCount()).isEqualTo(4L);
    }

    private static ProductSearchCriteria criteria(ProductSort sort) {
        return new ProductSearchCriteria("query", "category", null, null, null, sort);
    }

    @SuppressWarnings("unchecked")
    private static Specification<Product> anySpecification() {
        return any(Specification.class);
    }
}
