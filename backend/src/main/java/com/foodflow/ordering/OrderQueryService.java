package com.foodflow.ordering;

import com.foodflow.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class OrderQueryService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    public OrderQueryService(OrderRepository orders, OrderItemRepository items) {
        this.orders = orders;
        this.items = items;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<OrderItem> completedItemsForReview(UUID orderId, UUID userId) {
        CustomerOrder order = orders.findByIdAndCustomerForUpdate(orderId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден"));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_COMPLETED", "Оставить отзыв можно только по выполненному заказу");
        }
        return items.findAllByOrderId(orderId);
    }
}
