package com.sparta.point_system.service;

import com.sparta.point_system.entity.Membership;
import com.sparta.point_system.entity.MembershipLevel;
import com.sparta.point_system.entity.Order;
import com.sparta.point_system.entity.Payment;
import com.sparta.point_system.repository.MembershipRepository;
import com.sparta.point_system.repository.MembershipLevelRepository;
import com.sparta.point_system.repository.OrderRepository;
import com.sparta.point_system.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class MembershipService {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private MembershipLevelRepository membershipLevelRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * 사용자의 멤버십 등급에 따른 포인트 적립률 조회
     */
    @Transactional
    public BigDecimal getPointAccrualRate(Long userId) {
        Membership membership = membershipRepository.findByUserId(userId)
            .orElseGet(() -> {
                // 멤버십이 없으면 기본 등급(Normal) 부여
                return createDefaultMembership(userId);
            });

        MembershipLevel level = membershipLevelRepository.findById(membership.getLevelId())
            .orElseGet(() -> {
                // 등급이 없으면 Normal 등급 생성
                System.err.println("멤버십 등급 정보를 찾을 수 없습니다. Level ID: " + membership.getLevelId() + ", Normal 등급으로 대체합니다.");
                return createDefaultMembershipLevel("Normal");
            });

        return level.getPointAccrualRate();
    }

    /**
     * 결제 금액에 멤버십 등급 적립률을 적용하여 적립 포인트 계산
     */
    @Transactional(readOnly = true)
    public Integer calculateEarnedPoints(Long userId, BigDecimal paymentAmount) {
        BigDecimal accrualRate = getPointAccrualRate(userId);
        BigDecimal earnedPoints = paymentAmount.multiply(accrualRate);

        // 소수점 이하 반올림 처리
        return earnedPoints.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 사용자의 총 결제 금액 계산 (완료된 주문만)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalPaymentAmount(Long userId) {
        List<Order> completedOrders = orderRepository.findByUserIdAndStatus(userId, Order.OrderStatus.COMPLETED);
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Order order : completedOrders) {
            totalAmount = totalAmount.add(order.getTotalAmount());
        }
        
        return totalAmount;
    }

    /**
     * 사용자의 총 결제 금액 계산 (환불 제외, PAID 상태의 결제만)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalPaidAmount(Long userId) {
        List<Order> userOrders = orderRepository.findByUserId(userId);
        List<String> orderIds = userOrders.stream()
            .map(Order::getOrderId)
            .toList();
        
        List<Payment> paidPayments = paymentRepository.findByOrderIdInAndStatus(orderIds, Payment.PaymentStatus.PAID);
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Payment payment : paidPayments) {
            totalAmount = totalAmount.add(payment.getAmount());
        }
        
        return totalAmount;
    }

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
     * 사용자의 멤버십 정보 조회 (없으면 기본 등급으로 생성)
     */
    @Transactional
    public Membership getMembership(Long userId) {
        return membershipRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultMembership(userId));
    }

    /**
     * 기본 멤버십 생성 (Normal 등급)
     */
    @Transactional
    private Membership createDefaultMembership(Long userId) {
        MembershipLevel normalLevel = createDefaultMembershipLevel("Normal");
        
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setLevelId(normalLevel.getLevelId());
        return membershipRepository.save(membership);
    }

    /**
     * 멤버십 등급 정보와 함께 조회
     */
    @Transactional
    public MembershipWithLevel getMembershipWithLevel(Long userId) {
        try {
            Membership membership = getMembership(userId);
            
            // Lazy Loading 문제 방지를 위해 명시적으로 조회
            MembershipLevel level = membershipLevelRepository.findById(membership.getLevelId())
                .orElseGet(() -> {
                    // 등급이 없으면 Normal 등급 생성
                    System.err.println("멤버십 등급 정보를 찾을 수 없습니다. Level ID: " + membership.getLevelId() + ", Normal 등급으로 대체합니다.");
                    return createDefaultMembershipLevel("Normal");
                });
            
            // 프록시 객체를 실제 엔티티로 초기화 (Lazy Loading 방지)
            // level의 모든 필드에 접근하여 프록시를 초기화
            level.getLevelId();
            level.getName();
            level.getPointAccrualRate();
            level.getBenefitsDescription();
            
            BigDecimal totalPaymentAmount = calculateTotalPaidAmount(userId);
            
            return new MembershipWithLevel(membership, level, totalPaymentAmount);
        } catch (Exception e) {
            System.err.println("getMembershipWithLevel 오류 발생: " + e.getMessage());
            e.printStackTrace();
            // 에러 발생 시 기본값 반환
            MembershipLevel defaultLevel = createDefaultMembershipLevel("Normal");
            Membership defaultMembership = createDefaultMembership(userId);
            return new MembershipWithLevel(defaultMembership, defaultLevel, BigDecimal.ZERO);
        }
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
        } else {
            newLevel.setPointAccrualRate(new BigDecimal("0.01"));
            newLevel.setBenefitsDescription("일반 등급 - 기본 1% 포인트 적립");
        }
        
        return membershipLevelRepository.save(newLevel);
    }

    /**
     * 멤버십 정보와 등급을 함께 담는 DTO
     */
    public static class MembershipWithLevel {
        private Membership membership;
        private MembershipLevel level;
        private BigDecimal totalPaymentAmount;

        public MembershipWithLevel(Membership membership, MembershipLevel level, BigDecimal totalPaymentAmount) {
            this.membership = membership;
            this.level = level;
            this.totalPaymentAmount = totalPaymentAmount;
        }

        public Membership getMembership() {
            return membership;
        }

        public MembershipLevel getLevel() {
            return level;
        }

        public BigDecimal getTotalPaymentAmount() {
            return totalPaymentAmount;
        }
    }
}

