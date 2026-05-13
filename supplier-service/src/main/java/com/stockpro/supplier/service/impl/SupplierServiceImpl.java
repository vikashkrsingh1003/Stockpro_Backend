package com.stockpro.supplier.service.impl;

import com.stockpro.supplier.entity.Supplier;
import com.stockpro.supplier.repository.SupplierRepository;
import com.stockpro.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepo;
    private final CacheManager cacheManager;

    // Create new supplier — isActive set to true by default
    @Override
    public Supplier createSupplier(Supplier supplier) {
        supplier.setIsActive(true);
        Supplier saved = supplierRepo.save(supplier);
        clearSupplierListCaches();
        return saved;
    }

    // Get single supplier — throws RuntimeException if not found
    // GlobalExceptionHandler will return this as 400 Bad Request
    @Override
    @Cacheable(value = "suppliersById", key = "#id", unless = "#result == null")
    public Supplier getById(Long id) {
        return supplierRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    // Return all suppliers (active + inactive)
    @Override
    @Cacheable(value = "suppliersAll", key = "'all'", unless = "#result == null")
    public List<Supplier> getAllSuppliers() {
        return supplierRepo.findAll();
    }

    // Search by name OR city OR country — uses custom @Query in repository
    // PDF §2.4: "Search suppliers by name, city, or country"
    @Override
    @Cacheable(value = "suppliersSearch", key = "#query == null ? 'all' : #query.trim().toLowerCase()", unless = "#result == null")
    public List<Supplier> searchSuppliers(String query) {
        return supplierRepo.searchByName(query); // method name matches PDF spec
    }

    // Update editable fields — does NOT change rating or isActive
    @Override
    public Supplier updateSupplier(Long id, Supplier updated) {
        Supplier s = getById(id);
        s.setName(updated.getName());
        s.setContactPerson(updated.getContactPerson());
        s.setEmail(updated.getEmail());
        s.setPhone(updated.getPhone());
        s.setAddress(updated.getAddress());
        s.setCity(updated.getCity());
        s.setCountry(updated.getCountry());
        s.setTaxId(updated.getTaxId());
        s.setPaymentTerms(updated.getPaymentTerms());
        s.setLeadTimeDays(updated.getLeadTimeDays());
        Supplier saved = supplierRepo.save(s);
        evictSupplierCaches(saved);
        return saved;
    }

    // Soft delete — sets isActive=false
    // PDF §4.5: "prevents new POs but preserves historical records"
    @Override
    public void deactivateSupplier(Long id) {
        Supplier s = getById(id);
        s.setIsActive(false);
        supplierRepo.save(s);
        evictSupplierCaches(s);
    }

    // Hard delete — Admin only (enforced by @PreAuthorize in controller)
    // 📌 NOTE: Using standard JpaRepository.deleteById() instead of
    // deleteBySupplierId() to avoid @Transactional issues with derived delete queries
    @Override
    public void deleteSupplier(Long id) {
        evict("suppliersById", id);
        clearSupplierListCaches();
        supplierRepo.deleteById(id);
    }

    // Filter suppliers by city — PDF §2.4 geo-filter
    @Override
    @Cacheable(value = "suppliersByCity", key = "#city == null ? 'blank' : #city.trim().toLowerCase()", unless = "#result == null")
    public List<Supplier> getByCity(String city) {
        return supplierRepo.findByCity(city);
    }

    // Filter suppliers by country — PDF §2.4 geo-filter
    @Override
    @Cacheable(value = "suppliersByCountry", key = "#country == null ? 'blank' : #country.trim().toLowerCase()", unless = "#result == null")
    public List<Supplier> getByCountry(String country) {
        return supplierRepo.findByCountry(country);
    }

    // Update supplier performance rating
    // 📌 NOTE: PDF §2.4 says "Rate supplier performance after goods receipt"
    // Called by the Purchase Order service after a GRN (Goods Received Note) is recorded.
    // Sets the rating directly — the Purchase Order service calculates the score before calling this.
    @Override
    public Supplier updateRating(Long id, double rating) {
        Supplier s = getById(id);
        s.setRating(rating);
        Supplier saved = supplierRepo.save(s);
        evictSupplierCaches(saved);
        return saved;
    }

    private void evictSupplierCaches(Supplier supplier) {
        if (supplier == null) {
            return;
        }
        evict("suppliersById", supplier.getSupplierId());
        clearSupplierListCaches();
    }

    private void clearSupplierListCaches() {
        clear("suppliersAll");
        clear("suppliersSearch");
        clear("suppliersByCity");
        clear("suppliersByCountry");
    }

    private void evict(String cacheName, Object key) {
        if (key == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.evict(key);
        } catch (RuntimeException ex) {
            System.err.println(" Redis cache evict skipped for " + cacheName + " key=" + key + ": " + ex.getMessage());
        }
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.clear();
        } catch (RuntimeException ex) {
            System.err.println(" Redis cache clear skipped for " + cacheName + ": " + ex.getMessage());
        }
    }
}
