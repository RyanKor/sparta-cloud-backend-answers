package com.sparta.subscription_system.service;

import com.sparta.subscription_system.client.PortOneClient;
import com.sparta.subscription_system.entity.PaymentMethod;
import com.sparta.subscription_system.entity.User;
import com.sparta.subscription_system.repository.PaymentMethodRepository;
import com.sparta.subscription_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;
    private final PortOneClient portOneClient;

    @Autowired
    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository,
                                UserRepository userRepository,
                                PortOneClient portOneClient) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.userRepository = userRepository;
        this.portOneClient = portOneClient;
    }

    @Transactional
    public PaymentMethod createPaymentMethod(Long userId, String customerUid, String billingKey,
                                            String cardBrand, String last4, Boolean isDefault) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // customer_uid 중복 체크
        Optional<PaymentMethod> existingMethod = paymentMethodRepository.findByCustomerUid(customerUid);
        if (existingMethod.isPresent()) {
            throw new RuntimeException("이미 등록된 customer_uid입니다: " + customerUid);
        }

        // 기본 결제 수단으로 설정하는 경우, 기존 기본 결제 수단 해제
        if (isDefault != null && isDefault) {
            Optional<PaymentMethod> existingDefault = paymentMethodRepository
                    .findByUserUserIdAndIsDefaultTrue(userId);
            if (existingDefault.isPresent()) {
                PaymentMethod defaultMethod = existingDefault.get();
                defaultMethod.setIsDefault(false);
                paymentMethodRepository.save(defaultMethod);
            }
        }

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setUser(user);
        paymentMethod.setCustomerUid(customerUid);
        paymentMethod.setBillingKey(billingKey); // 빌링키 저장
        paymentMethod.setCardBrand(cardBrand);
        paymentMethod.setLast4(last4);
        paymentMethod.setIsDefault(isDefault != null ? isDefault : false);

        try {
            return paymentMethodRepository.save(paymentMethod);
        } catch (Exception e) {
            throw new RuntimeException("결제 수단 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    public List<PaymentMethod> getPaymentMethodsByUserId(Long userId) {
        return paymentMethodRepository.findByUserUserId(userId);
    }

    public Optional<PaymentMethod> getPaymentMethodById(Long methodId) {
        return paymentMethodRepository.findById(methodId);
    }

    @Transactional
    public PaymentMethod setDefaultPaymentMethod(Long userId, Long methodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new RuntimeException("Payment method not found: " + methodId));

        if (!paymentMethod.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Payment method does not belong to user: " + userId);
        }

        // 기존 기본 결제 수단 해제
        Optional<PaymentMethod> existingDefault = paymentMethodRepository
                .findByUserUserIdAndIsDefaultTrue(userId);
        if (existingDefault.isPresent()) {
            PaymentMethod defaultMethod = existingDefault.get();
            defaultMethod.setIsDefault(false);
            paymentMethodRepository.save(defaultMethod);
        }

        // 새로운 기본 결제 수단 설정
        paymentMethod.setIsDefault(true);
        return paymentMethodRepository.save(paymentMethod);
    }

    @Transactional
    public void deletePaymentMethod(Long userId, Long methodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new RuntimeException("Payment method not found: " + methodId));

        if (!paymentMethod.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Payment method does not belong to user: " + userId);
        }

        paymentMethodRepository.delete(paymentMethod);
    }

    /**
     * 서버 API를 통한 빌링키 발급
     * 참고: PortOne V2 API 문서 - https://developers.portone.io/opi/ko/integration/start/v2/billing/issue?v=v2
     * @param customerUid 고객 고유 식별자 (선택적, 없으면 PortOne이 자동 생성)
     * @param amount 초기 결제 금액 (최소 1,000원)
     * @param orderName 주문명
     * @return 빌링키 발급 결과 (카드 정보 포함)
     */
    @Transactional
    public Mono<Map<String, Object>> issueBillingKeyViaServer(String customerUid, Integer amount, String orderName) {
        // PortOne V2 API 형식에 맞춰 빌링키 발급 요청 생성
        // 참고: API를 통한 빌링키 발급은 카드 정보가 필요하므로, 
        // 실제로는 프론트엔드에서 PortOne.requestIssueBillingKey()를 사용하는 것이 권장됨
        Map<String, Object> billingKeyRequest = new java.util.HashMap<>();
        billingKeyRequest.put("channelKey", "channel-key-0590c7cd-d67e-4754-a464-29a860ba58de"); // TODO: 설정에서 가져오기
        billingKeyRequest.put("customer", Map.of("id", customerUid != null ? customerUid : "customer_" + System.currentTimeMillis()));
        
        // 카드 정보는 실제로는 프론트엔드에서 받아야 하지만, 
        // 여기서는 빌링키 발급이 이미 완료되었다고 가정하고 조회만 수행
        // 실제 구현에서는 프론트엔드에서 PortOne.requestIssueBillingKey()를 사용하는 것을 권장

        // 서버 API를 통한 빌링키 발급은 카드 정보가 필요하므로,
        // 프론트엔드에서 PortOne.requestIssueBillingKey()를 사용하는 것을 권장
        return Mono.error(new RuntimeException("서버 API를 통한 빌링키 발급은 카드 정보가 필요합니다. 프론트엔드에서 PortOne.requestIssueBillingKey()를 사용하세요."));
    }

    /**
     * 등록된 결제 수단으로 결제 실행
     * @param methodId 결제 수단 ID
     * @param amount 결제 금액
     * @param orderName 주문명
     * @return 결제 결과
     */
    @Transactional
    public Mono<Map<String, Object>> executePayment(Long methodId, Integer amount, String orderName) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new RuntimeException("Payment method not found: " + methodId));

        if (paymentMethod.getCustomerUid() == null || paymentMethod.getCustomerUid().trim().isEmpty()) {
            return Mono.error(new RuntimeException("결제 수단에 customerUid가 없습니다. 빌링키를 먼저 발급해주세요."));
        }

        // 빌링키로 결제 실행
        Map<String, Object> billingRequest = Map.of(
                "amount", amount,
                "merchantUid", "payment_" + System.currentTimeMillis(),
                "name", orderName
        );

        return portOneClient.getAccessToken()
                .flatMap(accessToken -> {
                    // 먼저 빌링키가 존재하는지 확인
                    return portOneClient.getBillingKey(paymentMethod.getCustomerUid(), accessToken)
                            .onErrorResume(error -> {
                                // 빌링키가 없거나 조회 실패 시 더 명확한 에러 메시지 반환
                                String errorMessage = error.getMessage();
                                if (errorMessage != null && errorMessage.contains("404")) {
                                    return Mono.error(new RuntimeException(
                                            "빌링키가 PortOne에 등록되어 있지 않습니다. customerUid: " + 
                                            paymentMethod.getCustomerUid() + 
                                            ". 빌링키를 다시 발급해주세요."));
                                }
                                return Mono.error(error);
                            })
                            .flatMap(billingKeyInfo -> {
                                // 빌링키가 존재하면 결제 실행
                                return portOneClient.executeBilling(
                                        paymentMethod.getCustomerUid(), 
                                        billingRequest, 
                                        accessToken);
                            });
                })
                .map(result -> {
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("success", true);
                    response.put("paymentMethodId", methodId);
                    response.put("customerUid", paymentMethod.getCustomerUid());
                    
                    // 포트원 응답에서 결제 정보 추출
                    if (result.containsKey("imp_uid")) {
                        response.put("impUid", result.get("imp_uid"));
                    } else if (result.containsKey("impUid")) {
                        response.put("impUid", result.get("impUid"));
                    }
                    if (result.containsKey("merchant_uid")) {
                        response.put("merchantUid", result.get("merchant_uid"));
                    } else if (result.containsKey("merchantUid")) {
                        response.put("merchantUid", result.get("merchantUid"));
                    }
                    if (result.containsKey("amount")) {
                        response.put("amount", result.get("amount"));
                    }
                    
                    return response;
                })
                .onErrorMap(error -> {
                    String errorMessage = error.getMessage();
                    if (errorMessage != null && errorMessage.contains("404")) {
                        return new RuntimeException(
                                "빌링키가 PortOne에 등록되어 있지 않습니다. " +
                                "결제 수단을 삭제하고 빌링키를 다시 발급해주세요. " +
                                "customerUid: " + paymentMethod.getCustomerUid());
                    }
                    return new RuntimeException("결제 실행 실패: " + errorMessage, error);
                });
    }
}

