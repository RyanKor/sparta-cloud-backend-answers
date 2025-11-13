package com.sparta.point_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "membership_levels")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MembershipLevel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Long levelId;
    
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
    
    @Column(name = "point_accrual_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal pointAccrualRate = new BigDecimal("0.01");
    
    @Column(name = "benefits_description", columnDefinition = "TEXT")
    private String benefitsDescription;
    
    public MembershipLevel(String name, BigDecimal pointAccrualRate, String benefitsDescription) {
        this.name = name;
        this.pointAccrualRate = pointAccrualRate;
        this.benefitsDescription = benefitsDescription;
    }
}

