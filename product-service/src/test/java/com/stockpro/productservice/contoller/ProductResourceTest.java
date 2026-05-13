package com.stockpro.productservice.contoller;

import com.stockpro.productservice.dto.ProductRequestDTO;
import com.stockpro.productservice.dto.ProductResponseDTO;
import com.stockpro.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductResourceTest {

    @Mock
    private ProductService service;

    private ProductResource controller;

    @BeforeEach
    void setup() {
        controller = new ProductResource(service);
    }

    @Test
    void readEndpointsDelegateToService() {
        ProductResponseDTO product = product();
        when(service.getById(1L)).thenReturn(product);
        when(service.getBySku("SKU-1")).thenReturn(product);
        when(service.getByBarcode("BAR-1")).thenReturn(product);
        when(service.getAllProducts(any())).thenReturn(new PageImpl<>(List.of(product)));
        when(service.getAllActiveProducts()).thenReturn(List.of(product));
        when(service.getByCategory("ELECTRONIC")).thenReturn(List.of(product));
        when(service.getByBrand("Brand")).thenReturn(List.of(product));
        when(service.searchProducts("phone")).thenReturn(List.of(product));
        when(service.filterProducts("phone", "ELECTRONIC")).thenReturn(List.of(product));
        when(service.getInactiveProducts()).thenReturn(List.of(product));

        assertSame(product, controller.getById(1L));
        assertSame(product, controller.getBySku("SKU-1"));
        assertSame(product, controller.getByBarcode("BAR-1"));
        assertEquals(1, controller.getAll(PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, controller.getAllInternal().size());
        assertEquals(1, controller.getByCategory("ELECTRONIC").size());
        assertEquals(1, controller.getByBrand("Brand").size());
        assertEquals(1, controller.search("phone").size());
        assertEquals(1, controller.filter("phone", "ELECTRONIC").size());
        assertEquals(1, controller.getInactiveProducts().size());
    }

    @Test
    void writeEndpointsDelegateToService() {
        ProductRequestDTO request = new ProductRequestDTO();
        ProductResponseDTO product = product();
        when(service.createProduct(request)).thenReturn(product);
        when(service.updateProduct(1L, request)).thenReturn(product);

        assertSame(product, controller.create(request));
        assertSame(product, controller.updateProduct(1L, request));
        assertEquals("Product deactivated successfully", controller.deactivateProduct(1L));
        assertEquals("Product deleted successfully", controller.deleteProduct(1L));
        assertEquals("Product reactivated successfully", controller.activateProduct(1L));

        controller.validateProduct(1L);
        controller.validateProductBySku("SKU-1");
        controller.updateTotalStock(1L, 10);

        verify(service).deactivateProduct(1L);
        verify(service).deleteProduct(1L);
        verify(service).activateProduct(1L);
        verify(service).getById(1L);
        verify(service).getBySku("SKU-1");
        verify(service).updateTotalStock(1L, 10);
    }

    private ProductResponseDTO product() {
        return ProductResponseDTO.builder()
                .productId(1L)
                .sku("SKU-1")
                .name("Phone")
                .build();
    }
}
