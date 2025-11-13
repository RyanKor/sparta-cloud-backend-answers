package com.sparta.point_system.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PaymentRequestDto {
    private String orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer pointsUsed;
    private BigDecimal pointsDiscountAmount;
    private String orderName;
    private String paymentMethod;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private List<OrderItemDto> orderItems;

    @Getter
    @Setter
    public static class OrderItemDto {
        private Long productId;
        private Integer quantity;
        private BigDecimal price;
        private String productName;
    }
}

