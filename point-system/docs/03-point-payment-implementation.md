# 포인트 기반 결제 기능 구현

## 목적

- Spring Boot를 활용하여 포인트 기반 결제 시스템의 백엔드 API를 구현합니다.
- 프론트엔드에서 호출할 수 있는 REST API 엔드포인트 구현를 구현합니다.

---

## 구현 내용

### 1. 포인트 잔액 조회 API

**엔드포인트**: `GET /api/points/balance/{userId}`

**주요 처리**:
- 사용자의 포인트 잔액 조회
- `PointService`를 통해 포인트 거래 내역 집계

**구현 힌트**:
```java
@RestController
@RequestMapping("/api/points")
public class PointController {
    
    @Autowired
    private PointService pointService;
    
    @GetMapping("/balance/{userId}")
    public ResponseEntity<Map<String, Object>> getPointBalance(@PathVariable Long userId) {
        Integer balance = pointService.getPointBalance(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("balance", balance);
        return ResponseEntity.ok(response);
    }
}
```

---

### 2. 포인트 충전 API

**엔드포인트**: `POST /api/points/charge/{userId}`

**주요 처리**:
- 포인트 충전 처리
- 포인트 거래 내역 기록
- 충전 후 새로운 잔액 반환

**구현 힌트**:
```java
@PostMapping("/charge/{userId}")
public ResponseEntity<Map<String, Object>> chargePoints(
        @PathVariable Long userId,
        @RequestParam(required = false, defaultValue = "100000") Integer points,
        @RequestParam(required = false) String description) {
    
    pointService.chargePoints(userId, points, description);
    Integer newBalance = pointService.getPointBalance(userId);
    
    Map<String, Object> response = new HashMap<>();
    response.put("userId", userId);
    response.put("chargedPoints", points);
    response.put("newBalance", newBalance);
    return ResponseEntity.ok(response);
}
```

---

### 3. 멤버십 정보 조회 API

**엔드포인트**: `GET /api/membership/user/{userId}/info`

**주요 처리**:
- 사용자의 멤버십 정보 조회
- 멤버십 등급 정보 조회
- 총 결제 금액 계산 및 반환

**구현 힌트**:
```java
@RestController
@RequestMapping("/api")
public class MembershipController {
    
    @Autowired
    private MembershipService membershipService;
    
    @GetMapping("/membership/user/{userId}/info")
    public ResponseEntity<Map<String, Object>> getMembershipInfo(@PathVariable Long userId) {
        MembershipService.MembershipWithLevel membershipWithLevel = 
            membershipService.getMembershipWithLevel(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("membership", membershipWithLevel.getMembership());
        response.put("level", membershipWithLevel.getLevel());
        response.put("totalPaymentAmount", membershipWithLevel.getTotalPaymentAmount());
        
        return ResponseEntity.ok(response);
    }
}
```

---

### 4. 결제 내역 조회 API

**엔드포인트**: `GET /api/membership/user/{userId}/payments`

**주요 처리**:
- 완료된 주문 목록 조회
- 결제 완료된 결제 목록 조회
- 취소된 주문 목록 조회
- 총 결제 금액 계산

**구현 힌트**:
```java
@GetMapping("/membership/user/{userId}/payments")
public ResponseEntity<Map<String, Object>> getUserPaymentHistory(@PathVariable Long userId) {
    // 완료된 주문 조회
    List<Order> completedOrders = orderRepository.findByUserIdAndStatus(
        userId, Order.OrderStatus.COMPLETED);
    
    // 취소된 주문 조회
    List<Order> cancelledOrders = orderRepository.findByUserIdAndStatus(
        userId, Order.OrderStatus.CANCELLED);
    
    // 총 결제 금액 계산
    BigDecimal totalPaidAmount = membershipService.calculateTotalPaidAmount(userId);
    
    Map<String, Object> response = new HashMap<>();
    response.put("completedOrders", completedOrders);
    response.put("cancelledOrders", cancelledOrders);
    response.put("totalPaidAmount", totalPaidAmount);
    
    return ResponseEntity.ok(response);
}
```

