package com.sparta.subscription_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "billing_interval", nullable = false, length = 50)
    private String billingInterval; // e.g., "monthly", "yearly"

    @Column(name = "trial_period_days", nullable = false)
    private Integer trialPeriodDays = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PlanStatus status = PlanStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PlanStatus {
        ACTIVE, INACTIVE
    }

    public Plan(String name, String description, BigDecimal price, String billingInterval, Integer trialPeriodDays) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.billingInterval = billingInterval;
        this.trialPeriodDays = trialPeriodDays;
    }
}


