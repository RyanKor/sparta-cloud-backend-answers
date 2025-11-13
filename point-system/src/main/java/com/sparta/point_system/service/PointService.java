package com.sparta.point_system.service;

import com.sparta.point_system.entity.PointTransaction;
import com.sparta.point_system.repository.PointTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PointService {

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    /**
     * 사용자의 현재 포인트 잔액 조회
     */
    @Transactional(readOnly = true)
    public Integer getPointBalance(Long userId) {
        List<PointTransaction> transactions = pointTransactionRepository.findByUserId(userId);
        return transactions.stream()
                .mapToInt(PointTransaction::getPoints)
                .sum();
    }

    /**
     * 포인트 사용 (차감)
     */
    @Transactional
    public boolean usePoints(Long userId, Integer points, String orderId, String description) {
        Integer currentBalance = getPointBalance(userId);
        
        if (currentBalance < points) {
            throw new RuntimeException("포인트 잔액이 부족합니다. 현재 잔액: " + currentBalance + " 포인트");
        }

        PointTransaction transaction = new PointTransaction(
                userId,
                orderId,
                -points, // 음수로 차감
                PointTransaction.TransactionType.SPENT,
                description != null ? description : "주문 결제 시 포인트 사용",
                null
        );
        
        pointTransactionRepository.save(transaction);
        return true;
    }

    /**
     * 포인트 적립
     */
    @Transactional
    public void earnPoints(Long userId, Integer points, String orderId, String description, LocalDateTime expiresAt) {
        PointTransaction transaction = new PointTransaction(
                userId,
                orderId,
                points,
                PointTransaction.TransactionType.EARNED,
                description != null ? description : "주문 완료로 인한 포인트 적립",
                expiresAt
        );
        
        pointTransactionRepository.save(transaction);
    }

    /**
     * 포인트 충전 (ADJUSTMENT 타입)
     */
    @Transactional
    public void chargePoints(Long userId, Integer points, String description) {
        PointTransaction transaction = new PointTransaction(
                userId,
                null,
                points,
                PointTransaction.TransactionType.ADJUSTMENT,
                description != null ? description : "포인트 충전",
                null
        );
        
        pointTransactionRepository.save(transaction);
    }

    /**
     * 포인트 환불 (복구) - 주문 취소 시 사용한 포인트를 복구
     */
    @Transactional
    public void refundPoints(Long userId, Integer points, String orderId, String description) {
        if (points == null || points <= 0) {
            return; // 환불할 포인트가 없으면 처리하지 않음
        }

        PointTransaction transaction = new PointTransaction(
                userId,
                orderId,
                points, // 양수로 복구
                PointTransaction.TransactionType.ADJUSTMENT,
                description != null ? description : "주문 취소로 인한 포인트 환불",
                null
        );
        
        pointTransactionRepository.save(transaction);
    }

    /**
     * 포인트 적립 취소 - 주문 취소 시 적립된 포인트를 차감
     */
    @Transactional
    public void cancelEarnedPoints(Long userId, Integer points, String orderId, String description) {
        if (points == null || points <= 0) {
            return; // 취소할 포인트가 없으면 처리하지 않음
        }

        PointTransaction transaction = new PointTransaction(
                userId,
                orderId,
                -points, // 음수로 차감
                PointTransaction.TransactionType.ADJUSTMENT,
                description != null ? description : "주문 취소로 인한 포인트 적립 취소",
                null
        );
        
        pointTransactionRepository.save(transaction);
    }

    /**
     * 사용자의 포인트 거래 내역 조회
     */
    @Transactional(readOnly = true)
    public List<PointTransaction> getPointTransactions(Long userId) {
        return pointTransactionRepository.findByUserId(userId);
    }
    
    /**
     * 주문 ID로 포인트 거래 내역 조회
     */
    @Transactional(readOnly = true)
    public List<PointTransaction> getPointTransactionsByOrderId(String orderId) {
        return pointTransactionRepository.findByOrderId(orderId);
    }
}

