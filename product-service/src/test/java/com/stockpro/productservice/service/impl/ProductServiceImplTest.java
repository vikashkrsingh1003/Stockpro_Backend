package com.stockpro.productservice.service.impl;

import com.stockpro.productservice.client.WarehouseClient;
import com.stockpro.productservice.dto.ProductRequestDTO;
import com.stockpro.productservice.dto.ProductResponseDTO;
import com.stockpro.productservice.entity.Product;
import com.stockpro.productservice.exception.ResourceNotFoundException;
import com.stockpro.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cache.CacheManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private WarehouseClient warehouseClient;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private ProductServiceImpl service;

    private Product product;

    @BeforeEach
    void setup() {
        product = Product.builder()
                .productId(1L)
                .name("Test Product")
                .sku("SKU123")
                .category("Electronics")
                .brand("TestBrand")
                .barcode("BAR123")
                .costPrice(100.0)
                .sellingPrice(120.0)
                .totalStock(0)
                .isActive(true)
                .build();
    }

    @Test
    void createProduct_success() {
        ProductRequestDTO dto = request();
        when(repository.findBySkuAndIsActiveTrue("SKU123")).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenReturn(product);

        ProductResponseDTO response = service.createProduct(dto);

        assertEquals(1L, response.getProductId());
        assertEquals("SKU123", response.getSku());
        verify(repository).save(any(Product.class));
    }

    @Test
    void createProduct_shouldThrow_whenSkuExists() {
        ProductRequestDTO dto = request();
        when(repository.findBySkuAndIsActiveTrue("SKU123")).thenReturn(Optional.of(product));

        assertThrows(RuntimeException.class, () -> service.createProduct(dto));

        verify(repository, never()).save(any());
    }

    @Test
    void getById_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponseDTO response = service.getById(1L);

        assertEquals("Test Product", response.getName());
    }

    @Test
    void getById_notFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(1L));
    }

    @Test
    void getBySku_success() {
        when(repository.findBySkuAndIsActiveTrue("SKU123")).thenReturn(Optional.of(product));

        assertEquals(1L, service.getBySku("SKU123").getProductId());
    }

    @Test
    void getBySku_notFound() {
        when(repository.findBySkuAndIsActiveTrue("SKU123")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getBySku("SKU123"));
    }

    @Test
    void getAllProducts_success() {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(repository.findByIsActiveTrue(any(Pageable.class))).thenReturn(page);

        Page<ProductResponseDTO> result = service.getAllProducts(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAllActiveProducts_success() {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(product));

        assertEquals(1, service.getAllActiveProducts().size());
    }

    @Test
    void getByCategory_success() {
        when(repository.findByCategoryAndIsActiveTrue("Electronics")).thenReturn(List.of(product));

        assertEquals(1, service.getByCategory("Electronics").size());
    }

    @Test
    void getByBrand_success() {
        when(repository.findByBrandAndIsActiveTrue("TestBrand")).thenReturn(List.of(product));

        assertEquals(1, service.getByBrand("TestBrand").size());
    }

    @Test
    void searchProducts_success() {
        when(repository.findByNameContainingIgnoreCaseAndIsActiveTrue("Test")).thenReturn(List.of(product));

        assertEquals(1, service.searchProducts("Test").size());
    }

    @Test
    void filterProducts_withQueryAndCategory() {
        when(repository.filterActiveProducts("Test", "Electronics")).thenReturn(List.of(product));

        assertEquals(1, service.filterProducts(" Test ", " Electronics ").size());
    }

    @Test
    void filterProducts_withBlankAndAllCategory() {
        when(repository.filterActiveProducts(null, null)).thenReturn(List.of(product));

        assertEquals(1, service.filterProducts(" ", "All").size());
    }

    @Test
    void updateProduct_success() {
        ProductRequestDTO dto = request();
        dto.setName("Updated");
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.save(product)).thenReturn(product);

        ProductResponseDTO response = service.updateProduct(1L, dto);

        assertEquals("Updated", response.getName());
        verify(repository).save(product);
    }

    @Test
    void updateProduct_notFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateProduct(1L, request()));
    }

    @Test
    void deactivateProduct_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        service.deactivateProduct(1L);

        assertFalse(product.getIsActive());
        verify(repository).save(product);
    }

    @Test
    void deactivateProduct_notFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deactivateProduct(1L));
    }

    @Test
    void deleteProduct_success() {
        service.deleteProduct(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void getByBarcode_success() {
        when(repository.findByBarcodeAndIsActiveTrue("BAR123")).thenReturn(Optional.of(product));

        assertEquals(1L, service.getByBarcode("BAR123").getProductId());
    }

    @Test
    void getByBarcode_notFound() {
        when(repository.findByBarcodeAndIsActiveTrue("BAR123")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getByBarcode("BAR123"));
    }

    @Test
    void updateTotalStock_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        service.updateTotalStock(1L, 100);

        assertEquals(100, product.getTotalStock());
        verify(repository).save(product);
    }

    @Test
    void updateTotalStock_notFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateTotalStock(1L, 100));
    }

    @Test
    void getInactiveProducts_success() {
        product.setIsActive(false);
        when(repository.findByIsActive(false)).thenReturn(List.of(product));

        assertEquals(1, service.getInactiveProducts().size());
    }

    @Test
    void activateProduct_success() {
        product.setIsActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        service.activateProduct(1L);

        assertTrue(product.getIsActive());
        verify(repository).save(product);
    }

    @Test
    void activateProduct_notFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.activateProduct(1L));
    }

    private ProductRequestDTO request() {
        ProductRequestDTO dto = new ProductRequestDTO();
        dto.setSku("SKU123");
        dto.setName("Test Product");
        dto.setDescription("Description");
        dto.setCategory("Electronics");
        dto.setBrand("TestBrand");
        dto.setUnitOfMeasure("PCS");
        dto.setCostPrice(100.0);
        dto.setSellingPrice(120.0);
        dto.setReorderLevel(20);
        dto.setMaxStockLevel(500);
        dto.setLeadTimeDays(4);
        dto.setBarcode("BAR123");
        dto.setImageUrl("image.png");
        return dto;
    }
}
