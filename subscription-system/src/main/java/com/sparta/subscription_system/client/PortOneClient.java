package com.sparta.subscription_system.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class PortOneClient {

    private final WebClient webClient;
    private final String apiSecret;

    public PortOneClient(@Value("${portone.api.url}") String apiUrl,
                         @Value("${portone.api.secret}") String apiSecret) {
        this.webClient = WebClient.create(apiUrl);
        this.apiSecret = apiSecret;
    }

    // API Secret으로 인증 토큰 요청
    public Mono<String> getAccessToken() {
        return webClient.post()
                .uri("/login/api-secret")
                .bodyValue(Map.of("apiSecret", apiSecret))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("accessToken"));
    }

    // 결제 ID로 결제 정보 조회
    public Mono<Map> getPaymentDetails(String paymentId, String accessToken) {
        return webClient.get()
                .uri("/payments/{paymentId}", paymentId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 정기결제 빌링키 발급 (PortOne V2 API)
    // 참고: https://developers.portone.io/opi/ko/integration/start/v2/billing/issue?v=v2
    // POST /billing-keys 엔드포인트 사용 (customerUid는 body에 포함)
    public Mono<Map> issueBillingKey(Map<String, Object> billingKeyRequest, String accessToken) {
        return webClient.post()
                .uri("/billing-keys")
                .header("Authorization", "PortOne " + apiSecret)
                .header("Content-Type", "application/json")
                .bodyValue(billingKeyRequest)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 정기결제 실행 (빌링키로 결제)
    public Mono<Map> executeBilling(String customerUid, Map<String, Object> billingRequest, String accessToken) {
        return webClient.post()
                .uri("/billing-keys/{customerUid}/payments", customerUid)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(billingRequest)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 결제 취소
    public Mono<Map> cancelPayment(String paymentId, String accessToken, String reason) {
        return webClient.post()
                .uri("/payments/{paymentId}/cancel", paymentId)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(Map.of("reason", reason))
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 예약결제 스케줄 생성 (PortOne V2 API)
    // 참고: https://developers.portone.io/opi/ko/integration/start/v2/billing/schedule?v=v2
    // POST /payments/{payment_id}/schedule 엔드포인트 사용
    // payment_id는 고유한 결제 ID여야 함
    public Mono<Map> createSchedule(String paymentId, Map<String, Object> scheduleRequest, String accessToken) {
        return webClient.post()
                .uri("/payments/{paymentId}/schedule", paymentId)
                .header("Authorization", "PortOne " + apiSecret)
                .header("Content-Type", "application/json")
                .bodyValue(scheduleRequest)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 예약결제 스케줄 조회
    public Mono<Map> getSchedule(String customerUid, String scheduleId, String accessToken) {
        return webClient.get()
                .uri("/billing-keys/{customerUid}/schedules/{scheduleId}", customerUid, scheduleId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 예약결제 스케줄 목록 조회
    // PortOne V2 API: 인증 방식은 PortOne {apiSecret} 또는 Bearer {accessToken} 모두 지원 가능
    // 현재는 Bearer {accessToken} 방식 사용 (getAccessToken()으로 발급받은 토큰)
    public Mono<Map> getSchedules(String customerUid, String accessToken) {
        return webClient.get()
                .uri("/billing-keys/{customerUid}/schedules", customerUid)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 예약결제 스케줄 삭제 (customerUid + scheduleId 방식)
    // PortOne V2 API: 인증 방식은 PortOne {apiSecret} 또는 Bearer {accessToken} 모두 지원 가능
    // 현재는 Bearer {accessToken} 방식 사용 (getAccessToken()으로 발급받은 토큰)
    // 참고: PortOne V2 API 문서에 따르면 Authorization: PortOne {apiSecret} 형식도 사용 가능
    public Mono<Map> deleteSchedule(String customerUid, String scheduleId, String accessToken) {
        return webClient.delete()
                .uri("/billing-keys/{customerUid}/schedules/{scheduleId}", customerUid, scheduleId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 예약결제 스케줄 삭제 (paymentId 기반 - 고객사 거래번호로 삭제)
    // PortOne V2 API: 스케줄 생성 시 사용한 paymentId로 스케줄 삭제
    // 참고: 스케줄 생성이 POST /payments/{paymentId}/schedule이므로, 삭제도 동일한 paymentId 사용
    // DELETE /payments/{paymentId}/schedule 엔드포인트 사용
    public Mono<Map> deleteScheduleByPaymentId(String paymentId, String accessToken) {
        return webClient.delete()
                .uri("/payments/{paymentId}/schedule", paymentId)
                .header("Authorization", "PortOne " + apiSecret)
                .header("Content-Type", "application/json")
                .retrieve()
                .bodyToMono(Map.class);
    }
    
    // 예약결제 스케줄 삭제 (PortOne {apiSecret} 방식 사용 - 대안)
    // PortOne V2 API 문서 기준: Authorization: PortOne {apiSecret} 형식 사용
    public Mono<Map> deleteScheduleWithApiSecret(String customerUid, String scheduleId) {
        return webClient.delete()
                .uri("/billing-keys/{customerUid}/schedules/{scheduleId}", customerUid, scheduleId)
                .header("Authorization", "PortOne " + apiSecret)
                .header("Content-Type", "application/json")
                .retrieve()
                .bodyToMono(Map.class);
    }
    
    // 예약결제 스케줄 목록 조회 (PortOne {apiSecret} 방식 사용 - 대안)
    // PortOne V2 API 문서 기준: Authorization: PortOne {apiSecret} 형식 사용
    public Mono<Map> getSchedulesWithApiSecret(String customerUid) {
        return webClient.get()
                .uri("/billing-keys/{customerUid}/schedules", customerUid)
                .header("Authorization", "PortOne " + apiSecret)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 빌링키 정보 조회
    public Mono<Map> getBillingKey(String customerUid, String accessToken) {
        return webClient.get()
                .uri("/billing-keys/{customerUid}", customerUid)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 빌링키 삭제 (예약된 빌링 결제 취소)
    // 참고: 빌링키를 삭제하면 해당 빌링키로 예약된 모든 결제 스케줄도 함께 취소됩니다.
    public Mono<Map> deleteBillingKey(String customerUid, String accessToken) {
        return webClient.delete()
                .uri("/billing-keys/{customerUid}", customerUid)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 결제 예약 취소 (올바른 API - PortOne V2)
    // 참고: https://developers.portone.io/api/rest-v2/payment?v=v2
    // DELETE /payment-schedules 엔드포인트 사용
    // billingKey 또는 scheduleIds 중 하나 이상은 필수
    // - billingKey만 입력: 해당 빌링키로 예약된 모든 결제 예약 건 취소
    // - scheduleIds만 입력: 입력된 결제 예약 건 아이디에 해당하는 예약 건들 취소
    // - 둘 다 입력: scheduleIds에 해당하는 예약 건들 취소 (단, 예약한 빌링키가 입력된 빌링키와 일치해야 함)
    public Mono<Map> revokePaymentSchedules(String accessToken, String billingKey, java.util.List<String> scheduleIds) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        if (billingKey != null && !billingKey.trim().isEmpty()) {
            requestBody.put("billingKey", billingKey);
        }
        if (scheduleIds != null && !scheduleIds.isEmpty()) {
            requestBody.put("scheduleIds", scheduleIds);
        }
        
        return webClient.method(HttpMethod.DELETE)
                .uri("/payment-schedules")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(Map.class);
    }

    // 결제 예약 취소 (PortOne API Secret 방식)
    public Mono<Map> revokePaymentSchedulesWithApiSecret(String billingKey, java.util.List<String> scheduleIds) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        if (billingKey != null && !billingKey.trim().isEmpty()) {
            requestBody.put("billingKey", billingKey);
        }
        if (scheduleIds != null && !scheduleIds.isEmpty()) {
            requestBody.put("scheduleIds", scheduleIds);
        }
        
        return webClient.method(HttpMethod.DELETE)
                .uri("/payment-schedules")
                .header("Authorization", "PortOne " + apiSecret)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(Map.class);
    }
}


