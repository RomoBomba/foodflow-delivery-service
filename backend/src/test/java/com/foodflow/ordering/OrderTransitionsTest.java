package com.foodflow.ordering;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTransitionsTest {
    static Stream<Arguments> transitions() {
        return Stream.of(OrderStatus.values()).flatMap(from -> Stream.of(OrderStatus.values())
                .map(to -> Arguments.of(from, to, switch (from) {
                    case CREATED -> to == OrderStatus.PREPARING || to == OrderStatus.CANCELLED;
                    case PREPARING -> to == OrderStatus.DELIVERING;
                    case DELIVERING -> to == OrderStatus.COMPLETED;
                    default -> false;
                })));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void onlyDocumentedTransitionsAreAllowed(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(OrderTransitions.isAllowed(from, to)).isEqualTo(expected);
    }
}
