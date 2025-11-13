package com.sparta.subscription_system.repository;

import com.sparta.subscription_system.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserUserId(Long userId);
    
    Optional<PaymentMethod> findByCustomerUid(String customerUid);
    
    Optional<PaymentMethod> findByUserUserIdAndIsDefaultTrue(Long userId);
}