---

### 5. 통합 결제 요청 API

**엔드포인트**: `POST /api/payments/request`

**주요 처리**:
1. 포인트 사용 처리 (선택사항)
2. 주문 생성 및 저장
3. 주문 아이템 저장
4. 주문 상태를 `PENDING_PAYMENT`로 설정

**구현 힌트**:
```java
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    
    @Autowired
    private PointService pointService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @PostMapping("/request")
    @Transactional
    public ResponseEntity<String> requestPayment(@RequestBody PaymentRequestDto request) {
        // 1. 포인트 사용 처리
        if (request.getPointsUsed() != null && request.getPointsUsed() > 0) {
            pointService.usePoints(
                request.getUserId(),
                request.getPointsUsed(),
                request.getOrderId(),
                "주문 결제 시 포인트 사용"
            );
        }
        
        // 2. 주문 생성
        Order order = new Order();
        order.setOrderId(request.getOrderId());
        order.setUserId(request.getUserId());
        order.setTotalAmount(request.getTotalAmount());
        order.setPointsUsed(request.getPointsUsed());
        order.setStatus(Order.OrderStatus.PENDING_PAYMENT);
        orderRepository.save(order);
        
        // 3. 주문 아이템 저장
        // ...
        
        return ResponseEntity.ok("Payment request processed successfully");
    }
}
```

---

### 6. 포인트 전액 결제 완료 API

**엔드포인트**: `POST /api/payments/complete-point-payment`

**주요 처리**:
1. 주문 상태를 `COMPLETED`로 변경
2. Payment 레코드 생성 (결제 방법: POINT)
3. 멤버십 등급에 따른 포인트 적립
4. 멤버십 등급 자동 업데이트

**구현 힌트**:
```java
@PostMapping("/complete-point-payment")
@Transactional
public ResponseEntity<Map<String, Object>> completePointPayment(
        @RequestBody Map<String, String> request) {
    
    String orderId = request.get("orderId");
    Order order = orderRepository.findByOrderId(orderId)
        .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다"));
    
    // 1. 주문 상태 변경
    order.setStatus(Order.OrderStatus.COMPLETED);
    orderRepository.save(order);
    
    // 2. Payment 레코드 생성
    Payment payment = new Payment();
    payment.setOrderId(orderId);
    payment.setAmount(order.getTotalAmount());
    payment.setStatus(Payment.PaymentStatus.PAID);
    payment.setPaymentMethod("POINT");
    paymentRepository.save(payment);
    
    // 3. 포인트 적립 (멤버십 등급 반영)
    Long userId = order.getUserId();
    Integer pointsEarned = membershipService.calculateEarnedPoints(
        userId, order.getTotalAmount());
    pointService.earnPoints(userId, pointsEarned, orderId, "포인트 결제 완료", null);
    
    // 4. 멤버십 등급 업데이트
    membershipService.updateMembershipLevel(userId);
    
    Map<String, Object> response = new HashMap<>();
    response.put("pointsEarned", pointsEarned);
    return ResponseEntity.ok(response);
}
```

---

### 7. PortOne 결제 완료 검증 API

**엔드포인트**: `POST /api/payments/complete`

**주요 처리**:
1. PortOne API로 결제 정보 조회 및 검증
2. 결제 상태 확인 (PAID 여부)
3. Payment 레코드 생성 및 저장
4. 주문 상태를 `COMPLETED`로 변경
5. 멤버십 등급에 따른 포인트 적립
6. 멤버십 등급 자동 업데이트

