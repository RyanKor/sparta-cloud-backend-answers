package com.sparta.subscription_system.repository;

import com.sparta.subscription_system.entity.SubscriptionInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, Long> {
    List<SubscriptionInvoice> findBySubscriptionSubscriptionId(Long subscriptionId);
    
    List<SubscriptionInvoice> findByStatus(SubscriptionInvoice.InvoiceStatus status);
    
    Optional<SubscriptionInvoice> findByImpUid(String impUid);
    
    List<SubscriptionInvoice> findBySubscriptionUserUserId(Long userId);
}


