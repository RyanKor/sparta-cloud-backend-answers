package com.sparta.subscription_system.repository;

import com.sparta.subscription_system.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserUserId(Long userId);
    
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);
    
    Optional<Subscription> findByUserUserIdAndStatus(Long userId, Subscription.SubscriptionStatus status);
    
    // 스케줄러용: 만료 예정 구독 조회
    List<Subscription> findByStatusInAndCurrentPeriodEndLessThanEqual(
            List<Subscription.SubscriptionStatus> statuses, 
            LocalDateTime currentPeriodEnd);
    
    // 스케줄러용: 체험 기간 종료 구독 조회
    List<Subscription> findByStatusAndTrialEndLessThanEqual(
            Subscription.SubscriptionStatus status, 
            LocalDateTime trialEnd);
}


