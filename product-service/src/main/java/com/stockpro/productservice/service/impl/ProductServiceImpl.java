package com.stockpro.productservice.service.impl;

import lombok.RequiredArgsConstructor;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.stockpro.productservice.dto.ProductRequestDTO;
import com.stockpro.productservice.dto.ProductResponseDTO;
import com.stockpro.productservice.entity.Product;
import com.stockpro.productservice.repository.ProductRepository;
import com.stockpro.productservice.service.ProductService;
import com.stockpro.productservice.exception.ResourceNotFoundException;
import com.stockpro.productservice.mapper.ProductMapper;

import java.util.List;

import static com.stockpro.productservice.mapper.ProductMapper.*;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final com.stockpro.productservice.client.WarehouseClient warehouseClient;
    private final CacheManager cacheManager;

    //  CREATE
    @Override
    public ProductResponseDTO createProduct(ProductRequestDTO dto) {

        Product product = toEntity(dto);

        repository.findBySkuAndIsActiveTrue(product.getSku())
                .ifPresent(p -> {
                    throw new RuntimeException("SKU already exists");
                });

        Product saved = repository.save(product);
        evictProductCaches(saved);
        return toDTO(saved);
    }

    //  GET BY ID
    @Override
    @Cacheable(value = "productsById", key = "#id", unless = "#result == null")
    public ProductResponseDTO getById(Long id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toDTO(product);
    }

    //  GET BY SKU
    @Override
    @Cacheable(value = "productsBySku", key = "#sku", unless = "#result == null")
    public ProductResponseDTO getBySku(String sku) {
        Product product = repository.findBySkuAndIsActiveTrue(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU"));
        return toDTO(product);
    }

    //  GET ALL (paginated)
    @Override
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
        return repository.findByIsActiveTrue(pageable)
                .map(ProductMapper::toDTO);
    }

    //  GET ALL — flat list for internal Feign calls (no pagination)
    @Override
    public List<ProductResponseDTO> getAllActiveProducts() {
        return repository.findByIsActiveTrue()
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    //  GET BY CATEGORY
    @Override
    public List<ProductResponseDTO> getByCategory(String category) {
        return repository.findByCategoryAndIsActiveTrue(category)
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    //  GET BY BRAND
    @Override
    public List<ProductResponseDTO> getByBrand(String brand) {
        return repository.findByBrandAndIsActiveTrue(brand)
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    //  SEARCH
    @Override
    public List<ProductResponseDTO> searchProducts(String name) {
        return repository.findByNameContainingIgnoreCaseAndIsActiveTrue(name)
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    @Override
    public List<ProductResponseDTO> filterProducts(String query, String category) {
        String cleanQuery = (query == null || query.isBlank()) ? null : query.trim();
        String cleanCategory = (category == null || category.isBlank() || category.equalsIgnoreCase("All")) ? null : category.trim();
        return repository.filterActiveProducts(cleanQuery, cleanCategory)
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    //  UPDATE
    @Override
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO dto) {

        Product existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setCategory(dto.getCategory());
        existing.setBrand(dto.getBrand());
        existing.setUnitOfMeasure(dto.getUnitOfMeasure());
        existing.setCostPrice(dto.getCostPrice());
        existing.setSellingPrice(dto.getSellingPrice());
        existing.setReorderLevel(dto.getReorderLevel());
        existing.setMaxStockLevel(dto.getMaxStockLevel());
        existing.setLeadTimeDays(dto.getLeadTimeDays());
        existing.setBarcode(dto.getBarcode());
        existing.setImageUrl(dto.getImageUrl());

        Product saved = repository.save(existing);
        evictProductCaches(saved);
        return toDTO(saved);
    }

    //  SOFT DELETE
    @Override
    public void deactivateProduct(Long id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setIsActive(false);
        repository.save(product);
        evictProductCaches(product);
    }

    //  HARD DELETE
    @Override
    public void deleteProduct(Long id) {
        repository.findById(id).ifPresent(this::evictProductCaches);
        repository.deleteById(id);
    }

    //  GET BY BARCODE
    @Override
    public ProductResponseDTO getByBarcode(String barcode) {
        Product product = repository.findByBarcodeAndIsActiveTrue(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with barcode"));

        return toDTO(product);
    }

    //  Feign: Update totalStock called by Warehouse Service
    @Override
    public void updateTotalStock(Long productId, Integer total) {
        Product product = repository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        product.setTotalStock(total);
        repository.save(product);
        evictProductCaches(product);
        System.out.println(" [Feign] Stock synced for Product " + productId + " -> " + total + " units");
    }

    //  Get all deactivated products
    @Override
    public List<ProductResponseDTO> getInactiveProducts() {
        return repository.findByIsActive(false)
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    //  Reactivate a deactivated product
    @Override
    public void activateProduct(Long id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setIsActive(true);
        repository.save(product);
        evictProductCaches(product);
    }

    private void evictProductCaches(Product product) {
        evict("productsById", product.getProductId());
        evict("productsBySku", product.getSku());
    }

    private void evict(String cacheName, Object key) {
        if (key == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            try {
                cache.evict(key);
            } catch (RuntimeException ex) {
                System.err.println(" Redis cache evict skipped for " + cacheName + " key=" + key + ": " + ex.getMessage());
            }
        }
    }

}
