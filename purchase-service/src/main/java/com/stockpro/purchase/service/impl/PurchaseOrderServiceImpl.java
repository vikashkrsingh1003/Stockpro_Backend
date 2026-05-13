package com.stockpro.purchase.service.impl;

import com.stockpro.purchase.client.PaymentClient;
import com.stockpro.purchase.client.ProductClient;
import com.stockpro.purchase.client.SupplierClient;
import com.stockpro.purchase.client.WarehouseClient;
import com.stockpro.purchase.config.RabbitMQConfig;
import com.stockpro.purchase.dto.PaymentReportRequest;
import com.stockpro.purchase.entity.*;
import com.stockpro.purchase.publisher.PurchaseOrderEventPublisher;
import com.stockpro.purchase.repository.PurchaseOrderRepository;
import com.stockpro.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final SupplierClient supplierClient;
    private final ProductClient productClient;
    private final WarehouseClient warehouseClient;
    private final PaymentClient paymentClient;
    private final PurchaseOrderEventPublisher eventPublisher; // RabbitMQ Publisher (non-critical)

    //  CREATE PO
    @Override
    public PurchaseOrder createPO(PurchaseOrder po) {

        //  Supplier validation
        if (!supplierClient.isSupplierActive(po.getSupplierId())) {
            throw new RuntimeException("Supplier is inactive");
        }

        //  Set defaults
        po.setStatus(POStatus.DRAFT);
        po.setOrderDate(LocalDateTime.now());
        po.setReferenceNumber("PO-" + System.currentTimeMillis());

        //  Product validation and Cost Calculation
        if (po.getItems() != null && !po.getItems().isEmpty()) {
            po.getItems().forEach(item -> {
                if (item.getSku() != null && !item.getSku().isEmpty()) {
                    // Fetch product by SKU
                    com.stockpro.purchase.dto.ProductDTO productDTO = productClient.getProductBySku(item.getSku());
                    item.setProductId(productDTO.getProductId());
                } else if (item.getProductId() != null) {
                    // Validate product by ID
                    productClient.getProductById(item.getProductId());
                } else {
                    throw new RuntimeException("Line item must have either productId or sku");
                }
            });

            //  Calculate total cost
            po.getItems().forEach(item -> {
                int orderedQuantity = item.getOrderedQuantity() == null ? 0 : item.getOrderedQuantity();
                double unitCost = item.getUnitCost() == null ? 0.0 : item.getUnitCost();
                item.setOrderedQuantity(orderedQuantity);
                item.setUnitCost(unitCost);
                item.setReceivedQuantity(item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity());
                item.setTotalCost(orderedQuantity * unitCost);
                item.setPurchaseOrder(po);
            });

            po.calculateTotalAmount();
        } else {
            po.setTotalAmount(0.0);
        }

        return poRepository.save(po);
    }

    //  SUBMIT PO → Trigger alert later
    @Override
    public PurchaseOrder submitPO(Long id) {
        PurchaseOrder po = getPO(id);

        po.setStatus(POStatus.PENDING);

        PurchaseOrder saved = poRepository.save(po);

        // [RabbitMQ] Notify alert-service and analytics-service that a PO needs approval
        eventPublisher.publish(saved, RabbitMQConfig.PO_SUBMITTED_ROUTING_KEY);

        return saved;
    }

    //  APPROVE PO
    @Override
    public PurchaseOrder approvePO(Long id) {
        PurchaseOrder po = getPO(id);

        if (po.getStatus() != POStatus.PENDING) {
            throw new RuntimeException("Only pending PO can be approved");
        }

        po.setStatus(POStatus.APPROVED);
        PurchaseOrder saved = poRepository.save(po);

        // [RabbitMQ] Notify analytics-service that a PO was approved
        eventPublisher.publish(saved, RabbitMQConfig.PO_APPROVED_ROUTING_KEY);

        return saved;
    }

    //  REJECT PO
    @Override
    public PurchaseOrder rejectPO(Long id, String reason) {
        PurchaseOrder po = getPO(id);

        po.setStatus(POStatus.REJECTED);
        po.setCancelReason(reason);

        return poRepository.save(po);
    }

    //  RECEIVE GOODS (MOST IMPORTANT)
    @Override
    public PurchaseOrder receiveGoods(Long id, Long productId, Integer receivedQty) {

        PurchaseOrder po = getPO(id);

        if (po.getStatus() != POStatus.APPROVED &&
            po.getStatus() != POStatus.PARTIALLY_RECEIVED &&
            po.getStatus() != POStatus.OVERDUE) {
            throw new RuntimeException("PO not eligible for receiving");
        }

        //  Find item
        POLineItem item = po.getItems().stream()
                .filter(i -> productId.equals(i.getProductId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Item not found"));

        //  Prevent over-receive
        int currentReceived = item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
        int orderedQuantity = item.getOrderedQuantity() == null ? 0 : item.getOrderedQuantity();
        int qtyToReceive = receivedQty == null ? 0 : receivedQty;

        if (qtyToReceive <= 0) {
            throw new RuntimeException("Received quantity must be greater than zero");
        }

        int newQty = currentReceived + qtyToReceive;
        if (newQty > orderedQuantity) {
            throw new RuntimeException("Received quantity exceeds ordered");
        }

        item.setReceivedQuantity(newQty);

        //  Update warehouse stock
        warehouseClient.addStock(
                po.getWarehouseId(),
                productId,
                qtyToReceive,
                "PO Receipt: " + po.getReferenceNumber() // 🔥 Linked Reason
        );

        //  Check overall PO status
        boolean allReceived = po.getItems().stream()
                .allMatch(i -> (i.getReceivedQuantity() == null ? 0 : i.getReceivedQuantity())
                        >= (i.getOrderedQuantity() == null ? 0 : i.getOrderedQuantity()));

        if (allReceived) {
            po.setStatus(POStatus.RECEIVED);
            po.setReceivedDate(LocalDateTime.now());
        } else {
            po.setStatus(POStatus.PARTIALLY_RECEIVED);
        }
        po.setPaymentStatus("PAYMENT_PENDING");

        PurchaseOrder saved = poRepository.save(po);

        paymentClient.generatePaymentReport(new PaymentReportRequest(
                saved.getId(),
                saved.getSupplierId(),
                receiptAmount(item, qtyToReceive),
                "INR",
                buildReceiptPaymentDetails(item, qtyToReceive)
        ));

        // [RabbitMQ] If fully received, notify analytics-service with total spend
        if (saved.getStatus() == POStatus.RECEIVED) {
            eventPublisher.publish(saved, RabbitMQConfig.PO_RECEIVED_ROUTING_KEY);
        }

        return saved;
    }

    //  CANCEL PO
    @Override
    public PurchaseOrder cancelPO(Long id, String reason) {
        PurchaseOrder po = getPO(id);

        po.setStatus(POStatus.CANCELLED);
        po.setCancelReason(reason);

        return poRepository.save(po);
    }

    @Override
    public PurchaseOrder updatePaymentStatus(Long id, String paymentStatus, String razorpayOrderId, String razorpayPaymentId) {
        PurchaseOrder po = getPO(id);

        po.setPaymentStatus(paymentStatus);
        po.setRazorpayOrderId(razorpayOrderId);
        po.setRazorpayPaymentId(razorpayPaymentId);
        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            po.setPaidAt(LocalDateTime.now());
        }

        return poRepository.save(po);
    }

    //  FILTER API
    @Override
    public List<PurchaseOrder> getPOs(Long supplierId,
                                     Long warehouseId,
                                     String status,
                                     LocalDateTime startDate,
                                     LocalDateTime endDate) {

        if (supplierId != null) {
            return poRepository.findBySupplierId(supplierId);
        }

        if (warehouseId != null) {
            return poRepository.findByWarehouseId(warehouseId);
        }

        if (status != null) {
            return poRepository.findByStatus(POStatus.valueOf(status));
        }

        if (startDate != null && endDate != null) {
            return poRepository.findByOrderDateBetween(startDate, endDate);
        }

        return poRepository.findAll();
    }

    //  Helper
    private PurchaseOrder getPO(Long id) {
        return poRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("PO not found"));
    }

    private BigDecimal receiptAmount(POLineItem item, int receivedQty) {
        double unitCost = item.getUnitCost() == null ? 0.0 : item.getUnitCost();
        return BigDecimal.valueOf(unitCost).multiply(BigDecimal.valueOf(receivedQty));
    }

    private String buildReceiptPaymentDetails(POLineItem item, int receivedQty) {
        double unitCost = item.getUnitCost() == null ? 0.0 : item.getUnitCost();
        double total = unitCost * receivedQty;
        return productLabel(item.getProductId())
                + " | Qty: " + receivedQty
                + " | Unit: " + unitCost
                + " | Total: " + total;
    }

    private String productLabel(Long productId) {
        try {
            com.stockpro.purchase.dto.ProductDTO product = productClient.getProductById(productId);
            if (product.getName() != null && !product.getName().isBlank()) {
                return product.getName();
            }
            if (product.getSku() != null && !product.getSku().isBlank()) {
                return product.getSku();
            }
        } catch (Exception ignored) {
        }
        return "Product #" + productId;
    }
}
