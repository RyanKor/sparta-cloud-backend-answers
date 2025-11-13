package com.sparta.point_system.controller;

import com.sparta.point_system.entity.PointTransaction;
import com.sparta.point_system.service.PointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
@CrossOrigin(origins = "*")
public class PointController {

    @Autowired
    private PointService pointService;

    /**
     * 사용자의 포인트 잔액 조회
     */
    @GetMapping("/balance/{userId}")
    public ResponseEntity<Map<String, Object>> getPointBalance(@PathVariable Long userId) {
        try {
            Integer balance = pointService.getPointBalance(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("balance", balance);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 포인트 충전
     */
    @PostMapping("/charge/{userId}")
    public ResponseEntity<Map<String, Object>> chargePoints(@PathVariable Long userId,
                                                           @RequestParam(required = false, defaultValue = "100000") Integer points,
                                                           @RequestParam(required = false) String description) {
        try {
            pointService.chargePoints(userId, points, description != null ? description : "테스트용 포인트 충전");
            Integer newBalance = pointService.getPointBalance(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("chargedPoints", points);
            response.put("newBalance", newBalance);
            response.put("message", "포인트가 충전되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 사용자의 포인트 거래 내역 조회
     */
    @GetMapping("/transactions/{userId}")
    public ResponseEntity<List<PointTransaction>> getPointTransactions(@PathVariable Long userId) {
        try {
            List<PointTransaction> transactions = pointService.getPointTransactions(userId);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

