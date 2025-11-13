package com.sparta.point_system.controller;

import com.sparta.point_system.entity.OrderItem;
import com.sparta.point_system.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class OrderItemController {

    @Autowired
    private OrderItemRepository orderItemRepository;

    @PostMapping("/order-item")
    public OrderItem createOrderItem(@RequestParam String orderId,
                                    @RequestParam Long productId,
                                    @RequestParam Integer quantity,
                                    @RequestParam BigDecimal price) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(productId);
        orderItem.setQuantity(quantity);
        orderItem.setPrice(price);
        return orderItemRepository.save(orderItem);
    }

    @GetMapping("/order-items")
    public List<OrderItem> getAllOrderItems() {
        return orderItemRepository.findAll();
    }

    @GetMapping("/order-item/{orderItemId}")
    public Optional<OrderItem> getOrderItemById(@PathVariable Long orderItemId) {
        return orderItemRepository.findById(orderItemId);
    }

    @GetMapping("/order-items/order/{orderId}")
    public List<OrderItem> getOrderItemsByOrderId(@PathVariable String orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    @PutMapping("/order-item/{orderItemId}")
    public OrderItem updateOrderItem(@PathVariable Long orderItemId,
                                    @RequestParam(required = false) Integer quantity,
                                    @RequestParam(required = false) BigDecimal price) {
        Optional<OrderItem> orderItemOpt = orderItemRepository.findById(orderItemId);
        if (orderItemOpt.isPresent()) {
            OrderItem orderItem = orderItemOpt.get();
            if (quantity != null) orderItem.setQuantity(quantity);
            if (price != null) orderItem.setPrice(price);
            return orderItemRepository.save(orderItem);
        }
        throw new RuntimeException("OrderItem not found with id: " + orderItemId);
    }

    @DeleteMapping("/order-item/{orderItemId}")
    public String deleteOrderItem(@PathVariable Long orderItemId) {
        orderItemRepository.deleteById(orderItemId);
        return "OrderItem deleted successfully";
    }
}

