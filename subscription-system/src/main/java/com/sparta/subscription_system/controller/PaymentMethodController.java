package com.sparta.subscription_system.controller;

import com.sparta.subscription_system.dto.CreatePaymentMethodRequest;
import com.sparta.subscription_system.entity.PaymentMethod;
import com.sparta.subscription_system.service.PaymentMethodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment-methods")
@CrossOrigin(origins = "*")
public class PaymentMethodController {

    @Autowired
    private PaymentMethodService paymentMethodService;

    @PostMapping("/user/{userId}")
    public ResponseEntity<?> createPaymentMethod(@PathVariable Long userId,
                                                             @RequestBody CreatePaymentMethodRequest request) {
        try {
            // 필수 필드 검증
            if (request.getCustomerUid() == null || request.getCustomerUid().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "customerUid는 필수입니다."));
            }

            PaymentMethod paymentMethod = paymentMethodService.createPaymentMethod(
                    userId,
                    request.getCustomerUid(),
                    request.getBillingKey(),
                    request.getCardBrand(),
                    request.getLast4(),
                    request.getIsDefault()
            );
            return ResponseEntity.ok(paymentMethod);
        } catch (RuntimeException e) {
            // 에러 메시지를 포함하여 반환
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // 예상치 못한 에러
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "결제 수단 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPaymentMethodsByUserId(@PathVariable Long userId) {
        try {
            List<PaymentMethod> paymentMethods = paymentMethodService.getPaymentMethodsByUserId(userId);
            return ResponseEntity.ok(paymentMethods);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "결제 수단 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/{methodId}")
    public ResponseEntity<PaymentMethod> getPaymentMethodById(@PathVariable Long methodId) {
        Optional<PaymentMethod> paymentMethod = paymentMethodService.getPaymentMethodById(methodId);
        return paymentMethod.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/user/{userId}/default/{methodId}")
    public ResponseEntity<PaymentMethod> setDefaultPaymentMethod(@PathVariable Long userId,
                                                                 @PathVariable Long methodId) {
        try {
            PaymentMethod paymentMethod = paymentMethodService.setDefaultPaymentMethod(userId, methodId);
            return ResponseEntity.ok(paymentMethod);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/user/{userId}/{methodId}")
    public ResponseEntity<Map<String, String>> deletePaymentMethod(@PathVariable Long userId,
                                                                   @PathVariable Long methodId) {
        try {
            paymentMethodService.deletePaymentMethod(userId, methodId);
            return ResponseEntity.ok(Map.of("message", "Payment method deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 서버 API를 통한 빌링키 발급
     * 주의: 실제로는 프론트엔드에서 결제 창을 열어야 하므로, 
     * 이 엔드포인트는 결제 완료 후 빌링키를 등록하는 용도로 사용됩니다.
     */
    @PostMapping("/user/{userId}/issue-billing-key")
    public Mono<ResponseEntity<Object>> issueBillingKey(@PathVariable Long userId,
                                                          @RequestBody Map<String, Object> request) {
        try {
            String customerUid = (String) request.get("customerUid");
            Integer amount = (Integer) request.get("amount");
            String orderName = (String) request.get("orderName");

            if (customerUid == null || customerUid.trim().isEmpty()) {
                return Mono.just(ResponseEntity.<Object>badRequest()
                        .body(Map.of("error", "customerUid는 필수입니다.")));
            }

            if (amount == null || amount < 1000) {
                return Mono.just(ResponseEntity.<Object>badRequest()
                        .body(Map.of("error", "결제 금액은 최소 1,000원 이상이어야 합니다.")));
            }

            if (orderName == null || orderName.trim().isEmpty()) {
                orderName = "빌링키 발급";
            }

            return paymentMethodService.issueBillingKeyViaServer(customerUid, amount, orderName)
                    .map(result -> ResponseEntity.<Object>ok(result))
                    .onErrorResume(error -> Mono.just(ResponseEntity.<Object>badRequest()
                            .body(Map.of("error", error.getMessage()))));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.<Object>badRequest()
                    .body(Map.of("error", "빌링키 발급 중 오류가 발생했습니다: " + e.getMessage())));
        }
    }

    /**
     * 등록된 결제 수단으로 결제 실행
     */
    @PostMapping("/user/{userId}/execute-payment")
    public Mono<ResponseEntity<Object>> executePayment(@PathVariable Long userId,
                                                       @RequestBody Map<String, Object> request) {
        try {
            Long methodId = Long.parseLong(request.get("methodId").toString());
            Integer amount = Integer.parseInt(request.get("amount").toString());
            String orderName = (String) request.getOrDefault("orderName", "결제");

            if (amount == null || amount < 1000) {
                return Mono.just(ResponseEntity.<Object>badRequest()
                        .body(Map.of("error", "결제 금액은 최소 1,000원 이상이어야 합니다.")));
            }

            // 결제 수단이 해당 사용자의 것인지 확인
            Optional<PaymentMethod> paymentMethod = paymentMethodService.getPaymentMethodById(methodId);
            if (paymentMethod.isEmpty() || !paymentMethod.get().getUser().getUserId().equals(userId)) {
                return Mono.just(ResponseEntity.<Object>badRequest()
                        .body(Map.of("error", "결제 수단을 찾을 수 없거나 사용자의 결제 수단이 아닙니다.")));
            }

            return paymentMethodService.executePayment(methodId, amount, orderName)
                    .map(result -> ResponseEntity.<Object>ok(result))
                    .onErrorResume(error -> Mono.just(ResponseEntity.<Object>badRequest()
                            .body(Map.of("error", error.getMessage()))));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.<Object>badRequest()
                    .body(Map.of("error", "결제 실행 중 오류가 발생했습니다: " + e.getMessage())));
        }
    }
}

