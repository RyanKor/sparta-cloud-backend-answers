package com.sparta.subscription_system.dto;

import com.sparta.subscription_system.entity.SubscriptionInvoice;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class InvoiceResponse {
    private Long invoiceId;
    private Long subscriptionId;
    private BigDecimal amount;
    private SubscriptionInvoice.InvoiceStatus status;
    private LocalDateTime dueDate;
    private LocalDateTime paidAt;
    private String impUid;
    private Integer attemptCount;
    private String errorMessage;
    private LocalDateTime createdAt;
}


