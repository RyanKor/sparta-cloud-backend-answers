package com.sparta.subscription_system.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentMethodRequest {
    private String customerUid;
    private String billingKey; // PortOne 빌링키
    private String cardBrand;
    private String last4;
    private Boolean isDefault;
}


