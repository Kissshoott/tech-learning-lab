package com.example.demo.controller;

import com.example.demo.metrics.OrderMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 订单控制器
 * 演示如何在业务代码中使用自定义指标
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderMetrics orderMetrics;

    public OrderController(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    /**
     * 创建订单
     */
    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderRequest) {
        Timer.Sample sample = Timer.start();

        try {
            // 记录新订单
            orderMetrics.recordOrder();

            // 模拟订单处理
            Thread.sleep(100);

            // 模拟随机失败（10% 概率）
            if (Math.random() < 0.1) {
                orderMetrics.recordFailedOrder();
                return createErrorResponse("Order creation failed");
            }

            // 记录处理时间
            sample.stop(orderMetrics.getProcessingTimer());

            return createSuccessResponse("Order created successfully");

        } catch (Exception e) {
            orderMetrics.recordFailedOrder();
            return createErrorResponse("Error: " + e.getMessage());
        }
    }

    /**
     * 获取订单统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getOrderStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingOrders", orderMetrics.getPendingOrders());
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }

    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
