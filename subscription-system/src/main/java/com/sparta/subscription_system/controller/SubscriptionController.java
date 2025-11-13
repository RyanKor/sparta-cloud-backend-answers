package com.sparta.subscription_system.controller;

import com.sparta.subscription_system.dto.CreateSubscriptionRequest;
import com.sparta.subscription_system.dto.SubscriptionResponse;
import com.sparta.subscription_system.entity.Subscription;
import com.sparta.subscription_system.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subscriptions")
@CrossOrigin(origins = "*")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @PostMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> createSubscription(@PathVariable Long userId,
                                                                  @RequestBody CreateSubscriptionRequest request) {
        try {
            Subscription subscription = subscriptionService.createSubscription(
                    userId,
                    request.getPlanId(),
                    request.getPaymentMethodId()
            );

            SubscriptionResponse response = convertToResponse(subscription);
            Map<String, Object> result = new HashMap<>();
            result.put("subscription", response);
            result.put("message", "Subscription created successfully");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptionsByUserId(@PathVariable Long userId) {
        List<Subscription> subscriptions = subscriptionService.getSubscriptionsByUserId(userId);
        List<SubscriptionResponse> responses = subscriptions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<SubscriptionResponse> getSubscriptionById(@PathVariable Long subscriptionId) {
        Optional<Subscription> subscription = subscriptionService.getSubscriptionById(subscriptionId);
        return subscription.map(sub -> ResponseEntity.ok(convertToResponse(sub)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/user/{userId}/cancel/{subscriptionId}")
    public ResponseEntity<Map<String, Object>> cancelSubscription(@PathVariable Long userId,
                                                                  @PathVariable Long subscriptionId) {
        try {
            Subscription subscription = subscriptionService.cancelSubscription(userId, subscriptionId);
            SubscriptionResponse response = convertToResponse(subscription);
            Map<String, Object> result = new HashMap<>();
            result.put("subscription", response);
            result.put("message", "Subscription canceled successfully");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{subscriptionId}/invoices")
    public ResponseEntity<Map<String, Object>> createInvoice(@PathVariable Long subscriptionId) {
        try {
            var invoice = subscriptionService.createInvoice(subscriptionId);
            Map<String, Object> result = new HashMap<>();
            result.put("invoiceId", invoice.getInvoiceId());
            result.put("amount", invoice.getAmount());
            result.put("dueDate", invoice.getDueDate());
            result.put("message", "Invoice created successfully");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/invoices/{invoiceId}/process")
    public Mono<ResponseEntity<Map<String, Object>>> processInvoicePayment(@PathVariable Long invoiceId) {
        return subscriptionService.processInvoicePayment(invoiceId)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("message", "Invoice payment processed successfully");
                        result.put("invoiceId", invoiceId);
                        return ResponseEntity.ok(result);
                    } else {
                        result.put("error", "Invoice payment processing failed");
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    private SubscriptionResponse convertToResponse(Subscription subscription) {
        SubscriptionResponse response = new SubscriptionResponse();
        response.setSubscriptionId(subscription.getSubscriptionId());
        response.setUserId(subscription.getUser().getUserId());
        response.setPlanId(subscription.getPlan().getPlanId());
        response.setPlanName(subscription.getPlan().getName());
        response.setPaymentMethodId(subscription.getPaymentMethod() != null ?
                subscription.getPaymentMethod().getMethodId() : null);
        response.setStatus(subscription.getStatus());
        response.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
        response.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd());
        response.setTrialEnd(subscription.getTrialEnd());
        response.setStartedAt(subscription.getStartedAt());
        response.setCanceledAt(subscription.getCanceledAt());
        response.setEndedAt(subscription.getEndedAt());
        return response;
    }
}


