package com.sparta.point_system.controller;

import com.sparta.point_system.dto.PaymentRequestDto;
import com.sparta.point_system.entity.Order;
import com.sparta.point_system.entity.OrderItem;
import com.sparta.point_system.entity.Payment;
import com.sparta.point_system.entity.Product;
import com.sparta.point_system.repository.OrderRepository;
import com.sparta.point_system.repository.OrderItemRepository;
import com.sparta.point_system.repository.PaymentRepository;
import com.sparta.point_system.repository.ProductRepository;
import com.sparta.point_system.service.PaymentService;
import com.sparta.point_system.service.PointService;
import com.sparta.point_system.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private PointService pointService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderItemRepository orderItemRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private com.sparta.point_system.service.MembershipService membershipService;
    
    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping("/payment")
    public Payment createPayment(@RequestParam String orderId,
                                @RequestParam(required = false) Long methodId,
                                @RequestParam(required = false) String impUid,
                                @RequestParam BigDecimal amount,
                                @RequestParam Payment.PaymentStatus status,
                                @RequestParam(required = false) String paymentMethod,
                                @RequestParam(required = false) LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setMethodId(methodId);
        payment.setImpUid(impUid);
        payment.setAmount(amount);
        payment.setStatus(status);
        payment.setPaymentMethod(paymentMethod);
        payment.setPaidAt(paidAt);
        return paymentRepository.save(payment);
    }

    @GetMapping("/payments")
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @GetMapping("/payment/{paymentId}")
    public Optional<Payment> getPaymentById(@PathVariable Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    @GetMapping("/payment/order/{orderId}")
    public Optional<Payment> getPaymentByOrderId(@PathVariable String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @GetMapping("/payments/status/{status}")
    public List<Payment> getPaymentsByStatus(@PathVariable Payment.PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }

    @PutMapping("/payment/{paymentId}")
    public Payment updatePayment(@PathVariable Long paymentId,
                                @RequestParam(required = false) BigDecimal amount,
                                @RequestParam(required = false) Payment.PaymentStatus status,
                                @RequestParam(required = false) String paymentMethod,
                                @RequestParam(required = false) LocalDateTime paidAt) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            if (amount != null) payment.setAmount(amount);
            if (status != null) payment.setStatus(status);
            if (paymentMethod != null) payment.setPaymentMethod(paymentMethod);
            if (paidAt != null) payment.setPaidAt(paidAt);
            return paymentRepository.save(payment);
        }
        throw new RuntimeException("Payment not found with id: " + paymentId);
    }

    @DeleteMapping("/payment/{paymentId}")
    public String deletePayment(@PathVariable Long paymentId) {
        paymentRepository.deleteById(paymentId);
        return "Payment deleted successfully";
    }
    
    // 결제 완료 검증 API
    @PostMapping("/complete")
    public Mono<ResponseEntity<String>> completePayment(@RequestBody Map<String, String> request) {
        String paymentId = request.get("paymentId");
        System.out.println("결제 완료 검증 요청 받음 - Payment ID: " + paymentId);
        
        return paymentService.verifyPayment(paymentId)
                .map(isSuccess -> {
                    if (isSuccess) {
                        return ResponseEntity.ok("Payment verification successful.");
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment verification failed.");
                    }
                });
    }
    
    // 통합 결제 요청 API (주문 + 결제 정보를 함께 처리, 포인트 사용 포함)
    @PostMapping("/request")
    public ResponseEntity<String> requestPayment(@RequestBody PaymentRequestDto paymentRequest) {
        try {
            // 인증된 사용자 ID 가져오기
            Long currentUserId = securityUtil.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("인증이 필요합니다.");
            }
            
            // 요청된 userId가 있으면 현재 사용자와 일치하는지 확인
            if (paymentRequest.getUserId() != null && !paymentRequest.getUserId().equals(currentUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("다른 사용자의 결제를 요청할 수 없습니다.");
            }
            
            // 1. 포인트 사용 처리
            if (paymentRequest.getPointsUsed() != null && paymentRequest.getPointsUsed() > 0) {
                try {
                    pointService.usePoints(
                        currentUserId,
                        paymentRequest.getPointsUsed(),
                        paymentRequest.getOrderId(),
                        "주문 결제 시 포인트 사용"
                    );
                } catch (RuntimeException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("포인트 사용 실패: " + e.getMessage());
                }
            }
            
            // 2. 주문 생성
            Order order = new Order();
            order.setOrderId(paymentRequest.getOrderId());
            order.setUserId(currentUserId);
            order.setTotalAmount(paymentRequest.getTotalAmount());
            order.setPointsUsed(paymentRequest.getPointsUsed() != null ? paymentRequest.getPointsUsed() : 0);
            order.setPointsDiscountAmount(paymentRequest.getPointsDiscountAmount() != null ? 
                    paymentRequest.getPointsDiscountAmount() : BigDecimal.ZERO);
            order.setStatus(Order.OrderStatus.PENDING_PAYMENT);
            
            Order savedOrder = orderRepository.save(order);
            System.out.println("주문이 생성되었습니다. Order ID: " + savedOrder.getOrderId());
            
            // 3. 주문 아이템들 저장
            if (paymentRequest.getOrderItems() != null && !paymentRequest.getOrderItems().isEmpty()) {
                for (PaymentRequestDto.OrderItemDto itemDto : paymentRequest.getOrderItems()) {
                    Optional<Product> productOptional = productRepository.findById(itemDto.getProductId());
                    if (productOptional.isEmpty()) {
                        System.err.println("상품을 찾을 수 없습니다. Product ID: " + itemDto.getProductId());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("상품을 찾을 수 없습니다. Product ID: " + itemDto.getProductId());
                    }
                    
                    OrderItem orderItem = new OrderItem();
                    orderItem.setOrderId(savedOrder.getOrderId());
                    orderItem.setProductId(itemDto.getProductId());
                    orderItem.setQuantity(itemDto.getQuantity());
                    orderItem.setPrice(itemDto.getPrice());
                    
                    orderItemRepository.save(orderItem);
                }
                System.out.println("주문 아이템 " + paymentRequest.getOrderItems().size() + "개가 저장되었습니다.");
            }
            
            return ResponseEntity.ok("Payment request processed successfully. Order ID: " + savedOrder.getOrderId());
            
        } catch (Exception e) {
            System.err.println("결제 요청 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Payment request processing failed: " + e.getMessage());
        }
    }
    
    // 포인트 전액 결제 완료 처리 API
    @PostMapping("/complete-point-payment")
    public ResponseEntity<Map<String, Object>> completePointPayment(@RequestBody Map<String, String> request) {
        try {
            // 인증된 사용자 ID 가져오기
            Long currentUserId = securityUtil.getCurrentUserId();
            if (currentUserId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "인증이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            
            String orderId = request.get("orderId");
            if (orderId == null || orderId.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "orderId는 필수입니다.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            Optional<Order> orderOptional = orderRepository.findByOrderId(orderId);
            if (orderOptional.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "주문을 찾을 수 없습니다. Order ID: " + orderId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            
            Order order = orderOptional.get();
            
            // 주문 소유자 확인
            if (!order.getUserId().equals(currentUserId)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "다른 사용자의 주문을 처리할 수 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            
            // 주문 상태를 COMPLETED로 변경
            order.setStatus(Order.OrderStatus.COMPLETED);
            orderRepository.save(order);
            
            // Payment 레코드 생성 (포인트 전액 결제)
            Payment payment = new Payment();
            payment.setOrderId(orderId);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(Payment.PaymentStatus.PAID);
            payment.setPaymentMethod("POINT");
            payment.setPaidAt(java.time.LocalDateTime.now());
            paymentRepository.save(payment);
            
            // 포인트 적립 처리 (멤버십 등급에 따른 차등 적립)
            Long userId = order.getUserId();
            Integer pointsEarned = membershipService.calculateEarnedPoints(userId, order.getTotalAmount());
            if (pointsEarned > 0) {
                pointService.earnPoints(
                    userId,
                    pointsEarned,
                    orderId,
                    "포인트 전액 결제 완료로 인한 포인트 적립 (멤버십 등급 반영)",
                    java.time.LocalDateTime.now().plusYears(1)
                );
            }
            
            // 멤버십 등급 자동 업데이트
            try {
                membershipService.updateMembershipLevel(userId);
                System.out.println("멤버십 등급이 자동 업데이트되었습니다. User ID: " + userId);
            } catch (Exception e) {
                System.err.println("멤버십 등급 업데이트 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "포인트 전액 결제가 완료되었습니다.");
            response.put("orderId", orderId);
            response.put("pointsEarned", pointsEarned);
            response.put("paymentId", payment.getPaymentId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("포인트 전액 결제 완료 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", "포인트 전액 결제 완료 처리 중 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    // PAID 상태의 결제 목록 조회 (환불 가능한 결제들) - 현재 사용자의 결제만 조회
    @GetMapping("/paid")
    public ResponseEntity<List<Payment>> getPaidPayments() {
        try {
            // 인증된 사용자 ID 가져오기
            Long currentUserId = securityUtil.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // 현재 사용자의 주문에 대한 결제만 조회
            List<Order> userOrders = orderRepository.findByUserId(currentUserId);
            List<String> userOrderIds = userOrders.stream()
                    .map(Order::getOrderId)
                    .toList();
            
            List<Payment> paidPayments = paymentRepository.findByStatus(Payment.PaymentStatus.PAID)
                    .stream()
                    .filter(payment -> userOrderIds.contains(payment.getOrderId()))
                    .toList();
            
            return ResponseEntity.ok(paidPayments);
        } catch (Exception e) {
            System.err.println("PAID 결제 목록 조회 오류: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // 결제 취소 API (PortOne imp_uid 사용, 포인트 환불 포함)
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelPayment(@RequestBody Map<String, String> request) {
        try {
            // 인증된 사용자 ID 가져오기
            Long currentUserId = securityUtil.getCurrentUserId();
            System.out.println("결제 취소 요청 - 현재 사용자 ID: " + currentUserId);
            
            if (currentUserId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "인증이 필요합니다.");
                System.out.println("결제 취소 실패: 인증되지 않은 사용자");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            
            String paymentId = request.get("paymentId"); // PortOne의 imp_uid (문자열)
            String reason = request.getOrDefault("reason", "고객 요청에 의한 취소");
            
            System.out.println("결제 취소 요청 받음 - Payment ID (imp_uid): " + paymentId);
            System.out.println("결제 취소 요청 받음 - User ID: " + currentUserId);
            
            if (paymentId == null || paymentId.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "paymentId는 필수입니다.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // DB에서 Payment 조회 (imp_uid로)
            Optional<Payment> paymentOptional = paymentRepository.findByImpUid(paymentId);
            if (paymentOptional.isEmpty()) {
                // imp_uid로 찾지 못하면 orderId로 시도 (결제 ID가 orderId와 같은 경우)
                Optional<Payment> paymentByOrderId = paymentRepository.findByOrderId(paymentId);
                if (paymentByOrderId.isPresent()) {
                    paymentOptional = paymentByOrderId;
                }
            }
            
            if (paymentOptional.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "결제 정보를 찾을 수 없습니다. Payment ID: " + paymentId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            
            Payment payment = paymentOptional.get();
            
            // 주문 소유자 확인
            Optional<Order> orderOptional = orderRepository.findByOrderId(payment.getOrderId());
            if (orderOptional.isPresent()) {
                Order order = orderOptional.get();
                if (!order.getUserId().equals(currentUserId)) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "다른 사용자의 결제를 취소할 수 없습니다.");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
                }
            } else {
                // 주문을 찾을 수 없는 경우에도 결제 정보가 있으면 진행
                // (주문이 삭제되었거나 없는 경우를 대비)
                System.out.println("경고: 주문 정보를 찾을 수 없습니다. Order ID: " + payment.getOrderId() + ", 하지만 결제 취소는 진행합니다.");
            }
            
            // 환불 가능 상태 확인
            if (payment.getStatus() != Payment.PaymentStatus.PAID && 
                payment.getStatus() != Payment.PaymentStatus.PARTIALLY_REFUNDED) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "환불할 수 없는 결제 상태입니다. 현재 상태: " + payment.getStatus());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // PortOne API로 환불 요청 (포인트 환불 로직은 PaymentService.cancelPayment 내부에서 처리됨)
            Boolean isSuccess = paymentService.cancelPayment(paymentId, reason).block();
            
            Map<String, Object> response = new HashMap<>();
            if (isSuccess != null && isSuccess) {
                response.put("message", "결제 취소가 성공적으로 처리되었습니다. 사용한 포인트도 복구되었습니다.");
                response.put("paymentId", paymentId);
                response.put("impUid", payment.getImpUid() != null ? payment.getImpUid() : paymentId);
                response.put("orderId", payment.getOrderId());
                response.put("refundAmount", payment.getAmount());
                System.out.println("결제 취소 성공 응답 반환: " + response);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "PortOne 결제 취소 요청이 실패했습니다.");
                System.out.println("결제 취소 실패 응답 반환: " + response);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            System.err.println("결제 취소 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", "결제 취소 처리 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

