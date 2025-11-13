package com.sparta.subscription_system.scheduler;

import com.sparta.subscription_system.entity.Subscription;
import com.sparta.subscription_system.entity.SubscriptionInvoice;
import com.sparta.subscription_system.repository.SubscriptionRepository;
import com.sparta.subscription_system.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 구독 결제 자동화 스케줄러
 * - 매일 자정에 실행되어 만료 예정 구독의 청구서를 생성하고 결제를 처리합니다.
 */
@Component
public class SubscriptionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Autowired
    public SubscriptionScheduler(SubscriptionRepository subscriptionRepository,
                                SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * 매일 자정(00:00)에 실행
     * current_period_end가 오늘 또는 과거인 활성 구독의 청구서를 생성하고 결제를 처리합니다.
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정
    @Transactional
    public void processDueSubscriptions() {
        logger.info("구독 청구서 생성 및 결제 처리 스케줄러 시작");

        LocalDateTime now = LocalDateTime.now();
        
        // current_period_end가 오늘 또는 과거인 활성 구독 조회
        List<Subscription> dueSubscriptions = subscriptionRepository
                .findByStatusInAndCurrentPeriodEndLessThanEqual(
                        List.of(Subscription.SubscriptionStatus.ACTIVE, Subscription.SubscriptionStatus.PAST_DUE),
                        now
                );

        logger.info("처리 대상 구독 수: {}", dueSubscriptions.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (Subscription subscription : dueSubscriptions) {
            try {
                // 이미 생성된 청구서가 있는지 확인
                boolean hasPendingInvoice = subscription.getInvoices().stream()
                        .anyMatch(invoice -> invoice.getStatus() == SubscriptionInvoice.InvoiceStatus.PENDING
                                && invoice.getDueDate().isBefore(now.plusDays(1)));

                if (hasPendingInvoice) {
                    logger.debug("구독 ID {}: 이미 대기 중인 청구서가 있습니다.", subscription.getSubscriptionId());
                    continue;
                }

                // 청구서 생성
                SubscriptionInvoice invoice = subscriptionService.createInvoice(subscription.getSubscriptionId());
                logger.info("구독 ID {}: 청구서 생성 완료 (청구서 ID: {})", 
                        subscription.getSubscriptionId(), invoice.getInvoiceId());

                // 청구서 결제 처리 (비동기)
                subscriptionService.processInvoicePayment(invoice.getInvoiceId())
                        .subscribe(
                                success -> {
                                    if (success) {
                                        logger.info("구독 ID {}: 청구서 ID {} 결제 처리 완료", 
                                                subscription.getSubscriptionId(), invoice.getInvoiceId());
                                        successCount.incrementAndGet();
                                    } else {
                                        logger.warn("구독 ID {}: 청구서 ID {} 결제 처리 실패", 
                                                subscription.getSubscriptionId(), invoice.getInvoiceId());
                                        failureCount.incrementAndGet();
                                    }
                                },
                                error -> {
                                    logger.error("구독 ID {}: 청구서 ID {} 결제 처리 중 오류 발생", 
                                            subscription.getSubscriptionId(), invoice.getInvoiceId(), error);
                                    failureCount.incrementAndGet();
                                }
                        );

                // 체험 기간 종료 예정 구독 처리
                if (subscription.getTrialEnd() != null && 
                    subscription.getTrialEnd().isBefore(now.plusDays(1)) &&
                    subscription.getTrialEnd().isAfter(now.minusDays(1))) {
                    // 체험 기간이 곧 종료되는 경우 첫 결제 청구서 생성
                    logger.info("구독 ID {}: 체험 기간 종료 예정, 첫 결제 청구서 생성", subscription.getSubscriptionId());
                }

            } catch (Exception e) {
                logger.error("구독 ID {}: 청구서 생성/결제 처리 중 오류 발생", 
                        subscription.getSubscriptionId(), e);
                failureCount.incrementAndGet();
            }
        }

        logger.info("구독 청구서 생성 및 결제 처리 완료 - 성공: {}, 실패: {}", successCount.get(), failureCount.get());
    }

    /**
     * 매 시간마다 실행 (선택적)
     * 체험 기간이 종료된 구독을 ACTIVE 상태로 변경
     */
    @Scheduled(cron = "0 0 * * * ?") // 매 시간
    @Transactional
    public void updateTrialEndedSubscriptions() {
        logger.debug("체험 기간 종료 구독 상태 업데이트 시작");

        LocalDateTime now = LocalDateTime.now();
        
        // 체험 기간이 종료되었지만 아직 TRIALING 상태인 구독 조회
        List<Subscription> trialEndedSubscriptions = subscriptionRepository
                .findByStatusAndTrialEndLessThanEqual(
                        Subscription.SubscriptionStatus.TRIALING,
                        now
                );

        for (Subscription subscription : trialEndedSubscriptions) {
            try {
                subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                subscriptionRepository.save(subscription);
                logger.info("구독 ID {}: 체험 기간 종료, ACTIVE 상태로 변경", subscription.getSubscriptionId());
            } catch (Exception e) {
                logger.error("구독 ID {}: 상태 업데이트 중 오류 발생", subscription.getSubscriptionId(), e);
            }
        }

        logger.debug("체험 기간 종료 구독 상태 업데이트 완료 - 처리 수: {}", trialEndedSubscriptions.size());
    }
}

