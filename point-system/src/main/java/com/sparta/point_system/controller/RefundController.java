package com.sparta.point_system.controller;

import com.sparta.point_system.entity.Refund;
import com.sparta.point_system.entity.Payment;
import com.sparta.point_system.entity.Order;
import com.sparta.point_system.repository.RefundRepository;
import com.sparta.point_system.repository.PaymentRepository;
import com.sparta.point_system.repository.OrderRepository;
import com.sparta.point_system.service.MembershipService;
import com.sparta.point_system.service.PaymentService;
import com.sparta.point_system.service.PointService;
import com.sparta.point_system.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/refunds")
@CrossOrigin(origins = "*")
public class RefundController {

    @Autowired
    private RefundRepository refundRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private PointService pointService;
    
    @Autowired
    private MembershipService membershipService;
    
    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping("/refund")
    public Refund createRefund(@RequestParam Long paymentId,
                              @RequestParam BigDecimal amount,
                              @RequestParam(required = false) String reason,
                              @RequestParam Refund.RefundStatus status) {
        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(status);
        return refundRepository.save(refund);
    }

    @GetMapping("/refunds")
    public List<Refund> getAllRefunds() {
        return refundRepository.findAll();
    }

    @GetMapping("/refund/{refundId}")
    public Optional<Refund> getRefundById(@PathVariable Long refundId) {
        return refundRepository.findById(refundId);
    }

    @GetMapping("/refunds/payment/{paymentId}")
    public List<Refund> getRefundsByPaymentId(@PathVariable Long paymentId) {
        return refundRepository.findByPaymentId(paymentId);
    }

    @PutMapping("/refund/{refundId}")
    public Refund updateRefund(@PathVariable Long refundId,
                              @RequestParam(required = false) BigDecimal amount,
                              @RequestParam(required = false) String reason,
                              @RequestParam(required = false) Refund.RefundStatus status) {
        Optional<Refund> refundOpt = refundRepository.findById(refundId);
        if (refundOpt.isPresent()) {
            Refund refund = refundOpt.get();
            if (amount != null) refund.setAmount(amount);
            if (reason != null) refund.setReason(reason);
            if (status != null) refund.setStatus(status);
            return refundRepository.save(refund);
        }
        throw new RuntimeException("Refund not found with id: " + refundId);
    }

    @DeleteMapping("/refund/{refundId}")
    public String deleteRefund(@PathVariable Long refundId) {
        refundRepository.deleteById(refundId);
        return "Refund deleted successfully";
    }

