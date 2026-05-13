package com.stockpro.purchase.service.impl;

import com.stockpro.purchase.client.PaymentClient;
import com.stockpro.purchase.client.ProductClient;
import com.stockpro.purchase.client.SupplierClient;
import com.stockpro.purchase.client.WarehouseClient;
import com.stockpro.purchase.dto.PaymentReportRequest;
import com.stockpro.purchase.dto.ProductDTO;
import com.stockpro.purchase.entity.POLineItem;
import com.stockpro.purchase.entity.POStatus;
import com.stockpro.purchase.entity.PurchaseOrder;
import com.stockpro.purchase.publisher.PurchaseOrderEventPublisher;
import com.stockpro.purchase.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock
    private PurchaseOrderRepository poRepository;

    @Mock
    private SupplierClient supplierClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private WarehouseClient warehouseClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private PurchaseOrderEventPublisher eventPublisher;

    @InjectMocks
    private PurchaseOrderServiceImpl service;

    private PurchaseOrder po;
    private POLineItem item;

    @BeforeEach
    void setup() {
        item = new POLineItem();
        item.setProductId(10L);
        item.setOrderedQuantity(2);
        item.setReceivedQuantity(0);
        item.setUnitCost(100.0);
        item.setTotalCost(200.0);

        po = new PurchaseOrder();
        po.setId(5L);
        po.setSupplierId(1L);
        po.setWarehouseId(2L);
        po.setReferenceNumber("PO-5");
        po.setStatus(POStatus.DRAFT);
        po.setTotalAmount(200.0);
        po.setItems(List.of(item));
    }

    @Test
    void createPO_successWithSku_resolvesProductAndCalculatesTotal() {
        item.setProductId(null);
        item.setSku("phone-001");
        ProductDTO product = new ProductDTO();
        product.setProductId(10L);
        when(supplierClient.isSupplierActive(1L)).thenReturn(true);
        when(productClient.getProductBySku("phone-001")).thenReturn(product);
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.createPO(po);

        assertEquals(POStatus.DRAFT, saved.getStatus());
        assertEquals(10L, item.getProductId());
        assertEquals(200.0, saved.getTotalAmount());
        assertSame(po, item.getPurchaseOrder());
    }

    @Test
    void createPO_successWithProductId_validatesProduct() {
        when(supplierClient.isSupplierActive(1L)).thenReturn(true);
        when(poRepository.save(po)).thenReturn(po);

        service.createPO(po);

        verify(productClient).getProductById(10L);
    }

    @Test
    void createPO_inactiveSupplier() {
        when(supplierClient.isSupplierActive(1L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.createPO(po));
    }

    @Test
    void createPO_missingProductIdentifier() {
        item.setProductId(null);
        item.setSku(null);
        when(supplierClient.isSupplierActive(1L)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.createPO(po));
    }

    @Test
    void createPO_withoutItems_setsTotalZero() {
        po.setItems(List.of());
        when(supplierClient.isSupplierActive(1L)).thenReturn(true);
        when(poRepository.save(po)).thenReturn(po);

        assertEquals(0.0, service.createPO(po).getTotalAmount());
    }

    @Test
    void submitPO_success() {
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.submitPO(5L);

        assertEquals(POStatus.PENDING, saved.getStatus());
        verify(eventPublisher).publish(eq(po), anyString());
    }

    @Test
    void submitPO_notFound() {
        when(poRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.submitPO(5L));
    }

    @Test
    void approvePO_success() {
        po.setStatus(POStatus.PENDING);
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.approvePO(5L);

        assertEquals(POStatus.APPROVED, saved.getStatus());
        verify(eventPublisher).publish(eq(po), anyString());
    }

    @Test
    void approvePO_rejectsNonPending() {
        po.setStatus(POStatus.DRAFT);
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));

        assertThrows(RuntimeException.class, () -> service.approvePO(5L));
    }

    @Test
    void rejectPO_success() {
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.rejectPO(5L, "Wrong price");

        assertEquals(POStatus.REJECTED, saved.getStatus());
        assertEquals("Wrong price", saved.getCancelReason());
    }

    @Test
    void receiveGoods_fullReceive_updatesWarehouseAndPaymentReport() {
        po.setStatus(POStatus.APPROVED);
        ProductDTO product = new ProductDTO();
        product.setProductId(10L);
        product.setName("Phone");
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);
        when(productClient.getProductById(10L)).thenReturn(product);

        PurchaseOrder saved = service.receiveGoods(5L, 10L, 2);

        assertEquals(POStatus.RECEIVED, saved.getStatus());
        assertEquals("PAYMENT_PENDING", saved.getPaymentStatus());
        assertNotNull(saved.getReceivedDate());
        verify(warehouseClient).addStock(2L, 10L, 2, "PO Receipt: PO-5");
        verify(paymentClient).generatePaymentReport(any());
        verify(eventPublisher).publish(eq(po), anyString());
    }

    @Test
    void receiveGoods_partialReceive_generatesPaymentReportForReceivedQuantity() {
        po.setStatus(POStatus.APPROVED);
        item.setOrderedQuantity(5);
        ProductDTO product = new ProductDTO();
        product.setProductId(10L);
        product.setName("Phone");
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);
        when(productClient.getProductById(10L)).thenReturn(product);

        PurchaseOrder saved = service.receiveGoods(5L, 10L, 2);

        assertEquals(POStatus.PARTIALLY_RECEIVED, saved.getStatus());
        assertEquals("PAYMENT_PENDING", saved.getPaymentStatus());

        ArgumentCaptor<PaymentReportRequest> reportCaptor = ArgumentCaptor.forClass(PaymentReportRequest.class);
        verify(paymentClient).generatePaymentReport(reportCaptor.capture());
        PaymentReportRequest report = reportCaptor.getValue();
        assertEquals(5L, report.getPoId());
        assertEquals(1L, report.getSupplierId());
        assertEquals(0, report.getAmount().compareTo(java.math.BigDecimal.valueOf(200.0)));
        assertEquals("Phone | Qty: 2 | Unit: 100.0 | Total: 200.0", report.getProductDetails());
        verify(eventPublisher, never()).publish(eq(po), anyString());
    }

    @Test
    void receiveGoods_notEligibleStatus() {
        po.setStatus(POStatus.DRAFT);
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));

        assertThrows(RuntimeException.class, () -> service.receiveGoods(5L, 10L, 1));
    }

    @Test
    void receiveGoods_itemNotFound() {
        po.setStatus(POStatus.APPROVED);
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));

        assertThrows(RuntimeException.class, () -> service.receiveGoods(5L, 99L, 1));
    }

    @Test
    void receiveGoods_zeroQuantity() {
        po.setStatus(POStatus.APPROVED);
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));

        assertThrows(RuntimeException.class, () -> service.receiveGoods(5L, 10L, 0));
    }

    @Test
    void receiveGoods_overReceive() {
        po.setStatus(POStatus.APPROVED);
        item.setReceivedQuantity(1);
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));

        assertThrows(RuntimeException.class, () -> service.receiveGoods(5L, 10L, 2));
    }

    @Test
    void cancelPO_success() {
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.cancelPO(5L, "Cancelled by officer");

        assertEquals(POStatus.CANCELLED, saved.getStatus());
        assertEquals("Cancelled by officer", saved.getCancelReason());
    }

    @Test
    void updatePaymentStatus_successPaidSetsPaidAt() {
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.updatePaymentStatus(5L, "PAID", "order_1", "pay_1");

        assertEquals("PAID", saved.getPaymentStatus());
        assertEquals("order_1", saved.getRazorpayOrderId());
        assertEquals("pay_1", saved.getRazorpayPaymentId());
        assertNotNull(saved.getPaidAt());
    }

    @Test
    void updatePaymentStatus_failedDoesNotSetPaidAt() {
        when(poRepository.findById(5L)).thenReturn(Optional.of(po));
        when(poRepository.save(po)).thenReturn(po);

        PurchaseOrder saved = service.updatePaymentStatus(5L, "PAYMENT_FAILED", "order_1", "pay_1");

        assertEquals("PAYMENT_FAILED", saved.getPaymentStatus());
        assertNull(saved.getPaidAt());
    }

    @Test
    void getPOs_bySupplier() {
        when(poRepository.findBySupplierId(1L)).thenReturn(List.of(po));

        assertEquals(1, service.getPOs(1L, null, null, null, null).size());
    }

    @Test
    void getPOs_byWarehouse() {
        when(poRepository.findByWarehouseId(2L)).thenReturn(List.of(po));

        assertEquals(1, service.getPOs(null, 2L, null, null, null).size());
    }

    @Test
    void getPOs_byStatus() {
        when(poRepository.findByStatus(POStatus.DRAFT)).thenReturn(List.of(po));

        assertEquals(1, service.getPOs(null, null, "DRAFT", null, null).size());
    }

    @Test
    void getPOs_byDateRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now();
        when(poRepository.findByOrderDateBetween(start, end)).thenReturn(List.of(po));

        assertEquals(1, service.getPOs(null, null, null, start, end).size());
    }

    @Test
    void getPOs_all() {
        when(poRepository.findAll()).thenReturn(List.of(po));

        assertEquals(1, service.getPOs(null, null, null, null, null).size());
    }
}
