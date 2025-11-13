package com.sparta.subscription_system.service;

import com.sparta.subscription_system.client.PortOneClient;
import com.sparta.subscription_system.entity.*;
import com.sparta.subscription_system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final SubscriptionRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final PortOneClient portOneClient;

    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                              PlanRepository planRepository,
                              PaymentMethodRepository paymentMethodRepository,
                              SubscriptionInvoiceRepository invoiceRepository,
                              SubscriptionRefundRepository refundRepository,
                              UserRepository userRepository,
                              PortOneClient portOneClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.invoiceRepository = invoiceRepository;
        this.refundRepository = refundRepository;
        this.userRepository = userRepository;
        this.portOneClient = portOneClient;
    }

    @Transactional
    public Subscription createSubscription(Long userId, Long planId, Long paymentMethodId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));

        if (plan.getStatus() != Plan.PlanStatus.ACTIVE) {
            throw new RuntimeException("Plan is not active: " + planId);
        }

        PaymentMethod paymentMethod = null;
        if (paymentMethodId != null) {
            paymentMethod = paymentMethodRepository.findById(paymentMethodId)
                    .orElseThrow(() -> new RuntimeException("Payment method not found: " + paymentMethodId));

            if (!paymentMethod.getUser().getUserId().equals(userId)) {
                throw new RuntimeException("Payment method does not belong to user: " + userId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentPeriodStart = now;
        LocalDateTime currentPeriodEnd = calculatePeriodEnd(now, plan.getBillingInterval());
        LocalDateTime trialEnd = null;

        Subscription.SubscriptionStatus status = Subscription.SubscriptionStatus.TRIALING;
        if (plan.getTrialPeriodDays() > 0) {
            trialEnd = now.plusDays(plan.getTrialPeriodDays());
            status = Subscription.SubscriptionStatus.TRIALING;
        } else {
            status = Subscription.SubscriptionStatus.ACTIVE;
        }

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setPaymentMethod(paymentMethod);
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(currentPeriodStart);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setTrialEnd(trialEnd);

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        // 구독 생성 시 초기 결제 내역을 청구서로 기록
        // 빌링키 발급 시 결제가 완료된 경우를 고려하여 청구서 생성
        if (paymentMethod != null) {
            // 빌링키 발급 시 결제가 완료된 경우를 대비하여 초기 청구서 생성
            // 실제로는 빌링키 발급 시 이미 결제가 완료되었으므로, 여기서는 구독 생성 시점의 청구서만 생성
            // 체험 기간이 없으면 첫 결제 청구서 생성
            if (status == Subscription.SubscriptionStatus.ACTIVE && plan.getTrialPeriodDays() == 0) {
                SubscriptionInvoice initialInvoice = new SubscriptionInvoice();
                initialInvoice.setSubscription(savedSubscription);
                initialInvoice.setAmount(plan.getPrice());
                initialInvoice.setStatus(SubscriptionInvoice.InvoiceStatus.PAID);
                initialInvoice.setPaidAt(LocalDateTime.now());
                initialInvoice.setDueDate(now);
                initialInvoice.setAttemptCount(1);
                initialInvoice.setImpUid("initial_payment_" + savedSubscription.getSubscriptionId());
                invoiceRepository.save(initialInvoice);
            }
        }

        // 결제 수단이 있고 활성 상태인 경우 예약결제 스케줄 생성
        if (paymentMethod != null && (status == Subscription.SubscriptionStatus.ACTIVE || status == Subscription.SubscriptionStatus.TRIALING)) {
            createBillingSchedule(savedSubscription)
                    .flatMap(result -> {
                        // 스케줄 생성 응답에서 scheduleId 추출
                        @SuppressWarnings("unchecked")
                        Map<String, Object> scheduleResponse = (Map<String, Object>) result;
                        String scheduleId = extractScheduleIdFromResponse(scheduleResponse, savedSubscription.getSubscriptionId());
                        
                        if (scheduleId != null && !scheduleId.trim().isEmpty()) {
                            // 응답에서 scheduleId를 찾았으면 저장하고 반환
                            savedSubscription.setScheduleId(scheduleId);
                            subscriptionRepository.save(savedSubscription);
                            System.out.println("예약결제 스케줄 생성 성공: subscriptionId=" + savedSubscription.getSubscriptionId() + 
                                             ", scheduleId=" + scheduleId);
                            return Mono.just(true);
                        } else {
                            // 응답에서 scheduleId를 찾지 못한 경우, 스케줄 목록을 조회해서 찾기
                            System.out.println("[스케줄 생성] 응답에서 scheduleId를 찾지 못함. 스케줄 목록 조회로 fallback: subscriptionId=" + 
                                             savedSubscription.getSubscriptionId());
                            return findScheduleIdFromSchedules(savedSubscription);
                        }
                    })
                    .subscribe(
                            success -> {
                                if (success) {
                                    System.out.println("예약결제 스케줄 생성 및 scheduleId 저장 완료: subscriptionId=" + savedSubscription.getSubscriptionId());
                                }
                            },
                            error -> {
                                // 스케줄 생성 실패 시 로그만 남김 (구독은 이미 생성됨)
                                System.err.println("예약결제 스케줄 생성 실패: " + error.getMessage());
                                error.printStackTrace();
                            }
                    );
        }

        return savedSubscription;
    }

    /**
     * 빌링키 발급 시 결제 내역을 청구서로 기록
     * @param userId 사용자 ID
     * @param planId 플랜 ID (선택적)
     * @param amount 결제 금액
     * @param impUid 포트원 결제 ID
     * @param paymentId 결제 ID
     * @return 생성된 청구서 (구독이 있으면 연결, 없으면 null)
     */
    @Transactional
    public SubscriptionInvoice createBillingKeyPaymentInvoice(Long userId, Long planId, BigDecimal amount, String impUid, String paymentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // 해당 사용자의 활성 구독 찾기 (플랜 ID가 있으면 해당 플랜의 구독)
        Subscription subscription = null;
        List<Subscription> userSubscriptions = subscriptionRepository.findByUserUserId(userId);
        
        if (planId != null) {
            subscription = userSubscriptions.stream()
                    .filter(sub -> sub.getPlan().getPlanId().equals(planId) 
                            && (sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE 
                                || sub.getStatus() == Subscription.SubscriptionStatus.TRIALING))
                    .findFirst()
                    .orElse(null);
        } else {
            // 플랜 ID가 없으면 가장 최근 활성 구독 사용
            subscription = userSubscriptions.stream()
                    .filter(sub -> sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE 
                            || sub.getStatus() == Subscription.SubscriptionStatus.TRIALING)
                    .findFirst()
                    .orElse(null);
        }

        // 구독이 없으면 청구서를 생성하지 않음 (나중에 구독 생성 시 연결)
        if (subscription == null) {
            return null;
        }

        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setSubscription(subscription);
        invoice.setAmount(amount);
        invoice.setStatus(SubscriptionInvoice.InvoiceStatus.PAID);
        invoice.setImpUid(impUid);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setDueDate(LocalDateTime.now());
        invoice.setAttemptCount(1);

        return invoiceRepository.save(invoice);
    }

    /**
     * 등록된 결제 수단으로 결제한 내역을 청구서로 기록 (구독 없이도 가능)
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param impUid 포트원 결제 ID
     * @param merchantUid 주문 ID
     * @param orderName 주문명
     * @return 생성된 청구서 (구독이 있으면 연결, 없으면 null)
     */
    @Transactional
    public SubscriptionInvoice createPaymentInvoice(Long userId, BigDecimal amount, String impUid, String merchantUid, String orderName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // 해당 사용자의 활성 구독 찾기 (가장 최근 활성 구독 사용)
        List<Subscription> userSubscriptions = subscriptionRepository.findByUserUserId(userId);
        Subscription subscription = userSubscriptions.stream()
                .filter(sub -> sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE 
                        || sub.getStatus() == Subscription.SubscriptionStatus.TRIALING)
                .findFirst()
                .orElse(null);

        // 구독이 없으면 청구서를 생성하지 않음 (구독이 없어도 결제는 가능하지만 청구서는 구독이 있을 때만 생성)
        if (subscription == null) {
            return null;
        }

        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setSubscription(subscription);
        invoice.setAmount(amount);
        invoice.setStatus(SubscriptionInvoice.InvoiceStatus.PAID);
        invoice.setImpUid(impUid);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setDueDate(LocalDateTime.now());
        invoice.setAttemptCount(1);

        return invoiceRepository.save(invoice);
    }

    private LocalDateTime calculatePeriodEnd(LocalDateTime start, String billingInterval) {
        if ("yearly".equalsIgnoreCase(billingInterval) || "annual".equalsIgnoreCase(billingInterval)) {
            return start.plusYears(1);
        } else {
            return start.plusMonths(1); // default to monthly
        }
    }

    public List<Subscription> getSubscriptionsByUserId(Long userId) {
        return subscriptionRepository.findByUserUserId(userId);
    }

    public Optional<Subscription> getSubscriptionById(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId);
    }

    @Transactional
    public Subscription cancelSubscription(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        if (!subscription.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Subscription does not belong to user: " + userId);
        }

        if (subscription.getStatus() == Subscription.SubscriptionStatus.CANCELED ||
            subscription.getStatus() == Subscription.SubscriptionStatus.ENDED) {
            throw new RuntimeException("Subscription is already canceled or ended");
        }

        // PortOne API를 통해 예약결제 스케줄 삭제 (동기적으로 처리)
        PaymentMethod paymentMethod = subscription.getPaymentMethod();
        if (paymentMethod != null && paymentMethod.getCustomerUid() != null) {
            try {
                Boolean deleteResult = deleteBillingSchedule(subscription).block(Duration.ofSeconds(10));
                if (deleteResult != null && deleteResult) {
                    System.out.println("예약결제 스케줄 삭제 성공: subscriptionId=" + subscriptionId);
                } else {
                    System.err.println("예약결제 스케줄 삭제 실패: subscriptionId=" + subscriptionId);
                    // 스케줄 삭제 실패 시 예외를 던져서 구독 취소를 중단할지 결정할 수 있음
                    // 현재는 로그만 남기고 계속 진행
                }
            } catch (Exception e) {
                System.err.println("예약결제 스케줄 삭제 중 오류 발생: subscriptionId=" + subscriptionId + ", error=" + e.getMessage());
                e.printStackTrace();
                // 스케줄 삭제 실패해도 구독 취소는 진행 (로그만 남김)
                // 필요시 여기서 예외를 던져서 구독 취소를 중단할 수 있음
            }
        } else {
            System.out.println("결제 수단 또는 customerUid가 없어 스케줄 삭제를 건너뜁니다: subscriptionId=" + subscriptionId);
        }

        // 구독 상태를 취소로 변경
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        Subscription savedSubscription = subscriptionRepository.save(subscription);

        // 구독 취소 시 관련된 모든 청구서의 상태를 CANCELED로 변경
        List<SubscriptionInvoice> invoices = invoiceRepository.findBySubscriptionSubscriptionId(subscriptionId);
        for (SubscriptionInvoice invoice : invoices) {
            // 모든 청구서를 CANCELED 상태로 변경 (결제 완료된 청구서도 포함)
            invoice.setStatus(SubscriptionInvoice.InvoiceStatus.CANCELED);
            invoice.setErrorMessage("구독 취소");
            invoiceRepository.save(invoice);
        }

        return savedSubscription;
    }

    @Transactional
    public SubscriptionInvoice createInvoice(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE &&
            subscription.getStatus() != Subscription.SubscriptionStatus.PAST_DUE) {
            throw new RuntimeException("Cannot create invoice for subscription with status: " + subscription.getStatus());
        }

        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setSubscription(subscription);
        invoice.setAmount(subscription.getPlan().getPrice());
        invoice.setStatus(SubscriptionInvoice.InvoiceStatus.PENDING);
        invoice.setDueDate(subscription.getCurrentPeriodEnd());
        invoice.setAttemptCount(0);

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Mono<Boolean> processInvoicePayment(Long invoiceId) {
        SubscriptionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() != SubscriptionInvoice.InvoiceStatus.PENDING) {
            return Mono.just(false);
        }

        Subscription subscription = invoice.getSubscription();
        PaymentMethod paymentMethod = subscription.getPaymentMethod();

        if (paymentMethod == null) {
            invoice.setStatus(SubscriptionInvoice.InvoiceStatus.FAILED);
            invoice.setErrorMessage("No payment method associated with subscription");
            invoiceRepository.save(invoice);
            return Mono.just(false);
        }

        // PortOne 정기결제 실행
        return portOneClient.getAccessToken()
                .flatMap(accessToken -> {
                    Map<String, Object> billingRequest = Map.of(
                            "amount", invoice.getAmount().intValue(),
                            "merchantUid", "invoice_" + invoiceId,
                            "name", subscription.getPlan().getName() + " 구독료"
                    );

                    return portOneClient.executeBilling(paymentMethod.getCustomerUid(), billingRequest, accessToken)
                            .map(billingResult -> {
                                String impUid = (String) billingResult.get("imp_uid");
                                if (impUid != null) {
                                    invoice.setStatus(SubscriptionInvoice.InvoiceStatus.PAID);
                                    invoice.setImpUid(impUid);
                                    invoice.setPaidAt(LocalDateTime.now());
                                    invoice.setAttemptCount(invoice.getAttemptCount() + 1);

                                    // 구독 기간 연장
                                    LocalDateTime newPeriodStart = subscription.getCurrentPeriodEnd();
                                    LocalDateTime newPeriodEnd = calculatePeriodEnd(newPeriodStart, subscription.getPlan().getBillingInterval());
                                    subscription.setCurrentPeriodStart(newPeriodStart);
                                    subscription.setCurrentPeriodEnd(newPeriodEnd);
                                    subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);

                                    invoiceRepository.save(invoice);
                                    subscriptionRepository.save(subscription);

                                    return true;
                                } else {
                                    invoice.setStatus(SubscriptionInvoice.InvoiceStatus.FAILED);
                                    invoice.setAttemptCount(invoice.getAttemptCount() + 1);
                                    invoice.setErrorMessage("Payment failed: No imp_uid returned");
                                    invoiceRepository.save(invoice);
                                    return false;
                                }
                            })
                            .onErrorResume(error -> {
                                invoice.setStatus(SubscriptionInvoice.InvoiceStatus.FAILED);
                                invoice.setAttemptCount(invoice.getAttemptCount() + 1);
                                invoice.setErrorMessage("Payment failed: " + error.getMessage());
                                invoiceRepository.save(invoice);

                                // 결제 실패 시 구독 상태를 PAST_DUE로 변경
                                subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
                                subscriptionRepository.save(subscription);

                                return Mono.just(false);
                            });
                })
                .onErrorReturn(false);
    }

    public List<SubscriptionInvoice> getInvoicesBySubscriptionId(Long subscriptionId) {
        return invoiceRepository.findBySubscriptionSubscriptionId(subscriptionId);
    }

    public List<SubscriptionInvoice> getInvoicesByUserId(Long userId) {
        return invoiceRepository.findBySubscriptionUserUserId(userId);
    }

    @Transactional
    public Mono<Boolean> refundInvoice(Long invoiceId, BigDecimal amount, String reason) {
        SubscriptionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() != SubscriptionInvoice.InvoiceStatus.PAID) {
            throw new RuntimeException("Only paid invoices can be refunded");
        }

        if (invoice.getImpUid() == null) {
            throw new RuntimeException("Invoice does not have imp_uid");
        }

        return portOneClient.getAccessToken()
                .flatMap(accessToken -> portOneClient.cancelPayment(invoice.getImpUid(), accessToken, reason))
                .map(cancelResult -> {
                    SubscriptionRefund refund = new SubscriptionRefund();
                    refund.setInvoice(invoice);
                    refund.setAmount(amount);
                    refund.setReason(reason);
                    refund.setStatus(SubscriptionRefund.RefundStatus.COMPLETED);
                    refundRepository.save(refund);

                    invoice.setStatus(SubscriptionInvoice.InvoiceStatus.REFUNDED);
                    invoiceRepository.save(invoice);

                    return true;
                })
                .onErrorReturn(false);
    }

    /**
     * 예약결제 스케줄 생성
     * 구독의 billingInterval에 따라 반복 결제 스케줄을 생성합니다.
     */
    private Mono<Map> createBillingSchedule(Subscription subscription) {
        PaymentMethod paymentMethod = subscription.getPaymentMethod();
        if (paymentMethod == null || paymentMethod.getCustomerUid() == null) {
            return Mono.error(new RuntimeException("Payment method or customerUid is missing"));
        }

        Plan plan = subscription.getPlan();
        String billingInterval = plan.getBillingInterval();
        
        // billingInterval에 따라 스케줄 간격 설정
        // "monthly" -> 매월, "yearly" -> 매년
        String scheduleInterval = "monthly".equalsIgnoreCase(billingInterval) ? "month" : "year";
        
        // 첫 결제 예정일 설정 (체험 기간이 있으면 체험 종료일, 없으면 다음 기간 시작일)
        LocalDateTime scheduledAt = subscription.getTrialEnd() != null 
                ? subscription.getTrialEnd() 
                : subscription.getCurrentPeriodEnd();

        // 저장된 billingKey가 있으면 직접 사용, 없으면 customerUid로 조회
        String billingKey = paymentMethod.getBillingKey();
        
        if (billingKey != null && !billingKey.trim().isEmpty()) {
            // 저장된 billingKey를 직접 사용
            return portOneClient.getAccessToken()
                    .flatMap(accessToken -> {
                        // PortOne V2 API 형식에 맞춰 스케줄 생성
                        // 참고: https://developers.portone.io/opi/ko/integration/start/v2/billing/schedule?v=v2
                        
                        // 고유한 payment_id 생성
                        String paymentId = "schedule_" + subscription.getSubscriptionId() + "_" + System.currentTimeMillis();
                        
                        // ISO 8601 형식으로 변환 (예: 2023-08-24T14:15:22Z)
                        String timeToPay = scheduledAt.toString().replace(" ", "T") + "Z";
                        
                        // payment 객체 생성
                        Map<String, Object> payment = new java.util.HashMap<>();
                        payment.put("billingKey", billingKey);
                        payment.put("orderName", plan.getName() + " 구독료");
                        
                        Map<String, Object> customer = new java.util.HashMap<>();
                        customer.put("id", String.valueOf(subscription.getUser().getUserId()));
                        payment.put("customer", customer);
                        
                        Map<String, Object> amount = new java.util.HashMap<>();
                        amount.put("total", plan.getPrice().intValue());
                        payment.put("amount", amount);
                        payment.put("currency", "KRW");
                        
                        // scheduleRequest 생성
                        Map<String, Object> scheduleRequest = new java.util.HashMap<>();
                        scheduleRequest.put("payment", payment);
                        scheduleRequest.put("timeToPay", timeToPay);
                        
                        // metadata에 subscriptionId를 포함하여 나중에 스케줄 삭제 시 사용
                        Map<String, Object> metadata = new java.util.HashMap<>();
                        metadata.put("subscriptionId", subscription.getSubscriptionId());
                        metadata.put("userId", subscription.getUser().getUserId());
                        scheduleRequest.put("metadata", metadata);

                        return portOneClient.createSchedule(paymentId, scheduleRequest, accessToken);
                    })
                    .onErrorMap(error -> new RuntimeException("예약결제 스케줄 생성 실패: " + error.getMessage(), error));
        } else {
            // billingKey가 없으면 customerUid로 조회 (기존 방식, 재시도 로직 포함)
            return portOneClient.getAccessToken()
                    .flatMap(accessToken -> {
                        // 먼저 빌링키가 존재하는지 확인 (재시도 로직 포함)
                        // 빌링키 발급 후 PortOne에 등록되는 데 시간이 걸릴 수 있으므로 재시도
                        return portOneClient.getBillingKey(paymentMethod.getCustomerUid(), accessToken)
                                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2))
                                        .filter(error -> {
                                            // 404 에러인 경우에만 재시도
                                            String errorMessage = error.getMessage();
                                            return errorMessage != null && 
                                                   (errorMessage.contains("404") || 
                                                    errorMessage.contains("not found") ||
                                                    errorMessage.contains("등록되어 있지 않습니다"));
                                        })
                                        .doBeforeRetry(retrySignal -> 
                                            System.out.println("빌링키 조회 재시도 중... customerUid: " + 
                                                paymentMethod.getCustomerUid() + 
                                                " (시도: " + retrySignal.totalRetries() + "/3)")))
                                .onErrorResume(error -> {
                                    // 재시도 후에도 실패하면 에러 반환
                                    String errorMessage = error.getMessage();
                                    if (errorMessage != null && (errorMessage.contains("404") || errorMessage.contains("not found"))) {
                                        return Mono.error(new RuntimeException(
                                                "빌링키가 PortOne에 등록되어 있지 않습니다. customerUid: " + 
                                                paymentMethod.getCustomerUid() + 
                                                ". 빌링키를 먼저 발급해주세요."));
                                    }
                                    return Mono.error(error);
                                })
                                .flatMap(billingKeyInfo -> {
                                    // PortOne V2 API 형식에 맞춰 스케줄 생성
                                    // 참고: https://developers.portone.io/opi/ko/integration/start/v2/billing/schedule?v=v2
                                    
                                    // 고유한 payment_id 생성
                                    String paymentId = "schedule_" + subscription.getSubscriptionId() + "_" + System.currentTimeMillis();
                                    
                                    // ISO 8601 형식으로 변환 (예: 2023-08-24T14:15:22Z)
                                    String timeToPay = scheduledAt.toString().replace(" ", "T") + "Z";
                                    
                                    // payment 객체 생성
                                    Map<String, Object> payment = new java.util.HashMap<>();
                                    
                                    // billingKeyInfo에서 billingKey 추출
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> billingKeyInfoMap = (Map<String, Object>) billingKeyInfo.get("billingKeyInfo");
                                    String extractedBillingKey = null;
                                    if (billingKeyInfoMap != null && billingKeyInfoMap.containsKey("billingKey")) {
                                        extractedBillingKey = (String) billingKeyInfoMap.get("billingKey");
                                    } else if (billingKeyInfo.containsKey("billingKey")) {
                                        extractedBillingKey = (String) billingKeyInfo.get("billingKey");
                                    }
                                    
                                    if (extractedBillingKey == null) {
                                        return Mono.error(new RuntimeException("빌링키 정보를 찾을 수 없습니다."));
                                    }
                                    
                                    payment.put("billingKey", extractedBillingKey);
                                    payment.put("orderName", plan.getName() + " 구독료");
                                    
                                    Map<String, Object> customer = new java.util.HashMap<>();
                                    customer.put("id", String.valueOf(subscription.getUser().getUserId()));
                                    payment.put("customer", customer);
                                    
                                    Map<String, Object> amount = new java.util.HashMap<>();
                                    amount.put("total", plan.getPrice().intValue());
                                    payment.put("amount", amount);
                                    payment.put("currency", "KRW");
                                    
                                    // scheduleRequest 생성
                                    Map<String, Object> scheduleRequest = new java.util.HashMap<>();
                                    scheduleRequest.put("payment", payment);
                                    scheduleRequest.put("timeToPay", timeToPay);
                                    
                                    // metadata에 subscriptionId를 포함하여 나중에 스케줄 삭제 시 사용
                                    Map<String, Object> metadata = new java.util.HashMap<>();
                                    metadata.put("subscriptionId", subscription.getSubscriptionId());
                                    metadata.put("userId", subscription.getUser().getUserId());
                                    scheduleRequest.put("metadata", metadata);

                                    return portOneClient.createSchedule(paymentId, scheduleRequest, accessToken);
                                });
                    })
                    .onErrorMap(error -> new RuntimeException("예약결제 스케줄 생성 실패: " + error.getMessage(), error));
        }
    }

    /**
     * 스케줄 생성 응답에서 scheduleId 추출
     */
    private String extractScheduleIdFromResponse(Map<String, Object> scheduleResponse, Long subscriptionId) {
        // 디버깅: 응답 전체 구조 로그 출력
        System.out.println("[스케줄 생성 응답] subscriptionId=" + subscriptionId + 
                         ", 응답: " + scheduleResponse);
        
        String scheduleId = null;
        
        // 응답 구조에 따라 scheduleId 추출 (여러 가능한 필드명 확인)
        // 1. 직접 id 필드
        if (scheduleResponse.containsKey("id")) {
            Object idObj = scheduleResponse.get("id");
            if (idObj != null) {
                scheduleId = idObj.toString();
            }
        }
        // 2. scheduleId 필드
        else if (scheduleResponse.containsKey("scheduleId")) {
            Object scheduleIdObj = scheduleResponse.get("scheduleId");
            if (scheduleIdObj != null) {
                scheduleId = scheduleIdObj.toString();
            }
        }
        // 3. 중첩된 schedule 객체 내부의 id
        else if (scheduleResponse.containsKey("schedule")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> scheduleObj = (Map<String, Object>) scheduleResponse.get("schedule");
            if (scheduleObj != null) {
                if (scheduleObj.containsKey("id")) {
                    Object idObj = scheduleObj.get("id");
                    if (idObj != null) {
                        scheduleId = idObj.toString();
                    }
                } else if (scheduleObj.containsKey("scheduleId")) {
                    Object scheduleIdObj = scheduleObj.get("scheduleId");
                    if (scheduleIdObj != null) {
                        scheduleId = scheduleIdObj.toString();
                    }
                }
            }
        }
        // 4. data 객체 내부 확인
        else if (scheduleResponse.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataObj = (Map<String, Object>) scheduleResponse.get("data");
            if (dataObj != null) {
                if (dataObj.containsKey("id")) {
                    Object idObj = dataObj.get("id");
                    if (idObj != null) {
                        scheduleId = idObj.toString();
                    }
                } else if (dataObj.containsKey("scheduleId")) {
                    Object scheduleIdObj = dataObj.get("scheduleId");
                    if (scheduleIdObj != null) {
                        scheduleId = scheduleIdObj.toString();
                    }
                }
            }
        }
        
        if (scheduleId == null || scheduleId.trim().isEmpty()) {
            System.err.println("[스케줄 생성] 응답에서 scheduleId를 찾지 못함: subscriptionId=" + subscriptionId);
            System.err.println("응답 키 목록: " + scheduleResponse.keySet());
        }
        
        return scheduleId;
    }

    /**
     * 스케줄 목록을 조회하여 해당 구독의 scheduleId 찾기 (fallback)
     */
    private Mono<Boolean> findScheduleIdFromSchedules(Subscription subscription) {
        PaymentMethod paymentMethod = subscription.getPaymentMethod();
        if (paymentMethod == null || paymentMethod.getCustomerUid() == null) {
            System.err.println("[스케줄 조회] 결제 수단 또는 customerUid가 없습니다. subscriptionId=" + subscription.getSubscriptionId());
            return Mono.just(false);
        }

        Long subscriptionId = subscription.getSubscriptionId();
        String customerUid = paymentMethod.getCustomerUid();
        
        System.out.println("[스케줄 조회] 시작: subscriptionId=" + subscriptionId + ", customerUid=" + customerUid);

        return portOneClient.getAccessToken()
                .flatMap(accessToken -> 
                    portOneClient.getSchedules(customerUid, accessToken)
                        .map(schedules -> {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> scheduleList = (List<Map<String, Object>>) schedules.get("schedules");
                            
                            if (scheduleList == null || scheduleList.isEmpty()) {
                                System.out.println("[스케줄 조회] 스케줄 목록이 비어있습니다. subscriptionId=" + subscriptionId);
                                return false;
                            }

                            System.out.println("[스케줄 조회] 조회된 스케줄 수: " + scheduleList.size() + ", subscriptionId=" + subscriptionId);

                            // 해당 구독의 스케줄 찾기
                            for (Map<String, Object> schedule : scheduleList) {
                                String scheduleId = (String) schedule.get("id");
                                if (scheduleId == null) {
                                    continue;
                                }
                                
                                // metadata에서 subscriptionId 확인
                                @SuppressWarnings("unchecked")
                                Map<String, Object> metadata = (Map<String, Object>) schedule.get("metadata");
                                
                                if (metadata != null) {
                                    Object subId = metadata.get("subscriptionId");
                                    if (subId != null) {
                                        // Long 타입 비교 (숫자와 문자열 모두 처리)
                                        boolean matches = false;
                                        if (subId instanceof Number) {
                                            matches = ((Number) subId).longValue() == subscriptionId;
                                        } else if (subId instanceof String) {
                                            try {
                                                matches = Long.parseLong((String) subId) == subscriptionId;
                                            } catch (NumberFormatException e) {
                                                // 무시
                                            }
                                        } else {
                                            matches = subId.equals(subscriptionId);
                                        }
                                        
                                        if (matches) {
                                            // scheduleId를 찾았으면 저장
                                            subscription.setScheduleId(scheduleId);
                                            subscriptionRepository.save(subscription);
                                            System.out.println("[스케줄 조회] scheduleId 찾음: subscriptionId=" + subscriptionId + 
                                                             ", scheduleId=" + scheduleId);
                                            return true;
                                        }
                                    }
                                }
                                
                                // paymentId 패턴으로도 확인 (fallback)
                                String paymentId = (String) schedule.get("paymentId");
                                if (paymentId != null) {
                                    String expectedPattern = "schedule_" + subscriptionId + "_";
                                    if (paymentId.startsWith(expectedPattern)) {
                                        subscription.setScheduleId(scheduleId);
                                        subscriptionRepository.save(subscription);
                                        System.out.println("[스케줄 조회] scheduleId 찾음 (paymentId 패턴): subscriptionId=" + subscriptionId + 
                                                         ", scheduleId=" + scheduleId);
                                        return true;
                                    }
                                }
                            }

                            System.out.println("[스케줄 조회] 해당 구독의 스케줄을 찾을 수 없습니다. subscriptionId=" + subscriptionId);
                            return false;
                        })
                        .onErrorResume(error -> {
                            System.err.println("[스케줄 조회] 조회 중 오류 발생: subscriptionId=" + subscriptionId + 
                                             ", customerUid=" + customerUid + ", error=" + error.getMessage());
                            error.printStackTrace();
                            return Mono.just(false);
                        })
                )
                .onErrorReturn(false);
    }

    /**
     * 예약결제 스케줄 삭제
     * PortOne V2 API의 올바른 엔드포인트 사용: DELETE /payment-schedules
     * 참고: https://developers.portone.io/api/rest-v2/payment?v=v2
     * 
     * 저장된 scheduleId를 직접 사용하여 삭제 요청을 보냅니다.
     * 
     * @return true: 스케줄 삭제 성공 또는 스케줄이 없음 (이미 삭제됨), false: 삭제 실패
     */
    public Mono<Boolean> deleteBillingSchedule(Subscription subscription) {
        Long subscriptionId = subscription.getSubscriptionId();
        String scheduleId = subscription.getScheduleId();
        
        System.out.println("[스케줄 삭제] 시작: subscriptionId=" + subscriptionId + ", scheduleId=" + scheduleId);

        // scheduleId가 없으면 삭제할 스케줄이 없는 것으로 처리
        if (scheduleId == null || scheduleId.trim().isEmpty()) {
            System.out.println("[스케줄 삭제] scheduleId가 없습니다. subscriptionId=" + subscriptionId + 
                             " (이미 삭제되었거나 생성되지 않음)");
            return Mono.just(true); // idempotent: 스케줄이 없으면 성공으로 처리
        }

        return portOneClient.getAccessToken()
                .flatMap(accessToken -> {
                    // 저장된 scheduleId를 직접 사용하여 삭제 요청
                    List<String> scheduleIdsToDelete = java.util.Collections.singletonList(scheduleId);
                    
                    System.out.println("[스케줄 삭제] scheduleId로 직접 삭제 시도: subscriptionId=" + subscriptionId + 
                                     ", scheduleId=" + scheduleId);

                    return portOneClient.revokePaymentSchedules(accessToken, null, scheduleIdsToDelete)
                            .map(result -> {
                                @SuppressWarnings("unchecked")
                                List<String> revokedIds = (List<String>) result.get("revokedScheduleIds");
                                if (revokedIds != null && !revokedIds.isEmpty()) {
                                    System.out.println("[스케줄 삭제] 삭제 성공: subscriptionId=" + subscriptionId + 
                                                     ", scheduleId=" + scheduleId + 
                                                     ", revokedScheduleIds=" + revokedIds);
                                }
                                return true;
                            })
                            .doOnSuccess(result -> 
                                System.out.println("[스케줄 삭제] 삭제 완료: subscriptionId=" + subscriptionId + 
                                                 ", scheduleId=" + scheduleId))
                            .onErrorResume(error -> {
                                String errorMsg = error.getMessage();
                                boolean is404 = error instanceof WebClientResponseException.NotFound ||
                                                (errorMsg != null && (errorMsg.contains("404") || 
                                                                      errorMsg.contains("Not Found") ||
                                                                      errorMsg.contains("not found")));
                                
                                if (is404) {
                                    System.out.println("[스케줄 삭제] 스케줄이 이미 삭제됨 (404): subscriptionId=" + subscriptionId + 
                                                     ", scheduleId=" + scheduleId);
                                    return Mono.just(true); // 404는 이미 삭제된 것으로 처리 (idempotent)
                                }
                                
                                System.err.println("[스케줄 삭제] 삭제 중 오류: subscriptionId=" + subscriptionId + 
                                                  ", scheduleId=" + scheduleId + 
                                                  ", error=" + errorMsg);
                                error.printStackTrace();
                                return Mono.just(false); // 삭제 실패 시 false 반환
                            });
                })
                .onErrorReturn(false)
                .doOnSuccess(result -> {
                    if (result) {
                        System.out.println("[스케줄 삭제] 프로세스 완료: subscriptionId=" + subscriptionId + 
                                         " (삭제 성공 또는 스케줄 없음)");
                    } else {
                        System.err.println("[스케줄 삭제] 프로세스 실패: subscriptionId=" + subscriptionId);
                    }
                });
    }
}