**구현 힌트**:
```java
@PostMapping("/complete")
public Mono<ResponseEntity<String>> completePayment(@RequestBody Map<String, String> request) {
    String paymentId = request.get("paymentId");
    
    return paymentService.verifyPayment(paymentId)
        .map(isSuccess -> {
            if (isSuccess) {
                return ResponseEntity.ok("Payment verified and saved successfully");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Payment verification failed");
            }
        });
}

// PaymentService 내부
@Transactional
public void savePaymentToDatabase(String paymentId, String orderId, 
                                  Integer amount, Map<String, Object> paymentDetails) {
    // Payment 레코드 생성
    Payment payment = new Payment();
    payment.setImpUid(paymentId);
    payment.setOrderId(orderId);
    payment.setAmount(BigDecimal.valueOf(amount));
    payment.setStatus(Payment.PaymentStatus.PAID);
    paymentRepository.save(payment);
    
    // 주문 상태 변경
    Order order = orderRepository.findByOrderId(orderId).orElseThrow();
    order.setStatus(Order.OrderStatus.COMPLETED);
    orderRepository.save(order);
    
    // 포인트 적립 및 멤버십 등급 업데이트
    Long userId = order.getUserId();
    Integer pointsEarned = membershipService.calculateEarnedPoints(
        userId, BigDecimal.valueOf(amount));
    pointService.earnPoints(userId, pointsEarned, orderId, "결제 완료", null);
    membershipService.updateMembershipLevel(userId);
}
```

---

### 8. 주문 조회 API

**엔드포인트**: `GET /api/order/{orderId}`

**주요 처리**:
- 주문 ID로 주문 정보 조회
- 주문 상태, 결제 정보 반환

**구현 힌트**:
```java
@RestController
@RequestMapping("/api")
public class OrderController {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @GetMapping("/order/{orderId}")
    public ResponseEntity<Order> getOrderById(@PathVariable String orderId) {
        Optional<Order> order = orderRepository.findByOrderId(orderId);
        return order.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

---

### 9. 상품 정보 조회 API

**엔드포인트**: `GET /api/product/{productId}`

**주요 처리**:
- 상품 ID로 상품 정보 조회

**구현 힌트**:
```java
@RestController
@RequestMapping("/api")
public class ProductController {
    
    @Autowired
    private ProductRepository productRepository;
    
