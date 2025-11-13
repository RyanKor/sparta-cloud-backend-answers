package com.sparta.point_system.service;

import com.sparta.point_system.client.PortOneClient;
import com.sparta.point_system.entity.Payment;
import com.sparta.point_system.entity.Refund;
import com.sparta.point_system.entity.Order;
import com.sparta.point_system.repository.PaymentRepository;
import com.sparta.point_system.repository.RefundRepository;
import com.sparta.point_system.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final PortOneClient portoneClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PointService pointService;
    private final MembershipService membershipService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PaymentService(PortOneClient portoneClient, PaymentRepository paymentRepository, 
                         RefundRepository refundRepository, OrderRepository orderRepository,
                         PointService pointService, MembershipService membershipService,
                         ObjectMapper objectMapper) {
        this.portoneClient = portoneClient;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.pointService = pointService;
        this.membershipService = membershipService;
        this.objectMapper = objectMapper;
    }

    public Mono<Boolean> verifyPayment(String paymentId) {
        return portoneClient.getAccessToken()
                .flatMap(accessToken -> portoneClient.getPaymentDetails(paymentId, accessToken))
                .map(paymentDetails -> {
                    System.out.println("결제 정보 조회 결과: " + paymentDetails);
                    
                    String status = (String) paymentDetails.get("status");
                    if (status == null || !("PAID".equalsIgnoreCase(status) || "Paid".equalsIgnoreCase(status))) {
                        System.out.println("결제 상태 오류: " + status);
                        return false;
                    }

                    Map<String, Object> amountInfo = (Map<String, Object>) paymentDetails.get("amount");
                    Integer paidAmount = 0;
                    if (amountInfo != null) {
                        Object totalObj = amountInfo.get("total");
                        if (totalObj instanceof Number) {
                            paidAmount = ((Number) totalObj).intValue();
                        } else if (totalObj instanceof String) {
                            try {
                                paidAmount = Integer.parseInt((String) totalObj);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    
                    String orderName = (String) paymentDetails.get("orderName");
                    String resolvedOrderId = resolveOrderId(paymentDetails, paymentId);
                    
                    System.out.println("결제 검증 성공!");
                    System.out.println("결제 ID: " + paymentId);
                    System.out.println("주문명: " + orderName);
                    System.out.println("주문 ID: " + resolvedOrderId);
                    System.out.println("결제 금액: " + paidAmount);
                    
                    try {
                        savePaymentToDatabase(paymentId, resolvedOrderId, paidAmount, paymentDetails);
                        System.out.println("=== 결제 정보 DB 저장 완료 ===");
                    } catch (Exception e) {
                        System.err.println("결제 정보 DB 저장 중 오류 발생: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    return true;
                })
                .onErrorReturn(false);
    }

    private String resolveOrderId(Map<String, Object> paymentDetails, String paymentId) {
        // 1) 최우선: customData에서 orderId 추출 (결제 요청 시 전달한 실제 주문 ID)
        // 이게 가장 중요함 - 결제 요청 시 생성된 주문 ID를 기준으로 통합 관리
        Object customDataObj = paymentDetails.get("customData");
        if (customDataObj != null) {
            try {
                if (customDataObj instanceof Map) {
                    // Map 형태인 경우
                    Object orderIdInMap = ((Map<?, ?>) customDataObj).get("orderId");
                    if (orderIdInMap instanceof String && !((String) orderIdInMap).isBlank()) {
                        System.out.println("customData Map에서 orderId 추출: " + orderIdInMap);
                        return (String) orderIdInMap;
                    }
                } else if (customDataObj instanceof String) {
                    // JSON 문자열인 경우 파싱
                    String customDataStr = (String) customDataObj;
                    if (!customDataStr.isBlank()) {
                        try {
                            // JSON 문자열을 Map으로 파싱
                            Map<String, Object> customDataMap = objectMapper.readValue(customDataStr, Map.class);
                            Object orderIdInMap = customDataMap.get("orderId");
                            if (orderIdInMap instanceof String && !((String) orderIdInMap).isBlank()) {
                                System.out.println("customData JSON 문자열에서 orderId 추출: " + orderIdInMap);
                                return (String) orderIdInMap;
                            }
                        } catch (Exception jsonParseException) {
                            // JSON 파싱 실패 시 간단한 문자열 파싱 시도
                            if (customDataStr.contains("orderId")) {
                                int idx = customDataStr.indexOf("orderId");
                                int colon = customDataStr.indexOf(":", idx);
                                if (colon > -1) {
                                    int startQuote = customDataStr.indexOf('"', colon);
                                    int endQuote = customDataStr.indexOf('"', startQuote + 1);
                                    if (startQuote > -1 && endQuote > startQuote) {
                                        String extracted = customDataStr.substring(startQuote + 1, endQuote);
                                        if (!extracted.isBlank()) {
                                            System.out.println("customData 문자열에서 orderId 추출: " + extracted);
                                            return extracted;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("customData 파싱 중 오류: " + e.getMessage());
            }
        }

        // 2) merchantUid, merchantPaymentId, orderId (PortOne에서 제공하는 필드)
        String[] candidateKeys = new String[]{"merchantUid", "merchantPaymentId", "orderId"};
        for (String key : candidateKeys) {
            Object value = paymentDetails.get(key);
            if (value instanceof String && !((String) value).isBlank()) {
                System.out.println("[" + key + "]에서 orderId 추출: " + value);
                return (String) value;
            }
        }

        // 3) 최후의 수단: payment.id를 사용 (경고와 함께)
        Object id = paymentDetails.get("id");
        if (id instanceof String && !((String) id).isBlank()) {
            System.err.println("[⚠️ 경고] customData에서 주문 ID를 찾을 수 없어 payment.id로 대체합니다: " + id);
            System.err.println("[⚠️ 경고] 이 경우 주문 정보가 없을 수 있으므로 주문을 자동 생성합니다.");
            return (String) id;
        }
        System.err.println("[⚠️ 경고] 주문 ID를 찾을 수 없어 결제 ID로 대체합니다: " + paymentId);
        System.err.println("[⚠️ 경고] 이 경우 주문 정보가 없을 수 있으므로 주문을 자동 생성합니다.");
        return paymentId;
    }

    @Transactional
    private void savePaymentToDatabase(String paymentId, String orderId, Integer amount, Map<String, Object> paymentDetails) {
        try {
            System.out.println("savePaymentToDatabase 메서드 호출됨");
            System.out.println("입력 파라미터 - paymentId: " + paymentId + ", orderId: " + orderId + ", amount: " + amount);
            
            Optional<Order> orderOptional = orderRepository.findByOrderId(orderId);
            if (orderOptional.isEmpty()) {
                // 주문을 찾을 수 없는 경우 - 이는 정상적인 흐름이 아님
                // customData에서 orderId를 제대로 추출했다면 주문이 있어야 함
                System.err.println("❌ [오류] 주문을 찾을 수 없습니다. Order ID: " + orderId);
                System.err.println("❌ [오류] 이는 customData에서 orderId를 제대로 추출하지 못했거나, 주문이 생성되지 않았음을 의미합니다.");
                System.err.println("❌ [오류] 주문 정보 없이 결제를 진행할 수 없으므로 자동으로 주문을 생성합니다.");
                System.err.println("⚠️ [주의] 자동 생성된 주문은 포인트 사용 정보가 없을 수 있습니다.");
                
                // 주문이 없으면 자동으로 생성 (단, 포인트 정보는 없을 수 있음)
                Order newOrder = createOrderFromPaymentDetails(orderId, amount, paymentDetails);
                if (newOrder != null) {
                    orderRepository.save(newOrder);
                    System.out.println("새 주문이 생성되었습니다. Order ID: " + orderId);
                    System.out.println("⚠️ 주의: 자동 생성된 주문은 포인트 사용 정보가 없을 수 있습니다.");
                    orderOptional = orderRepository.findByOrderId(orderId);
                } else {
                    System.err.println("주문 생성에 실패했습니다. Order ID: " + orderId);
                    return;
                }
            } else {
                // 기존 주문이 있는 경우 포인트 정보 확인
                Order existingOrder = orderOptional.get();
                System.out.println("✅ 기존 주문 발견 - Order ID: " + orderId);
                System.out.println("   - Points Used: " + existingOrder.getPointsUsed() + 
                                 ", Points Discount: " + existingOrder.getPointsDiscountAmount());
                System.out.println("   - User ID: " + existingOrder.getUserId());
                System.out.println("   - Total Amount: " + existingOrder.getTotalAmount());
            }
            
            if (orderOptional.isEmpty()) {
                System.err.println("주문을 찾을 수 없습니다. Order ID: " + orderId);
                return;
            }
            
            Order order = orderOptional.get();
            
            // 기존 결제가 있는지 확인 (imp_uid로 먼저, 없으면 order_id로)
            Optional<Payment> existingPayment = paymentRepository.findByImpUid(paymentId);
            if (existingPayment.isEmpty()) {
                existingPayment = paymentRepository.findByOrderId(orderId);
            }
            
            Payment payment;
            if (existingPayment.isPresent()) {
                // 기존 결제 업데이트
                payment = existingPayment.get();
                System.out.println("기존 결제 정보를 업데이트합니다. Payment ID: " + payment.getPaymentId());
            } else {
                // 새 결제 생성
                payment = new Payment();
                System.out.println("새 결제 정보를 생성합니다.");
            }
            
            payment.setOrderId(orderId);
            payment.setImpUid(paymentId);
            payment.setAmount(BigDecimal.valueOf(amount));
            payment.setStatus(Payment.PaymentStatus.PAID);
            
            Object payMethod = paymentDetails.get("payMethod");
            if (payMethod == null) {
                payMethod = paymentDetails.get("method");
            }
            if (payMethod instanceof String) {
                payment.setPaymentMethod((String) payMethod);
            }
            
            try {
                Object paidAtObj = paymentDetails.get("paidAt");
                if (paidAtObj instanceof String) {
                    String paidAt = (String) paidAtObj;
                    java.time.Instant instant = java.time.Instant.parse(paidAt);
                    payment.setPaidAt(LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
                }
            } catch (Exception ignored) {
            }
            
            paymentRepository.save(payment);
            System.out.println("결제 정보가 데이터베이스에 저장되었습니다.");
            
            // 주문 상태 업데이트
            order.setStatus(Order.OrderStatus.COMPLETED);
            orderRepository.save(order);
            System.out.println("주문 상태가 COMPLETED로 업데이트되었습니다. Order ID: " + orderId);
            
            // 포인트 적립 처리 (멤버십 등급에 따른 차등 적립)
            Long userId = order.getUserId();
            Integer pointsEarned = membershipService.calculateEarnedPoints(userId, order.getTotalAmount());
            if (pointsEarned > 0) {
                pointService.earnPoints(
                    userId,
                    pointsEarned,
                    orderId,
                    "주문 완료로 인한 포인트 적립 (멤버십 등급 반영)",
                    LocalDateTime.now().plusYears(1) // 1년 후 만료
                );
            }
            
            // 멤버십 등급 자동 업데이트 (총 결제 금액 기준)
            try {
                membershipService.updateMembershipLevel(userId);
                System.out.println("멤버십 등급이 자동 업데이트되었습니다. User ID: " + userId);
            } catch (Exception e) {
                System.err.println("멤버십 등급 업데이트 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }
            
        } catch (Exception e) {
            System.err.println("결제 정보 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 결제 상세 정보에서 주문을 자동 생성
     */
    private Order createOrderFromPaymentDetails(String orderId, Integer amount, Map<String, Object> paymentDetails) {
        try {
            Order order = new Order();
            order.setOrderId(orderId);
            order.setTotalAmount(BigDecimal.valueOf(amount));
            order.setStatus(Order.OrderStatus.PENDING_PAYMENT);
            order.setPointsUsed(0);
            order.setPointsDiscountAmount(BigDecimal.ZERO);
            
            // 고객 정보에서 user_id 추출 시도
            Object customerObj = paymentDetails.get("customer");
            if (customerObj instanceof Map) {
                Map<String, Object> customer = (Map<String, Object>) customerObj;
                Object customerId = customer.get("id");
                if (customerId instanceof Number) {
                    order.setUserId(((Number) customerId).longValue());
                } else if (customerId instanceof String) {
                    try {
                        order.setUserId(Long.parseLong((String) customerId));
                    } catch (NumberFormatException ignored) {
                        order.setUserId(1L); // 기본값
                    }
                } else {
                    order.setUserId(1L); // 기본값
                }
            } else {
                order.setUserId(1L); // 기본값
            }
            
            System.out.println("자동 생성된 주문 정보:");
            System.out.println("- Order ID: " + order.getOrderId());
            System.out.println("- User ID: " + order.getUserId());
            System.out.println("- Total Amount: " + order.getTotalAmount());
            System.out.println("- Status: " + order.getStatus());
            
            return order;
        } catch (Exception e) {
            System.err.println("주문 자동 생성 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    public Mono<Boolean> cancelPayment(String paymentId, String reason) {
        return portoneClient.getAccessToken()
                .flatMap(accessToken ->
                        portoneClient.getPaymentDetails(paymentId, accessToken)
                                .flatMap(paymentDetails -> {
                                    Object officialIdObj = paymentDetails.get("id");
                                    String idToCancel = (officialIdObj instanceof String && !((String) officialIdObj).isBlank())
                                            ? (String) officialIdObj
                                            : paymentId;

                                    return portoneClient.cancelPayment(idToCancel, accessToken, reason)
                                            .map(cancelResult -> {
                                                try {
                                                    updateDatabaseAfterCancel(paymentDetails, idToCancel, reason, cancelResult);
                                                } catch (Exception e) {
                                                    System.err.println("취소 후 DB 업데이트 중 오류: " + e.getMessage());
                                                    e.printStackTrace();
                                                }
                                                return true;
                                            });
                                })
                                .onErrorResume(detailError -> {
                                    return portoneClient.cancelPayment(paymentId, accessToken, reason)
                                            .map(cancelResult -> {
                                                try {
                                                    updateDatabaseAfterCancel(null, paymentId, reason, cancelResult);
                                                } catch (Exception e) {
                                                    System.err.println("취소 후 DB 업데이트 중 오류: " + e.getMessage());
                                                }
                                                return true;
                                            });
                                })
                )
                .doOnError(e -> System.err.println("결제 취소 중 오류: " + e.getMessage()))
                .onErrorReturn(false);
    }

    @Transactional
    public void updateDatabaseAfterCancel(Map<String, Object> paymentDetails,
                                         String idToCancel,
                                         String reason,
                                         Map<String, Object> cancelResult) {
        Optional<Payment> paymentOptional = paymentRepository.findByImpUid(idToCancel);
        if (paymentOptional.isEmpty() && paymentDetails != null) {
            String resolvedOrderId = resolveOrderId(paymentDetails, idToCancel);
            if (resolvedOrderId != null && !resolvedOrderId.isBlank()) {
                paymentOptional = paymentRepository.findByOrderId(resolvedOrderId);
            }
        }

        if (paymentOptional.isEmpty()) {
            System.err.println("[경고] 취소 후 DB 업데이트 실패: 결제 레코드를 찾을 수 없습니다.");
            return;
        }

        Payment payment = paymentOptional.get();
        String orderId = payment.getOrderId();

        System.out.println("=== 환불 처리 시작 ===");
        System.out.println("Payment ID: " + payment.getPaymentId());
        System.out.println("Order ID: " + orderId);
        
        // 주문 정보 조회
        Optional<Order> orderOptional = orderRepository.findByOrderId(orderId);
        Long userId = null;
        
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            userId = order.getUserId();
            System.out.println("User ID: " + userId);
            System.out.println("Points Used (from Order): " + order.getPointsUsed());
            System.out.println("Total Amount: " + order.getTotalAmount());
        } else {
            System.err.println("[경고] 주문 정보를 찾을 수 없습니다. Order ID: " + orderId);
            System.out.println("주문 정보 없이 point_transactions에서 직접 포인트 복구를 시도합니다.");
        }
        
        // 1. 사용한 포인트 복구 - point_transactions에서 직접 확인 (주문 정보와 무관하게)
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
                // userId가 없으면 첫 번째 거래에서 가져오기
                if (userId == null && !orderTransactions.isEmpty()) {
                    userId = orderTransactions.get(0).getUserId();
                    System.out.println("User ID를 point_transactions에서 추출: " + userId);
                }
                
                if (userId != null) {
                    System.out.println("포인트 환불 시작 - 사용한 포인트: " + spentPointsForOrder + " (point_transactions에서 확인)");
                    pointService.refundPoints(
                        userId,
                        spentPointsForOrder,
                        orderId,
                        "주문 취소로 인한 포인트 환불 (사용한 포인트 복구)"
                    );
                    System.out.println("포인트 환불 완료: " + spentPointsForOrder + " 포인트 복구됨");
                } else {
                    System.err.println("[오류] User ID를 찾을 수 없어 포인트 환불을 수행할 수 없습니다.");
                }
            } else {
                // point_transactions에서 찾지 못한 경우, Order 테이블의 pointsUsed 값 사용
                if (orderOptional.isPresent()) {
                    Order order = orderOptional.get();
                    if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
                        System.out.println("포인트 환불 시작 - 사용한 포인트: " + order.getPointsUsed() + " (Order 테이블에서 확인)");
                        pointService.refundPoints(
                            order.getUserId(),
                            order.getPointsUsed(),
                            orderId,
                            "주문 취소로 인한 포인트 환불 (사용한 포인트 복구)"
                        );
                        System.out.println("포인트 환불 완료: " + order.getPointsUsed() + " 포인트 복구됨");
                    } else {
                        System.out.println("포인트 사용 내역이 없습니다. (Order: " + order.getPointsUsed() + ", Transactions: " + spentPointsForOrder + ")");
                    }
                } else {
                    System.out.println("포인트 사용 내역이 없습니다. (Transactions: " + spentPointsForOrder + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("포인트 환불 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
        
            // 주문 정보가 있는 경우에만 추가 처리
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            userId = order.getUserId();

            // 2. 적립된 포인트 취소 (멤버십 등급에 따른 적립률 적용)
            Integer pointsEarned = membershipService.calculateEarnedPoints(userId, order.getTotalAmount());
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
        }

        BigDecimal refundAmount = extractRefundAmount(cancelResult);
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            refundAmount = payment.getAmount();
        }

        Payment.PaymentStatus newStatus = refundAmount.compareTo(payment.getAmount()) >= 0
                ? Payment.PaymentStatus.REFUNDED
                : Payment.PaymentStatus.PARTIALLY_REFUNDED;
        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        Refund refund = new Refund();
        refund.setPaymentId(payment.getPaymentId());
        refund.setAmount(refundAmount);
        refund.setReason(reason);
        refund.setStatus(Refund.RefundStatus.COMPLETED);
        refundRepository.save(refund);
    }

    private BigDecimal extractRefundAmount(Map<String, Object> cancelResult) {
        if (cancelResult == null) return null;
        try {
            Object canceledAmount = cancelResult.get("canceledAmount");
            if (canceledAmount == null) {
                canceledAmount = cancelResult.get("cancelAmount");
            }
            if (canceledAmount instanceof Number) {
                return BigDecimal.valueOf(((Number) canceledAmount).doubleValue());
            }
            if (canceledAmount instanceof String && !((String) canceledAmount).isBlank()) {
                return new BigDecimal((String) canceledAmount);
            }

            Object amountObj = cancelResult.get("amount");
            if (amountObj instanceof Map<?, ?> amountMap) {
                Object cancelled = amountMap.get("cancelled");
                if (cancelled == null) {
                    cancelled = amountMap.get("canceled");
                }
                if (cancelled instanceof Number) {
                    return BigDecimal.valueOf(((Number) cancelled).doubleValue());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