    /**
     * 환불 요청 API (포인트 환불 포함)
     * 결제 취소 시 사용한 포인트를 자동으로 복구하고, 적립된 포인트도 취소합니다.
     */
    @PostMapping("/request")
    public Mono<ResponseEntity<Map<String, Object>>> requestRefund(@RequestBody Map<String, Object> refundRequest) {
        try {
            // 인증된 사용자 ID 가져오기
            Long currentUserId = securityUtil.getCurrentUserId();
            if (currentUserId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "인증이 필요합니다.");
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error));
            }
            
            System.out.println("환불 요청 받음: " + refundRequest);
            
            Long paymentId = null;
            if (refundRequest.get("paymentId") instanceof Number) {
                paymentId = ((Number) refundRequest.get("paymentId")).longValue();
            } else if (refundRequest.get("paymentId") instanceof String) {
                paymentId = Long.parseLong((String) refundRequest.get("paymentId"));
            }
            
            if (paymentId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "paymentId는 필수입니다.");
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
            }
            
            // 1. 결제 정보 조회 및 검증
            Optional<Payment> paymentOptional = paymentRepository.findById(paymentId);
            if (paymentOptional.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "결제 정보를 찾을 수 없습니다. Payment ID: " + paymentId);
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
            }
            
            Payment payment = paymentOptional.get();
            
            // 주문 소유자 확인
            Optional<Order> orderOptional = orderRepository.findByOrderId(payment.getOrderId());
            if (orderOptional.isPresent()) {
                Order order = orderOptional.get();
                if (!order.getUserId().equals(currentUserId)) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "다른 사용자의 결제를 환불할 수 없습니다.");
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error));
                }
            }
            
            // 2. 환불 가능 상태 확인
            if (payment.getStatus() != Payment.PaymentStatus.PAID && 
                payment.getStatus() != Payment.PaymentStatus.PARTIALLY_REFUNDED) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "환불할 수 없는 결제 상태입니다. 현재 상태: " + payment.getStatus());
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
            }
            
            // 3. 환불 금액 설정
            BigDecimal refundAmount = payment.getAmount();
            if (refundRequest.get("amount") != null) {
                Object amountObj = refundRequest.get("amount");
                if (amountObj instanceof Number) {
                    refundAmount = BigDecimal.valueOf(((Number) amountObj).doubleValue());
                } else if (amountObj instanceof String) {
                    refundAmount = new BigDecimal((String) amountObj);
                }
            }
            
            // 4. 환불 사유
            String reason = refundRequest.get("reason") != null ? 
                    (String) refundRequest.get("reason") : "사용자 요청에 의한 환불";
            
            // 람다 표현식에서 사용하기 위해 final 변수로 복사
            final Long finalPaymentId = paymentId;
            final BigDecimal finalRefundAmount = refundAmount;
            
            // 5. PortOne API로 환불 요청 (포인트 복구 로직은 PaymentService.cancelPayment 내부에서 처리됨)
            if (payment.getImpUid() == null || payment.getImpUid().isEmpty()) {
                // PortOne 결제가 아닌 경우 (포인트 전액 결제 등)
                // 포인트만 복구 처리
                try {
                    String orderId = payment.getOrderId();
                    
                    // 주문 정보 조회 (이미 위에서 조회한 orderOptional 재사용)
                    if (orderOptional.isPresent()) {
                        Order order = orderOptional.get();
                        Long userId = order.getUserId();
                        
                        System.out.println("=== 포인트 환불 처리 시작 (PortOne 미사용) ===");
                        System.out.println("Order ID: " + orderId);
                        System.out.println("User ID: " + userId);
                        System.out.println("Points Used (from Order): " + order.getPointsUsed());
                        
                        // 1. 사용한 포인트 복구 - point_transactions에서 직접 확인
                        try {
                            // 해당 주문으로 사용된 포인트 거래 내역 찾기 (SPENT 타입)
                            List<com.sparta.point_system.entity.PointTransaction> orderTransactions = 
                                pointService.getPointTransactionsByOrderId(orderId);
                            
                            // 해당 주문으로 사용된 포인트 찾기 (음수 값들의 절댓값 합계)
                            Integer spentPointsForOrder = orderTransactions.stream()
                                .filter(t -> t.getType() == com.sparta.point_system.entity.PointTransaction.TransactionType.SPENT
                                        && t.getPoints() < 0) // SPENT는 음수로 저장됨
                                .mapToInt(t -> Math.abs(t.getPoints())) // 절댓값으로 변환
                                .sum();
                            
                            if (spentPointsForOrder > 0) {
                                System.out.println("포인트 환불 시작 - 사용한 포인트: " + spentPointsForOrder + " (point_transactions에서 확인)");
                                pointService.refundPoints(
                                    userId,
                                    spentPointsForOrder,
                                    orderId,
                                    "주문 취소로 인한 포인트 환불 (사용한 포인트 복구)"
                                );
                                System.out.println("포인트 환불 완료: " + spentPointsForOrder + " 포인트 복구됨");
                            } else {
                                // point_transactions에서 찾지 못한 경우, Order 테이블의 pointsUsed 값 사용
                                if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
                                    System.out.println("포인트 환불 시작 - 사용한 포인트: " + order.getPointsUsed() + " (Order 테이블에서 확인)");
                                    pointService.refundPoints(
                                        userId,
                                        order.getPointsUsed(),
                                        orderId,
                                        "주문 취소로 인한 포인트 환불 (사용한 포인트 복구)"
                                    );
                                    System.out.println("포인트 환불 완료: " + order.getPointsUsed() + " 포인트 복구됨");
                                } else {
                                    System.out.println("포인트 사용 내역이 없습니다. (Order: " + order.getPointsUsed() + ", Transactions: " + spentPointsForOrder + ")");
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("포인트 환불 중 오류 발생: " + e.getMessage());
                            e.printStackTrace();
                        }
                        
                        // 2. 적립된 포인트 취소
                        Integer pointsEarned = calculatePointsEarned(order.getTotalAmount().intValue(), userId);
                        if (pointsEarned > 0) {
                            try {
                                // 해당 주문으로 적립된 포인트 거래 내역 찾기
                                List<com.sparta.point_system.entity.PointTransaction> transactions = 
                                    pointService.getPointTransactions(userId);
                                
                                // 해당 주문으로 적립된 포인트 찾기
                                Integer earnedPointsForOrder = transactions.stream()
                                    .filter(t -> orderId.equals(t.getOrderId()) 
                                            && t.getType() == com.sparta.point_system.entity.PointTransaction.TransactionType.EARNED
                                            && t.getPoints() > 0)
                                    .mapToInt(com.sparta.point_system.entity.PointTransaction::getPoints)
                                    .sum();
                                
                                if (earnedPointsForOrder > 0) {
                                    pointService.cancelEarnedPoints(
                                        userId,
                                        earnedPointsForOrder,
                                        orderId,
                                        "주문 취소로 인한 포인트 적립 취소"
                                    );
                                    System.out.println("포인트 적립 취소 완료: " + earnedPointsForOrder + " 포인트 차감됨");
                                }
                            } catch (Exception e) {
                                System.err.println("포인트 적립 취소 중 오류 발생: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        
                        // 3. 주문 상태를 CANCELLED로 변경
                        order.setStatus(Order.OrderStatus.CANCELLED);
                        orderRepository.save(order);
                        System.out.println("주문 상태가 CANCELLED로 변경되었습니다. Order ID: " + orderId);
                        
                        // 4. 멤버십 등급 자동 업데이트 (총 결제 금액이 줄어들었으므로 재계산)
                        try {
                            membershipService.updateMembershipLevel(userId);
                            System.out.println("멤버십 등급이 자동 업데이트되었습니다. User ID: " + userId);
                        } catch (Exception e) {
                            System.err.println("멤버십 등급 업데이트 중 오류 발생: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.err.println("[경고] 주문 정보를 찾을 수 없습니다. Order ID: " + orderId);
                    }
                    
                    // 결제 상태 업데이트
                    payment.setStatus(Payment.PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                    
                    // 환불 레코드 생성
                    Refund refund = new Refund();
                    refund.setPaymentId(payment.getPaymentId());
                    refund.setAmount(refundAmount);
                    refund.setReason(reason);
                    refund.setStatus(Refund.RefundStatus.COMPLETED);
                    refundRepository.save(refund);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "포인트 환불이 완료되었습니다. 사용한 포인트가 복구되었습니다.");
                    response.put("paymentId", paymentId);
                    response.put("refundAmount", refundAmount);
                    return Mono.just(ResponseEntity.ok(response));
                } catch (Exception e) {
                    System.err.println("포인트 환불 처리 중 오류: " + e.getMessage());
                    e.printStackTrace();
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "환불 처리 중 오류: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                }
            }
            
            return paymentService.cancelPayment(payment.getImpUid(), reason)
                    .map(isSuccess -> {
                        Map<String, Object> response = new HashMap<>();
                        if (isSuccess) {
                            // 멤버십 등급 자동 업데이트 (PaymentService.updateDatabaseAfterCancel에서 이미 처리됨)
                            // 하지만 추가로 확인하기 위해 주문 정보를 조회하여 업데이트
                            try {
                                String orderId = payment.getOrderId();
                                Optional<Order> refundOrderOptional = orderRepository.findByOrderId(orderId);
                                if (refundOrderOptional.isPresent()) {
                                    Long userId = refundOrderOptional.get().getUserId();
                                    membershipService.updateMembershipLevel(userId);
                                    System.out.println("환불 후 멤버십 등급이 자동 업데이트되었습니다. User ID: " + userId);
                                }
                            } catch (Exception e) {
                                System.err.println("환불 후 멤버십 등급 업데이트 중 오류 발생: " + e.getMessage());
                                e.printStackTrace();
                            }
                            
                            response.put("message", "환불이 성공적으로 처리되었습니다. 사용한 포인트도 복구되었습니다.");
                            response.put("paymentId", finalPaymentId);
                            response.put("refundAmount", finalRefundAmount);
                            return ResponseEntity.ok(response);
                        } else {
                            response.put("error", "PortOne 환불 요청이 실패했습니다.");
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                        }
                    })
                    .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "환불 처리 중 오류가 발생했습니다.")));
                            
        } catch (Exception e) {
            System.err.println("환불 요청 처리 중 오류: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", "환불 요청 처리 중 오류가 발생했습니다: " + e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
        }
    }
    
    /**
     * 포인트 적립 금액 계산 (멤버십 등급에 따라 다를 수 있음)
     * 현재는 주문 금액의 1%로 고정
     */
    private Integer calculatePointsEarned(Integer orderAmount, Long userId) {
        // TODO: 멤버십 등급에 따른 적립률 적용
        return (int) (orderAmount * 0.01);
    }
}