    @GetMapping("/product/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable Long productId) {
        Optional<Product> product = productRepository.findById(productId);
        return product.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

---

### 10. 포인트 기반 결제 환불 구현

**목적**:
사용자가 결제를 취소하거나 환불을 요청할 경우, 사용된 포인트를 복구하고 적립된 포인트를 취소하며, 총 결제 금액 변동에 따라 멤버십 등급을 자동으로 갱신하는 기능을 구현합니다. 이는 결제 시스템의 완전성과 사용자 경험을 보장하는 데 필수적입니다.

**엔드포인트**: `POST /api/refunds/request`, `POST /api/payments/cancel`

**주요 처리**:
1. 결제 상태 확인 (PAID 상태만 환불 가능)
2. 사용한 포인트 복구 (`point_transactions`에서 SPENT 타입 거래 확인)
3. 적립된 포인트 취소 (`point_transactions`에서 EARNED 타입 거래 확인)
4. 주문 상태를 `CANCELLED`로 변경
5. 결제 상태를 `REFUNDED`로 변경
6. 환불 레코드 생성
7. 멤버십 등급 자동 업데이트

**구현 힌트**:
```java
@RestController
@RequestMapping("/api/refunds")
public class RefundController {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private PointService pointService;
    
    @Autowired
    private MembershipService membershipService;
    
    @PostMapping("/request")
    public Mono<ResponseEntity<Map<String, Object>>> requestRefund(
            @RequestBody Map<String, Object> refundRequest) {
        
        Long paymentId = Long.parseLong(refundRequest.get("paymentId").toString());
        Optional<Payment> paymentOptional = paymentRepository.findById(paymentId);
        
        if (paymentOptional.isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        
        Payment payment = paymentOptional.get();
        
        // 환불 가능 상태 확인
        if (payment.getStatus() != Payment.PaymentStatus.PAID) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "환불할 수 없는 결제 상태입니다")));
        }
        
        // PortOne 결제 취소 또는 포인트 전액 결제 환불 처리
        if (payment.getImpUid() != null && !payment.getImpUid().isEmpty()) {
            // PortOne 결제 취소
            return paymentService.cancelPayment(payment.getImpUid(), reason)
                .map(isSuccess -> {
                    if (isSuccess) {
                        return ResponseEntity.ok(Map.of("message", "환불 완료"));
                    } else {
                        return ResponseEntity.badRequest()
                            .body(Map.of("error", "환불 처리 실패"));
                    }
                });
        } else {
            // 포인트 전액 결제 환불 처리
            return processPointRefund(payment);
        }
    }
}

// PaymentService 내부
@Transactional
public void updateDatabaseAfterCancel(String orderId, String reason) {
    Optional<Payment> paymentOptional = paymentRepository.findByOrderId(orderId);
    if (paymentOptional.isEmpty()) return;
    
    Payment payment = paymentOptional.get();
    Optional<Order> orderOptional = orderRepository.findByOrderId(orderId);
    
    if (orderOptional.isPresent()) {
        Order order = orderOptional.get();
        Long userId = order.getUserId();
        
        // 1. 사용한 포인트 복구
        List<PointTransaction> orderTransactions = 
            pointService.getPointTransactionsByOrderId(orderId);
        
        Integer spentPointsForOrder = orderTransactions.stream()
            .filter(t -> t.getType() == TransactionType.SPENT && t.getPoints() < 0)
            .mapToInt(t -> Math.abs(t.getPoints()))
            .sum();
        
        if (spentPointsForOrder > 0) {
            pointService.refundPoints(userId, spentPointsForOrder, orderId, 
                "주문 취소로 인한 포인트 환불");
        }
        
        // 2. 적립된 포인트 취소
        Integer pointsEarned = membershipService.calculateEarnedPoints(
            userId, order.getTotalAmount());
        
        if (pointsEarned > 0) {
            List<PointTransaction> transactions = pointService.getPointTransactions(userId);
            Integer earnedPointsForOrder = transactions.stream()
                .filter(t -> orderId.equals(t.getOrderId()) 
                        && t.getType() == TransactionType.EARNED
                        && t.getPoints() > 0)
                .mapToInt(PointTransaction::getPoints)
                .sum();
            
            if (earnedPointsForOrder > 0) {
                pointService.cancelEarnedPoints(userId, earnedPointsForOrder, orderId,
                    "주문 취소로 인한 포인트 적립 취소");
            }
        }
        
        // 3. 주문 상태 변경
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        
        // 4. 결제 상태 변경
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        
        // 5. 멤버십 등급 자동 업데이트
        membershipService.updateMembershipLevel(userId);
    }
}
```

---

### 11. 포인트 기반 멤버십 등급 관리 구현

**목적**:
사용자의 총 결제 금액에 따라 멤버십 등급(Normal, VIP, VVIP)을 자동으로 부여하고 관리하며, 각 등급에 따라 차등화된 포인트 적립률(1%, 5%, 10%)을 적용하는 시스템을 구축합니다. 이는 사용자 충성도를 높이고 차별화된 혜택을 제공하기 위함입니다.

**주요 처리**:
1. 멤버십 등급 정의 및 초기화
2. 총 결제 금액 계산
3. 등급 결정 및 업데이트
4. 포인트 적립률 적용
5. API 제공

**구현 힌트**:

#### 1. 멤버십 등급 엔티티 정의
```java
@Entity
@Table(name = "membership_levels")
public class MembershipLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long levelId;
    
    @Column(nullable = false, unique = true)
    private String name; // Normal, VIP, VVIP
    
    @Column(nullable = false)
    private BigDecimal pointAccrualRate; // 0.01, 0.05, 0.10
    
    private String benefitsDescription;
}
```

#### 2. 총 결제 금액 계산
```java
@Service
public class MembershipService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    /**
     * 사용자의 총 결제 금액 계산 (COMPLETED 주문만 집계)
     */
    public BigDecimal calculateTotalPaidAmount(Long userId) {
        List<Order> completedOrders = orderRepository.findByUserIdAndStatus(
            userId, Order.OrderStatus.COMPLETED);
        
        List<String> orderIds = completedOrders.stream()
            .map(Order::getOrderId)
            .collect(Collectors.toList());
        
        List<Payment> paidPayments = paymentRepository.findByOrderIdInAndStatus(
            orderIds, Payment.PaymentStatus.PAID);
        
        BigDecimal totalAmount = paidPayments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return totalAmount;
    }
}
```

#### 3. 등급 결정 및 업데이트
```java
/**
 * 총 결제 금액에 따른 멤버십 등급 결정
 * - 5만원 이하: Normal (1%)
 * - 10만원 이하: VIP (5%)
 * - 15만원 이상: VVIP (10%)
 */
@Transactional
public Long determineMembershipLevel(BigDecimal totalPaymentAmount) {
    // Normal: 50,000원 이하
    if (totalPaymentAmount.compareTo(new BigDecimal("50000")) <= 0) {
        MembershipLevel normalLevel = createDefaultMembershipLevel("Normal");
        return normalLevel.getLevelId();
    }
    
    // VIP: 100,000원 이하
    if (totalPaymentAmount.compareTo(new BigDecimal("100000")) <= 0) {
        MembershipLevel vipLevel = createDefaultMembershipLevel("VIP");
        return vipLevel.getLevelId();
    }
    
    // VVIP: 150,000원 이상
    MembershipLevel vvipLevel = createDefaultMembershipLevel("VVIP");
    return vvipLevel.getLevelId();
}

/**
 * 사용자의 멤버십 등급 자동 업데이트
 */
@Transactional
public Membership updateMembershipLevel(Long userId) {
    BigDecimal totalPaymentAmount = calculateTotalPaidAmount(userId);
    Long newLevelId = determineMembershipLevel(totalPaymentAmount);
    
    Optional<Membership> membershipOpt = membershipRepository.findByUserId(userId);
    Membership membership;
    
    if (membershipOpt.isPresent()) {
        membership = membershipOpt.get();
        membership.setLevelId(newLevelId);
    } else {
        membership = new Membership();
        membership.setUserId(userId);
        membership.setLevelId(newLevelId);
    }
    
    return membershipRepository.save(membership);
}

/**
 * 기본 멤버십 등급 생성 (없을 경우)
 */
@Transactional
private MembershipLevel createDefaultMembershipLevel(String levelName) {
    Optional<MembershipLevel> existingLevel = membershipLevelRepository.findByName(levelName);
    if (existingLevel.isPresent()) {
        return existingLevel.get();
    }
    
    MembershipLevel newLevel = new MembershipLevel();
    newLevel.setName(levelName);
    
    // 등급별 적립률 설정
    if ("Normal".equals(levelName)) {
        newLevel.setPointAccrualRate(new BigDecimal("0.01"));
        newLevel.setBenefitsDescription("일반 등급 - 기본 1% 포인트 적립");
    } else if ("VIP".equals(levelName)) {
        newLevel.setPointAccrualRate(new BigDecimal("0.05"));
        newLevel.setBenefitsDescription("우수 등급 - 5% 포인트 적립");
    } else if ("VVIP".equals(levelName)) {
        newLevel.setPointAccrualRate(new BigDecimal("0.10"));
        newLevel.setBenefitsDescription("최우수 등급 - 10% 포인트 적립");
    }
    
    return membershipLevelRepository.save(newLevel);
}
```

#### 4. 포인트 적립률 적용
```java
/**
 * 사용자의 멤버십 등급에 따른 포인트 적립률 조회
 */
public BigDecimal getPointAccrualRate(Long userId) {
    Membership membership = getMembership(userId);
    MembershipLevel level = membershipLevelRepository.findById(membership.getLevelId())
        .orElseThrow(() -> new RuntimeException("멤버십 등급 정보를 찾을 수 없습니다"));
    
    return level.getPointAccrualRate();
}

/**
 * 결제 금액에 멤버십 등급 적립률을 적용하여 적립 포인트 계산
 */
public Integer calculateEarnedPoints(Long userId, BigDecimal paymentAmount) {
    BigDecimal accrualRate = getPointAccrualRate(userId);
    BigDecimal earnedPoints = paymentAmount.multiply(accrualRate);
    
    // 소수점 이하 반올림 처리
    return earnedPoints.setScale(0, RoundingMode.HALF_UP).intValue();
}
```

#### 5. 결제 완료 시 멤버십 등급 업데이트 호출
```java
// PaymentService.savePaymentToDatabase 내부
@Transactional
public void savePaymentToDatabase(String paymentId, String orderId, 
                                  Integer amount, Map<String, Object> paymentDetails) {
    // ... 결제 정보 저장 ...
    
    // 포인트 적립 및 멤버십 등급 업데이트
    Long userId = order.getUserId();
    Integer pointsEarned = membershipService.calculateEarnedPoints(
        userId, BigDecimal.valueOf(amount));
    pointService.earnPoints(userId, pointsEarned, orderId, "결제 완료", null);
    
    // 멤버십 등급 자동 업데이트
    membershipService.updateMembershipLevel(userId);
}
```

#### 6. API 제공
```java
@RestController
@RequestMapping("/api")
public class MembershipController {
    
    @Autowired
    private MembershipService membershipService;
    
    /**
     * 멤버십 정보 및 등급 조회
     */
    @GetMapping("/membership/user/{userId}/info")
    public ResponseEntity<Map<String, Object>> getMembershipInfo(@PathVariable Long userId) {
        MembershipService.MembershipWithLevel membershipWithLevel = 
            membershipService.getMembershipWithLevel(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("membership", membershipWithLevel.getMembership());
        response.put("level", membershipWithLevel.getLevel());
        response.put("totalPaymentAmount", membershipWithLevel.getTotalPaymentAmount());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 사용자 결제 내역 조회
     */
    @GetMapping("/membership/user/{userId}/payments")
    public ResponseEntity<Map<String, Object>> getUserPaymentHistory(@PathVariable Long userId) {
        // 완료된 주문, 취소된 주문, 총 결제 금액 등 반환
        // ...
    }
}
```

**주요 고려사항**:
- 결제 완료 시 `membershipService.updateMembershipLevel(userId)` 자동 호출
- 결제 취소 시에도 `membershipService.updateMembershipLevel(userId)` 자동 호출하여 등급 다운그레이드 처리
- 멤버십 등급이 없을 경우 기본 등급(Normal) 자동 생성
- 포인트 적립 시 현재 멤버십 등급의 적립률 적용

---

## 12. 인증 기능 추가하기

**목적**:
Spring Security와 JWT(JSON Web Token)를 활용하여 RESTful API에 인증 및 인가 기능을 구현합니다. 사용자는 회원가입 및 로그인을 통해 JWT 토큰을 발급받고, 이후 모든 보호된 API 요청 시 이 토큰을 사용하여 인증합니다.

**구현 내용**:

### 1. 의존성 추가 (build.gradle)

```gradle
dependencies {
    // Spring Security
    implementation 'org.springframework.boot:spring-boot-starter-security'
    
    // JWT 라이브러리
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
    
    // Validation
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

### 2. JWT 유틸리티 구현

**파일**: `src/main/java/com/sparta/point_system/util/JwtUtil.java`

```java
package com.sparta.point_system.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret:your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-characters-long}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours default
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // JWT 토큰 생성
    public String generateToken(String email, Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // 토큰에서 이메일 추출
    public String getEmailFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    // 토큰에서 사용자 ID 추출
    public Long getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }

