package com.shopassist.order;

import com.shopassist.common.ResourceNotFoundException;
import com.shopassist.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Order reads for the signed-in shopper.
 *
 * <p>No method here takes a user id. The owner is resolved from the security
 * context on every call, so a caller cannot supply one — and from Phase 5 that
 * includes the language model, whose tools reach orders exclusively through this
 * class. Combined with {@link OrderRepository} having no lookup that omits the
 * owner, "you can only see your own orders" holds because there is no way to
 * express anything else, not because a prompt asks the model not to try.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final CurrentUserService currentUserService;

    public OrderService(OrderRepository orderRepository,
                        OrderEventRepository orderEventRepository,
                        CurrentUserService currentUserService) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.currentUserService = currentUserService;
    }

    public List<OrderSummaryResponse> myOrders() {
        return orderRepository
                .findByUserIdOrderByPlacedAtDesc(currentUserService.requireUserId())
                .stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    public OrderDetailResponse myOrder(String orderNumber) {
        Order order = requireOwnedOrder(orderNumber);

        // Events are loaded separately rather than through the entity graph that
        // already fetches items. Fetching two collection associations in one
        // query is a MultipleBagFetchException; two queries is the correct fix,
        // not a workaround.
        List<OrderEvent> events =
                orderEventRepository.findByOrderIdOrderByOccurredAtAsc(order.getId());

        return OrderDetailResponse.from(order, events);
    }

    public List<OrderEventResponse> myOrderTimeline(String orderNumber) {
        Order order = requireOwnedOrder(orderNumber);
        return orderEventRepository.findByOrderIdOrderByOccurredAtAsc(order.getId())
                .stream()
                .map(OrderEventResponse::from)
                .toList();
    }

    /**
     * Loads an order belonging to the caller.
     *
     * <p>An order that does not exist and an order belonging to somebody else
     * both raise the same exception with the same message, so the response
     * cannot be used to discover which order numbers are real.
     */
    private Order requireOwnedOrder(String orderNumber) {
        return orderRepository
                .findByOrderNumberIgnoreCaseAndUserId(orderNumber, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No order found with number " + orderNumber));
    }
}
