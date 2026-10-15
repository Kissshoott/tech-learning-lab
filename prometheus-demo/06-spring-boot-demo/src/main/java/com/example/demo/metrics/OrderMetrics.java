package com.example.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单业务指标
 * 演示如何自定义 Prometheus 指标
 */
@Component
public class OrderMetrics {

    private final Counter orderCounter;
    private final Counter orderFailedCounter;
    private final Counter orderPaymentFailedCounter;
    private final Timer orderProcessingTimer;
    private final AtomicInteger pendingOrders;

    public OrderMetrics(MeterRegistry registry) {
        // 订单总数计数器
        this.orderCounter = Counter.builder("orders_total")
                .description("Total number of orders created")
                .tag("type", "new")
                .register(registry);

        // 订单失败总数
        this.orderFailedCounter = Counter.builder("orders_failed_total")
                .description("Total number of failed orders")
                .tag("reason", "general")
                .register(registry);

        // 支付失败总数
        this.orderPaymentFailedCounter = Counter.builder("orders_failed_total")
                .description("Total number of payment failed orders")
                .tag("reason", "payment")
                .register(registry);

        // 订单处理时间
        this.orderProcessingTimer = Timer.builder("orders_processing_time")
                .description("Time taken to process an order")
                .register(registry);

        // 待处理订单数（Gauge）
        this.pendingOrders = new AtomicInteger(0);
        registry.gauge("orders_pending", pendingOrders);
    }

    /**
     * 记录新订单
     */
    public void recordOrder() {
        orderCounter.increment();
        pendingOrders.incrementAndGet();
    }

    /**
     * 记录订单完成
     */
    public void recordOrderCompleted() {
        pendingOrders.decrementAndGet();
    }

    /**
     * 记录订单失败
     */
    public void recordFailedOrder() {
        orderFailedCounter.increment();
        pendingOrders.decrementAndGet();
    }

    /**
     * 记录支付失败
     */
    public void recordPaymentFailedOrder() {
        orderPaymentFailedCounter.increment();
        pendingOrders.decrementAndGet();
    }

    /**
     * 记录订单处理时间
     */
    public Timer getProcessingTimer() {
        return orderProcessingTimer;
    }

    /**
     * 获取待处理订单数
     */
    public int getPendingOrders() {
        return pendingOrders.get();
    }
}