    // 토큰 유효성 검증
    public Boolean validateToken(String token) {
        try {
            getAllClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getClaimFromToken(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
}
```

**application.properties 설정**:
```properties
# JWT Configuration
jwt.secret=${JWT_SECRET:your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-characters-long}
jwt.expiration=${JWT_EXPIRATION:86400000}  # 24시간 (밀리초)
```

### 3. UserDetailsService 구현

**파일**: `src/main/java/com/sparta/point_system/security/UserDetailsServiceImpl.java`

```java
package com.sparta.point_system.security;

import com.sparta.point_system.entity.User;
import com.sparta.point_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_USER")
                .build();
    }
}
```

### 4. JWT 인증 필터 구현

**파일**: `src/main/java/com/sparta/point_system/security/JwtAuthenticationFilter.java`

```java
package com.sparta.point_system.security;

import com.sparta.point_system.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
                String email = jwtUtil.getEmailFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("JWT 인증 설정 중 오류 발생", e);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 5. SecurityConfig 설정

**파일**: `src/main/java/com/sparta/point_system/config/SecurityConfig.java`

```java
package com.sparta.point_system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.point_system.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 공개 엔드포인트
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/products/**").permitAll()
                .requestMatchers("/api/product/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/login.html", "/register.html", "/point-payment.html").permitAll()
                .requestMatchers("/portone-webhook/**").permitAll()
                // 인증 필요한 엔드포인트
                .requestMatchers("/api/payments/**").authenticated()
                .requestMatchers("/api/refunds/**").authenticated()
                .requestMatchers("/api/orders/**").authenticated()
                .requestMatchers("/api/points/**").authenticated()
                .requestMatchers("/api/membership/**").authenticated()
                // 기타 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 인증 실패 시 JSON 응답 반환 (리다이렉트 방지)
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            private final ObjectMapper objectMapper = new ObjectMapper();

            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                 AuthenticationException authException) throws IOException {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                Map<String, Object> result = new HashMap<>();
                result.put("error", "인증이 필요합니다.");
                result.put("message", authException.getMessage());
                result.put("status", 401);

                response.getWriter().write(objectMapper.writeValueAsString(result));
            }
        };
    }

    // 접근 거부 시 JSON 응답 반환
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            Map<String, Object> result = new HashMap<>();
            result.put("error", "접근이 거부되었습니다.");
            result.put("message", accessDeniedException.getMessage());
            result.put("status", 403);

            ObjectMapper objectMapper = new ObjectMapper();
            response.getWriter().write(objectMapper.writeValueAsString(result));
        };
    }

    // CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### 6. 인증 컨트롤러 구현

**파일**: `src/main/java/com/sparta/point_system/controller/AuthController.java`

**DTO 클래스들**:

```java
// LoginRequest.java
package com.sparta.point_system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}

