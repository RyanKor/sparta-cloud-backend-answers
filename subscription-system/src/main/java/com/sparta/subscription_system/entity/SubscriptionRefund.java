package com.sparta.subscription_system.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_refunds")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonBackReference
    private SubscriptionInvoice invoice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RefundStatus status;

    @CreationTimestamp
    @Column(name = "refunded_at", nullable = false, updatable = false)
    private LocalDateTime refundedAt;

    public enum RefundStatus {
        PENDING, COMPLETED, FAILED
    }

    public SubscriptionRefund(SubscriptionInvoice invoice, BigDecimal amount, String reason, RefundStatus status) {
        this.invoice = invoice;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
    }
}


