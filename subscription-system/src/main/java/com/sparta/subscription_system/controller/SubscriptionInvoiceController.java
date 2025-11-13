package com.sparta.subscription_system.controller;

import com.sparta.subscription_system.dto.InvoiceResponse;
import com.sparta.subscription_system.entity.SubscriptionInvoice;
import com.sparta.subscription_system.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin(origins = "*")
public class SubscriptionInvoiceController {

    @Autowired
    private SubscriptionService subscriptionService;

    @GetMapping("/subscription/{subscriptionId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesBySubscriptionId(@PathVariable Long subscriptionId) {
        List<SubscriptionInvoice> invoices = subscriptionService.getInvoicesBySubscriptionId(subscriptionId);
        List<InvoiceResponse> responses = invoices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByUserId(@PathVariable Long userId) {
        List<SubscriptionInvoice> invoices = subscriptionService.getInvoicesByUserId(userId);
        List<InvoiceResponse> responses = invoices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{invoiceId}/refund")
    public Mono<ResponseEntity<Map<String, Object>>> refundInvoice(@PathVariable Long invoiceId,
                                                                    @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String reason = (String) request.getOrDefault("reason", "Customer request");

        return subscriptionService.refundInvoice(invoiceId, amount, reason)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("message", "Invoice refunded successfully");
                        result.put("invoiceId", invoiceId);
                        return ResponseEntity.ok(result);
                    } else {
                        result.put("error", "Invoice refund failed");
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * 등록된 결제 수단으로 결제한 내역을 청구서로 기록
     */
    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> createPaymentInvoice(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.parseLong(request.get("userId").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String impUid = (String) request.get("impUid");
            String merchantUid = (String) request.get("merchantUid");
            String orderName = (String) request.getOrDefault("orderName", "결제");

            SubscriptionInvoice invoice = subscriptionService.createPaymentInvoice(
                    userId, amount, impUid, merchantUid, orderName);

            Map<String, Object> result = new HashMap<>();
            if (invoice != null) {
                result.put("message", "Payment invoice created successfully");
                result.put("invoiceId", invoice.getInvoiceId());
                result.put("invoice", convertToResponse(invoice));
                return ResponseEntity.ok(result);
            } else {
                result.put("message", "No active subscription found. Payment completed but invoice will be created when subscription is created.");
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 빌링키 발급 시 결제 내역을 청구서로 기록
     */
    @PostMapping("/billing-key-payment")
    public ResponseEntity<Map<String, Object>> createBillingKeyPaymentInvoice(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.parseLong(request.get("userId").toString());
            Long planId = request.get("planId") != null ? Long.parseLong(request.get("planId").toString()) : null;
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String impUid = (String) request.get("impUid");
            String paymentId = (String) request.get("paymentId");

            SubscriptionInvoice invoice = subscriptionService.createBillingKeyPaymentInvoice(
                    userId, planId, amount, impUid, paymentId);

            Map<String, Object> result = new HashMap<>();
            if (invoice != null) {
                result.put("message", "Billing key payment invoice created successfully");
                result.put("invoiceId", invoice.getInvoiceId());
                result.put("invoice", convertToResponse(invoice));
                return ResponseEntity.ok(result);
            } else {
                result.put("message", "No active subscription found. Invoice will be created when subscription is created.");
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private InvoiceResponse convertToResponse(SubscriptionInvoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setInvoiceId(invoice.getInvoiceId());
        response.setSubscriptionId(invoice.getSubscription().getSubscriptionId());
        response.setAmount(invoice.getAmount());
        response.setStatus(invoice.getStatus());
        response.setDueDate(invoice.getDueDate());
        response.setPaidAt(invoice.getPaidAt());
        response.setImpUid(invoice.getImpUid());
        response.setAttemptCount(invoice.getAttemptCount());
        response.setErrorMessage(invoice.getErrorMessage());
        response.setCreatedAt(invoice.getCreatedAt());
        return response;
    }
}