// RegisterRequest.java
package com.sparta.point_system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    private String password;
    
    @NotBlank(message = "이름은 필수입니다.")
    private String name;
}

// AuthResponse.java
package com.sparta.point_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private Long userId;
    private String name;
    private String message;
}
```

**AuthController 구현**:

```java
package com.sparta.point_system.controller;

import com.sparta.point_system.dto.AuthResponse;
import com.sparta.point_system.dto.LoginRequest;
import com.sparta.point_system.dto.RegisterRequest;
import com.sparta.point_system.entity.User;
import com.sparta.point_system.repository.UserRepository;
import com.sparta.point_system.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    // 회원가입
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // 이메일 중복 확인
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "이미 사용 중인 이메일입니다.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            // 새 사용자 생성
            User user = new User();
            user.setEmail(request.getEmail());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setName(request.getName());

            User savedUser = userRepository.save(user);

            // JWT 토큰 생성
            String token = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getUserId());

            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setEmail(savedUser.getEmail());
            response.setUserId(savedUser.getUserId());
            response.setName(savedUser.getName());
            response.setMessage("회원가입이 완료되었습니다.");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "회원가입 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 인증 시도
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // 사용자 정보 조회
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            // JWT 토큰 생성
            String token = jwtUtil.generateToken(user.getEmail(), user.getUserId());

            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setEmail(user.getEmail());
            response.setUserId(user.getUserId());
            response.setName(user.getName());
            response.setMessage("로그인이 완료되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "로그인 실패: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    // 현재 사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "인증 토큰이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            String jwt = token.substring(7);
            if (!jwtUtil.validateToken(jwt)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "유효하지 않은 토큰입니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            String email = jwtUtil.getEmailFromToken(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", user.getUserId());
            userInfo.put("email", user.getEmail());
            userInfo.put("name", user.getName());

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "사용자 정보 조회 실패: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}
```

### 7. 보호된 API 구현 (인증된 사용자 정보 사용)

**SecurityUtil 유틸리티**:

```java
package com.sparta.point_system.util;

import com.sparta.point_system.entity.User;
import com.sparta.point_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

    @Autowired
    private UserRepository userRepository;

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return null;
    }

    public Long getCurrentUserId() {
        String email = getCurrentUserEmail();
        if (email != null) {
            return userRepository.findByEmail(email)
                    .map(User::getUserId)
                    .orElse(null);
        }
        return null;
    }

    public User getCurrentUser() {
        String email = getCurrentUserEmail();
        if (email != null) {
            return userRepository.findByEmail(email).orElse(null);
        }
        return null;
    }
}
```

**보호된 API 예시**:

```java
@RestController
@RequestMapping("/api/points")
public class PointController {
    
