package com.stockpro.supplier.service.impl;

import com.stockpro.supplier.entity.Supplier;
import com.stockpro.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cache.CacheManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepo;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private SupplierServiceImpl service;

    private Supplier supplier;

    @BeforeEach
    void setup() {
        supplier = new Supplier();
        supplier.setSupplierId(1L);
        supplier.setName("Acme");
        supplier.setContactPerson("Amit");
        supplier.setEmail("acme@mail.com");
        supplier.setPhone("9999999999");
        supplier.setAddress("Street");
        supplier.setCity("Mumbai");
        supplier.setCountry("India");
        supplier.setTaxId("GST123");
        supplier.setPaymentTerms("NET30");
        supplier.setLeadTimeDays(5);
        supplier.setRating(4.0);
        supplier.setIsActive(true);
    }

    @Test
    void createSupplier_success() {
        supplier.setIsActive(false);
        when(supplierRepo.save(supplier)).thenReturn(supplier);

        Supplier saved = service.createSupplier(supplier);

        assertTrue(saved.getIsActive());
        verify(supplierRepo).save(supplier);
    }

    @Test
    void getById_success() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));

        assertEquals("Acme", service.getById(1L).getName());
    }

    @Test
    void getById_notFound() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getById(1L));
    }

    @Test
    void getAllSuppliers_success() {
        when(supplierRepo.findAll()).thenReturn(List.of(supplier));

        assertEquals(1, service.getAllSuppliers().size());
    }

    @Test
    void searchSuppliers_success() {
        when(supplierRepo.searchByName("mum")).thenReturn(List.of(supplier));

        assertEquals(1, service.searchSuppliers("mum").size());
    }

    @Test
    void updateSupplier_success() {
        Supplier updated = new Supplier();
        updated.setName("Updated");
        updated.setContactPerson("Raj");
        updated.setEmail("new@mail.com");
        updated.setPhone("111");
        updated.setAddress("New Street");
        updated.setCity("Pune");
        updated.setCountry("India");
        updated.setTaxId("GST999");
        updated.setPaymentTerms("NET15");
        updated.setLeadTimeDays(3);
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepo.save(supplier)).thenReturn(supplier);

        Supplier saved = service.updateSupplier(1L, updated);

        assertEquals("Updated", saved.getName());
        assertEquals("Pune", saved.getCity());
        assertEquals(3, saved.getLeadTimeDays());
        verify(supplierRepo).save(supplier);
    }

    @Test
    void updateSupplier_notFound() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateSupplier(1L, supplier));
    }

    @Test
    void deactivateSupplier_success() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));

        service.deactivateSupplier(1L);

        assertFalse(supplier.getIsActive());
        verify(supplierRepo).save(supplier);
    }

    @Test
    void deactivateSupplier_notFound() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.deactivateSupplier(1L));
    }

    @Test
    void deleteSupplier_success() {
        service.deleteSupplier(1L);

        verify(supplierRepo).deleteById(1L);
    }

    @Test
    void getByCity_success() {
        when(supplierRepo.findByCity("Mumbai")).thenReturn(List.of(supplier));

        assertEquals(1, service.getByCity("Mumbai").size());
    }

    @Test
    void getByCountry_success() {
        when(supplierRepo.findByCountry("India")).thenReturn(List.of(supplier));

        assertEquals(1, service.getByCountry("India").size());
    }

    @Test
    void updateRating_success() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepo.save(supplier)).thenReturn(supplier);

        Supplier saved = service.updateRating(1L, 4.8);

        assertEquals(4.8, saved.getRating());
        verify(supplierRepo).save(supplier);
    }

    @Test
    void updateRating_notFound() {
        when(supplierRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateRating(1L, 4.8));
    }
}
