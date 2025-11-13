package com.sparta.point_system.controller;

import com.sparta.point_system.entity.Order;
import com.sparta.point_system.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @PostMapping("/order")
    public Order createOrder(@RequestParam String orderId,
                            @RequestParam Long userId,
                            @RequestParam BigDecimal totalAmount,
                            @RequestParam(required = false, defaultValue = "0") Integer pointsUsed,
                            @RequestParam(required = false, defaultValue = "0") BigDecimal pointsDiscountAmount,
                            @RequestParam Order.OrderStatus status) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPointsUsed(pointsUsed);
        order.setPointsDiscountAmount(pointsDiscountAmount);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    @GetMapping("/orders")
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @GetMapping("/order/{orderId}")
    public Optional<Order> getOrderById(@PathVariable String orderId) {
        return orderRepository.findById(orderId);
    }

    @GetMapping("/orders/user/{userId}")
    public List<Order> getOrdersByUserId(@PathVariable Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @GetMapping("/orders/status/{status}")
    public List<Order> getOrdersByStatus(@PathVariable Order.OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @PutMapping("/order/{orderId}")
    public Order updateOrder(@PathVariable String orderId,
                            @RequestParam(required = false) BigDecimal totalAmount,
                            @RequestParam(required = false) Integer pointsUsed,
                            @RequestParam(required = false) BigDecimal pointsDiscountAmount,
                            @RequestParam(required = false) Order.OrderStatus status) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            if (totalAmount != null) order.setTotalAmount(totalAmount);
            if (pointsUsed != null) order.setPointsUsed(pointsUsed);
            if (pointsDiscountAmount != null) order.setPointsDiscountAmount(pointsDiscountAmount);
            if (status != null) order.setStatus(status);
            return orderRepository.save(order);
        }
        throw new RuntimeException("Order not found with id: " + orderId);
    }

    @DeleteMapping("/order/{orderId}")
    public String deleteOrder(@PathVariable String orderId) {
        orderRepository.deleteById(orderId);
        return "Order deleted successfully";
    }
}

