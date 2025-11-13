package com.sparta.point_system.repository;

import com.sparta.point_system.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByUserId(Long userId);
    
    List<PointTransaction> findByUserIdAndType(Long userId, PointTransaction.TransactionType type);
    
    List<PointTransaction> findByOrderId(String orderId);
    
    List<PointTransaction> findByExpiresAtBeforeAndType(LocalDateTime now, PointTransaction.TransactionType type);
}