    @Autowired
    private PointService pointService;
    
    @Autowired
    private SecurityUtil securityUtil;
    
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getPointBalance() {
        // SecurityContext에서 현재 사용자 정보 가져오기
        Long userId = securityUtil.getCurrentUserId();
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        Integer balance = pointService.getPointBalance(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("balance", balance);
        return ResponseEntity.ok(response);
    }
}
```

### 8. API 사용 예시

**회원가입**:
```bash
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}

# 응답
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "user@example.com",
  "userId": 1,
  "name": "홍길동",
  "message": "회원가입이 완료되었습니다."
}
```

**로그인**:
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

# 응답
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "user@example.com",
  "userId": 1,
  "name": "홍길동",
  "message": "로그인이 완료되었습니다."
}
```

**보호된 API 호출**:
```bash
GET /api/points/balance
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# 응답
{
  "userId": 1,
  "balance": 100000
}
```

**현재 사용자 정보 조회**:
```bash
GET /api/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# 응답
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동"
}
```

**주요 고려사항**:

1. **JWT Secret Key**: 프로덕션 환경에서는 반드시 강력한 비밀키를 사용하고 환경 변수로 관리해야 합니다.
2. **토큰 만료 시간**: 보안을 위해 적절한 만료 시간을 설정합니다 (기본 24시간).
3. **HTTPS 사용**: 프로덕션 환경에서는 반드시 HTTPS를 사용하여 토큰이 노출되지 않도록 합니다.
4. **CORS 설정**: 프론트엔드와 백엔드가 다른 도메인에 있는 경우 CORS 설정이 필요합니다.
5. **인증 실패 처리**: 인증 실패 시 JSON 응답을 반환하여 리다이렉트를 방지합니다 (SPA 환경에 적합).
6. **세션 관리**: JWT 기반 인증은 Stateless이므로 `SessionCreationPolicy.STATELESS`로 설정합니다.

