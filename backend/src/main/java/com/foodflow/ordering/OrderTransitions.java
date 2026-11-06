package com.foodflow.ordering;

public final class OrderTransitions {
    private OrderTransitions() { }

    public static boolean isAllowed(OrderStatus from, OrderStatus to) {
        return (from == OrderStatus.CREATED && (to == OrderStatus.PREPARING || to == OrderStatus.CANCELLED))
                || (from == OrderStatus.PREPARING && to == OrderStatus.IN_DELIVERY)
                || (from == OrderStatus.IN_DELIVERY && to == OrderStatus.COMPLETED);
    }
}
