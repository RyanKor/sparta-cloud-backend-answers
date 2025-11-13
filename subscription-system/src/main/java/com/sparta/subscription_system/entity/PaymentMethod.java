package com.sparta.subscription_system.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "method_id")
    private Long methodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    @Column(name = "customer_uid", nullable = false, unique = true, length = 255)
    private String customerUid; // PortOne 정기결제용 customerUid

    @Column(name = "billing_key", length = 255)
    private String billingKey; // PortOne 빌링키 (예약 결제 스케줄 생성 시 사용)

    @Column(name = "card_brand", length = 50)
    private String cardBrand; // e.g., "Visa", "Mastercard"

    @Column(name = "last4", length = 4)
    private String last4; // e.g., "4242"

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "paymentMethod", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Subscription> subscriptions;

    public PaymentMethod(User user, String customerUid, String cardBrand, String last4, Boolean isDefault) {
        this.user = user;
        this.customerUid = customerUid;
        this.cardBrand = cardBrand;
        this.last4 = last4;
        this.isDefault = isDefault;
    }
}

