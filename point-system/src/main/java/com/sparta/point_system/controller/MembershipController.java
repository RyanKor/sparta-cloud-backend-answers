package com.sparta.point_system.controller;

import com.sparta.point_system.entity.Membership;
import com.sparta.point_system.entity.MembershipLevel;
import com.sparta.point_system.entity.Order;
import com.sparta.point_system.entity.Payment;
import com.sparta.point_system.repository.MembershipRepository;
import com.sparta.point_system.repository.OrderRepository;
import com.sparta.point_system.repository.PaymentRepository;
import com.sparta.point_system.service.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MembershipController {

    @Autowired
    private MembershipRepository membershipRepository;
    
    @Autowired
    private MembershipService membershipService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;

    @PostMapping("/membership")
    public Membership createMembership(@RequestParam Long userId,
                                      @RequestParam Long levelId,
                                      @RequestParam(required = false) LocalDateTime expiresAt) {
        Membership membership = new Membership(userId, levelId, expiresAt);
        return membershipRepository.save(membership);
    }

    @GetMapping("/memberships")
    public List<Membership> getAllMemberships() {
        return membershipRepository.findAll();
    }

    @GetMapping("/membership/{membershipId}")
    public Optional<Membership> getMembershipById(@PathVariable Long membershipId) {
        return membershipRepository.findById(membershipId);
    }

    @GetMapping("/membership/user/{userId}")
    public Optional<Membership> getMembershipByUserId(@PathVariable Long userId) {
        return membershipRepository.findByUserId(userId);
    }
    
    /**
     * 사용자의 멤버십 정보와 등급, 총 결제 금액을 함께 조회
     */
    @GetMapping("/membership/user/{userId}/info")
    public ResponseEntity<Map<String, Object>> getMembershipInfo(@PathVariable Long userId) {
        try {
            MembershipService.MembershipWithLevel membershipWithLevel = 
                membershipService.getMembershipWithLevel(userId);
            
            // 엔티티를 직접 반환하지 않고 필요한 필드만 추출
            Membership membership = membershipWithLevel.getMembership();
            MembershipLevel level = membershipWithLevel.getLevel();
            
            Map<String, Object> response = new HashMap<>();
            
            // Membership 정보
            Map<String, Object> membershipMap = new HashMap<>();
            membershipMap.put("membershipId", membership.getMembershipId());
            membershipMap.put("userId", membership.getUserId());
            membershipMap.put("levelId", membership.getLevelId());
            membershipMap.put("joinedAt", membership.getJoinedAt());
            membershipMap.put("expiresAt", membership.getExpiresAt());
            response.put("membership", membershipMap);
            
            // MembershipLevel 정보 (프록시 문제 방지)
            Map<String, Object> levelMap = new HashMap<>();
            levelMap.put("levelId", level.getLevelId());
            levelMap.put("name", level.getName());
            levelMap.put("pointAccrualRate", level.getPointAccrualRate());
            levelMap.put("benefitsDescription", level.getBenefitsDescription());
            response.put("level", levelMap);
            
            response.put("totalPaymentAmount", membershipWithLevel.getTotalPaymentAmount());
            response.put("pointAccrualRate", level.getPointAccrualRate());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("멤버십 정보 조회 오류: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 사용자의 결제 내역 조회 (주문 + 결제 정보)
     */
    @GetMapping("/membership/user/{userId}/payments")
    public ResponseEntity<Map<String, Object>> getUserPaymentHistory(@PathVariable Long userId) {
        try {
            // 완료된 주문 조회
            List<Order> completedOrders = orderRepository.findByUserIdAndStatus(userId, Order.OrderStatus.COMPLETED);
            
            // 주문 ID 목록
            List<String> orderIds = completedOrders.stream()
                .map(Order::getOrderId)
                .collect(Collectors.toList());
            
            // PAID 상태의 결제 조회
            List<Payment> paidPayments = paymentRepository.findByOrderIdInAndStatus(orderIds, Payment.PaymentStatus.PAID);
            
            // 취소된 주문 조회
            List<Order> cancelledOrders = orderRepository.findByUserIdAndStatus(userId, Order.OrderStatus.CANCELLED);
            
            // 총 결제 금액 계산
            BigDecimal totalPaidAmount = membershipService.calculateTotalPaidAmount(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("completedOrders", completedOrders);
            response.put("paidPayments", paidPayments);
            response.put("cancelledOrders", cancelledOrders);
            response.put("totalPaidAmount", totalPaidAmount);
            response.put("totalOrderCount", completedOrders.size());
            response.put("cancelledOrderCount", cancelledOrders.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PutMapping("/membership/{membershipId}")
    public Membership updateMembership(@PathVariable Long membershipId,
                                      @RequestParam(required = false) Long levelId,
                                      @RequestParam(required = false) LocalDateTime expiresAt) {
        Optional<Membership> membershipOpt = membershipRepository.findById(membershipId);
        if (membershipOpt.isPresent()) {
            Membership membership = membershipOpt.get();
            if (levelId != null) membership.setLevelId(levelId);
            if (expiresAt != null) membership.setExpiresAt(expiresAt);
            return membershipRepository.save(membership);
        }
        throw new RuntimeException("Membership not found with id: " + membershipId);
    }

    @DeleteMapping("/membership/{membershipId}")
    public String deleteMembership(@PathVariable Long membershipId) {
        membershipRepository.deleteById(membershipId);
        return "Membership deleted successfully";
    }
}