---

## 주요 고려사항

### 1. 트랜잭션 관리
- `@Transactional` 어노테이션을 활용하여 주문 생성, 포인트 차감, 결제 처리를 하나의 트랜잭션으로 관리
- 결제 실패 시 모든 변경사항 롤백

### 2. 포인트 처리
- 포인트 사용 시 잔액 확인 및 차감
- 포인트 적립 시 멤버십 등급에 따른 차등 적립률 적용
- 포인트 거래 내역은 `PointTransaction` 엔티티에 기록

### 3. 멤버십 등급 관리
- 결제 완료 시 총 결제 금액 재계산
- 총 결제 금액에 따라 멤버십 등급 자동 업데이트
- `MembershipService`를 통해 등급 관리 로직 분리

### 4. 에러 처리
- 포인트 잔액 부족 시 적절한 에러 메시지 반환
- 주문/결제 정보 조회 실패 시 404 응답
- 예외 발생 시 상세한 에러 메시지 제공

### 5. PortOne API 연동
- `PortOneClient`를 통해 PortOne API 호출
- Reactor Core의 `Mono`를 활용한 비동기 처리
- 결제 검증 및 취소 처리

---

## 참고사항

- **API 엔드포인트 상세 정보**: `02-api-endpoints.md` 파일 참고
- **결제 플로우 설계**: `01-point-payment-flow-design.md` 파일 참고
- **실제 구현 예시**: `src/main/java/com/sparta/point_system/controller/` 패키지 참고