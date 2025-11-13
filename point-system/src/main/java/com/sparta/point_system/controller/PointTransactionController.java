package com.sparta.point_system.controller;

import com.sparta.point_system.entity.PointTransaction;
import com.sparta.point_system.repository.PointTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class PointTransactionController {

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @PostMapping("/point-transaction")
    public PointTransaction createPointTransaction(@RequestParam Long userId,
                                                   @RequestParam(required = false) String orderId,
                                                   @RequestParam Integer points,
                                                   @RequestParam PointTransaction.TransactionType type,
                                                   @RequestParam(required = false) String description,
                                                   @RequestParam(required = false) LocalDateTime expiresAt) {
        PointTransaction transaction = new PointTransaction(userId, orderId, points, type, description, expiresAt);
        return pointTransactionRepository.save(transaction);
    }

    @GetMapping("/point-transactions")
    public List<PointTransaction> getAllPointTransactions() {
        return pointTransactionRepository.findAll();
    }

    @GetMapping("/point-transaction/{transactionId}")
    public Optional<PointTransaction> getPointTransactionById(@PathVariable Long transactionId) {
        return pointTransactionRepository.findById(transactionId);
    }

    @GetMapping("/point-transactions/user/{userId}")
    public List<PointTransaction> getPointTransactionsByUserId(@PathVariable Long userId) {
        return pointTransactionRepository.findByUserId(userId);
    }

    @GetMapping("/point-transactions/user/{userId}/type/{type}")
    public List<PointTransaction> getPointTransactionsByUserIdAndType(@PathVariable Long userId,
                                                                     @PathVariable PointTransaction.TransactionType type) {
        return pointTransactionRepository.findByUserIdAndType(userId, type);
    }

    @GetMapping("/point-transactions/order/{orderId}")
    public List<PointTransaction> getPointTransactionsByOrderId(@PathVariable String orderId) {
        return pointTransactionRepository.findByOrderId(orderId);
    }

    @DeleteMapping("/point-transaction/{transactionId}")
    public String deletePointTransaction(@PathVariable Long transactionId) {
        pointTransactionRepository.deleteById(transactionId);
        return "PointTransaction deleted successfully";
    }
}

