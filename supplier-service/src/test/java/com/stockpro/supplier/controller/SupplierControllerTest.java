package com.stockpro.supplier.controller;

import com.stockpro.supplier.entity.Supplier;
import com.stockpro.supplier.service.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierControllerTest {

    @Mock
    private SupplierService supplierService;

    private SupplierController controller;
    private Supplier supplier;

    @BeforeEach
    void setup() {
        controller = new SupplierController(supplierService);
        supplier = new Supplier();
        supplier.setSupplierId(1L);
        supplier.setName("Supplier");
        supplier.setCity("Mumbai");
        supplier.setCountry("India");
        supplier.setIsActive(true);
    }

    @Test
    void readEndpointsDelegateToService() {
        when(supplierService.getAllSuppliers()).thenReturn(List.of(supplier));
        when(supplierService.getById(1L)).thenReturn(supplier);
        when(supplierService.searchSuppliers("sup")).thenReturn(List.of(supplier));
        when(supplierService.getByCity("Mumbai")).thenReturn(List.of(supplier));
        when(supplierService.getByCountry("India")).thenReturn(List.of(supplier));

        assertEquals(1, controller.getAll().size());
        assertSame(supplier, controller.getById(1L));
        assertTrue(controller.isSupplierActive(1L));
        assertEquals(1, controller.search("sup").size());
        assertEquals(1, controller.getByCity("Mumbai").size());
        assertEquals(1, controller.getByCountry("India").size());
    }

    @Test
    void isSupplierActiveFalseWhenInactiveOrMissing() {
        supplier.setIsActive(false);
        when(supplierService.getById(1L)).thenReturn(supplier);
        when(supplierService.getById(2L)).thenReturn(null);

        assertFalse(controller.isSupplierActive(1L));
        assertFalse(controller.isSupplierActive(2L));
    }

    @Test
    void writeEndpointsDelegateToService() {
        when(supplierService.createSupplier(supplier)).thenReturn(supplier);
        when(supplierService.updateSupplier(1L, supplier)).thenReturn(supplier);
        when(supplierService.updateRating(1L, 4.5)).thenReturn(supplier);

        assertSame(supplier, controller.create(supplier));
        assertSame(supplier, controller.update(1L, supplier));
        assertSame(supplier, controller.updateRating(1L, 4.5));
        assertEquals("Supplier deactivated ", controller.deactivate(1L));
        assertEquals("Supplier deleted ", controller.delete(1L));

        verify(supplierService).deactivateSupplier(1L);
        verify(supplierService).deleteSupplier(1L);
    }
}
