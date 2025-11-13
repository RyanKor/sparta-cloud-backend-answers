package com.sparta.point_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PointTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "order_id", length = 255)
    private String orderId;
    
    @Column(name = "points", nullable = false)
    private Integer points;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TransactionType type;
    
    @Column(name = "description", length = 255)
    private String description;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    public enum TransactionType {
        EARNED, SPENT, EXPIRED, ADJUSTMENT
    }
    
    public PointTransaction(Long userId, String orderId, Integer points, TransactionType type, String description, LocalDateTime expiresAt) {
        this.userId = userId;
        this.orderId = orderId;
        this.points = points;
        this.type = type;
        this.description = description;
        this.expiresAt = expiresAt;
    }
}

