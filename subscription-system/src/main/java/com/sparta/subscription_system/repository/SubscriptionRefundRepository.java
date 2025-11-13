package com.sparta.subscription_system.repository;

import com.sparta.subscription_system.entity.SubscriptionRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRefundRepository extends JpaRepository<SubscriptionRefund, Long> {
    List<SubscriptionRefund> findByInvoiceInvoiceId(Long invoiceId);
}


