package com.sparta.subscription_system.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreatePlanRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private String billingInterval;
    private Integer trialPeriodDays;
}


