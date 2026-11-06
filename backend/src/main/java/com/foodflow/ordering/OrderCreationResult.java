package com.foodflow.ordering;

public record OrderCreationResult(OrderResponse order, boolean replayed) { }
