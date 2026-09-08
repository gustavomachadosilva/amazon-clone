package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public Page<Product> search(String query, String category, Pageable pageable) {
        return productRepository.search(query, category, pageable);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public Product create(Product product) {
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product update(Long id, Product changes) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setPrice(changes.getPrice());
        existing.setStockQuantity(changes.getStockQuantity());
        existing.setCategory(changes.getCategory());
        existing.setImageUrl(changes.getImageUrl());
        return productRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Product not found: " + id);
        }
        productRepository.deleteById(id);
    }

    @Override
    public Page<Product> findBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable);
    }

    // REQUIRES_NEW: this is invoked from an AFTER_COMMIT event listener, where the
    // triggering transaction's resources are still bound to the thread until the
    // listener returns — a default-propagation @Transactional here would silently
    // join that (already-physically-committed) transaction instead of opening a
    // fresh one, so retries would keep reusing the same stale persistence context.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        int updated = product.getStockQuantity() - quantity;
        if (updated < 0) {
            throw new IllegalStateException("Insufficient stock for product " + productId);
        }
        product.setStockQuantity(updated);
        productRepository.save(product);
    }

    @Override
    public List<String> listCategories() {
        return productRepository.findDistinctCategories();
    }
}
