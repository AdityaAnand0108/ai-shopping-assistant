package com.shopassist.controllers.order;

import com.shopassist.dto.order.OrderDetailResponse;
import com.shopassist.dto.order.OrderEventResponse;
import com.shopassist.dto.order.OrderSummaryResponse;
import com.shopassist.services.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in shopper's orders.
 *
 * <p>None of these paths carry a user identifier. Whose orders these are comes
 * from the token, so there is no URL a client could edit to reach somebody
 * else's.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order history for the signed-in shopper")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List your orders, most recent first")
    public List<OrderSummaryResponse> myOrders() {
        return orderService.myOrders();
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Fetch one of your orders in full")
    public OrderDetailResponse byNumber(@PathVariable String orderNumber) {
        return orderService.myOrder(orderNumber);
    }

    @GetMapping("/{orderNumber}/timeline")
    @Operation(summary = "Fetch the tracking timeline for one of your orders")
    public List<OrderEventResponse> timeline(@PathVariable String orderNumber) {
        return orderService.myOrderTimeline(orderNumber);
    }
}
