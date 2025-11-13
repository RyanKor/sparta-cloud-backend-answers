package com.sparta.subscription_system.dto;

import com.sparta.subscription_system.entity.Subscription;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SubscriptionResponse {
    private Long subscriptionId;
    private Long userId;
    private Long planId;
    private String planName;
    private Long paymentMethodId;
    private Subscription.SubscriptionStatus status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime trialEnd;
    private LocalDateTime startedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime endedAt;
}


