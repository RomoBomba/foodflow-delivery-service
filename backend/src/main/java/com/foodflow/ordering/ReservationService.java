package com.foodflow.ordering;

import com.foodflow.catalog.CatalogStockService;
import com.foodflow.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationService {
    private final ReservationRepository reservations;
    private final OrderItemRepository items;
    private final CatalogStockService stock;

    public ReservationService(ReservationRepository reservations, OrderItemRepository items, CatalogStockService stock) {
        this.reservations = reservations;
        this.items = items;
        this.stock = stock;
    }

    public void create(OrderItem item) { reservations.save(new Reservation(item)); }
    public void release(UUID orderId) { finish(orderId, ReservationStatus.RELEASED); }
    public void consume(UUID orderId) { finish(orderId, ReservationStatus.CONSUMED); }

    // The caller holds the order lock; every reservation mutation uses that same lock.
    private void finish(UUID orderId, ReservationStatus target) {
        for (OrderItem item : items.findAllByOrderIdOrderByDishIdAsc(orderId)) {
            Reservation reservation = reservations.findByOrderItemId(item.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "RESERVATION_MISSING", "Резерв позиции не найден"));
            if (reservation.getStatus() != ReservationStatus.ACTIVE || reservation.getQuantity() != item.getQuantity()) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_RESERVATION", "Резерв позиции уже закрыт или некорректен");
            }
            if (target == ReservationStatus.RELEASED) stock.release(item.getDishId(), reservation.getQuantity());
            reservation.finish(target);
        }
    }
}
