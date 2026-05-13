package com.stockpro.purchase.controller;

import com.stockpro.purchase.dto.PaymentStatusUpdateRequest;
import com.stockpro.purchase.entity.POStatus;
import com.stockpro.purchase.entity.PurchaseOrder;
import com.stockpro.purchase.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderControllerTest {

    @Mock
    private PurchaseOrderService poService;

    private PurchaseOrderController controller;
    private PurchaseOrder po;

    @BeforeEach
    void setup() {
        controller = new PurchaseOrderController(poService);
        ReflectionTestUtils.setField(controller, "internalServiceToken", "internal");
        po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(POStatus.APPROVED);
        po.setExpectedDeliveryDate(LocalDateTime.now().minusDays(1));
    }

    @Test
    void lifecycleEndpointsDelegateToService() {
        when(poService.createPO(po)).thenReturn(po);
        when(poService.submitPO(1L)).thenReturn(po);
        when(poService.approvePO(1L)).thenReturn(po);
        when(poService.rejectPO(1L, "bad")).thenReturn(po);
        when(poService.receiveGoods(1L, 2L, 3)).thenReturn(po);
        when(poService.cancelPO(1L, "cancel")).thenReturn(po);

        assertSame(po, controller.createPO(po).getBody());
        assertSame(po, controller.submitPO(1L).getBody());
        assertSame(po, controller.approvePO(1L).getBody());
        assertSame(po, controller.rejectPO(1L, "bad").getBody());
        assertSame(po, controller.receiveGoods(1L, 2L, 3).getBody());
        assertSame(po, controller.cancelPO(1L, "cancel").getBody());
    }

    @Test
    void getPOsDelegatesFiltersToService() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now();
        when(poService.getPOs(1L, 2L, "APPROVED", start, end)).thenReturn(List.of(po));

        assertEquals(1, controller.getPOs(1L, 2L, "APPROVED", start, end).getBody().size());
    }

    @Test
    void updatePaymentStatusValidatesInternalToken() {
        PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest();
        request.setPaymentStatus("PAID");
        request.setRazorpayOrderId("order_1");
        request.setRazorpayPaymentId("pay_1");
        when(poService.updatePaymentStatus(1L, "PAID", "order_1", "pay_1")).thenReturn(po);

        assertSame(po, controller.updatePaymentStatus(1L, "internal", request).getBody());
        assertThrows(ResponseStatusException.class, () -> controller.updatePaymentStatus(1L, "wrong", request));
    }

    @Test
    void getOverduePOsFiltersOnlyPastApprovedOrders() {
        PurchaseOrder future = new PurchaseOrder();
        future.setId(2L);
        future.setExpectedDeliveryDate(LocalDateTime.now().plusDays(1));
        when(poService.getPOs(null, null, "APPROVED", null, null)).thenReturn(List.of(po, future));

        var overdue = controller.getOverduePOs().getBody();

        assertEquals(1, overdue.size());
        assertEquals(1L, overdue.get(0).getId());
    }
}
