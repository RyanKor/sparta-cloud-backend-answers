package com.sparta.subscription_system.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubscriptionRequest {
    private Long planId;
    private Long paymentMethodId;
}


