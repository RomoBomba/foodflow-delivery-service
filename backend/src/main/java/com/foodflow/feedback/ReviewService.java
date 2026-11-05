package com.foodflow.feedback;

import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.dish.DishRepository;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenRepository;
import com.foodflow.common.ApiException;
import com.foodflow.identity.IdentityQueryService;
import com.foodflow.ordering.CustomerOrder;
import com.foodflow.ordering.OrderItem;
import com.foodflow.ordering.OrderItemRepository;
import com.foodflow.ordering.OrderRepository;
import com.foodflow.ordering.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DishRepository dishRepository;
    private final KitchenRepository kitchenRepository;
    private final IdentityQueryService identityQueryService;

    public ReviewService(
            ReviewRepository reviewRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            DishRepository dishRepository,
            KitchenRepository kitchenRepository,
            IdentityQueryService identityQueryService
    ) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.dishRepository = dishRepository;
        this.kitchenRepository = kitchenRepository;
        this.identityQueryService = identityQueryService;
    }

    @Transactional
    public List<ReviewResponse> create(String email, UUID orderId, OrderReviewRequest request) {
        UUID userId = identityQueryService.getUserId(email);
        CustomerOrder order = orderRepository.findByIdAndCustomerId(orderId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден"));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_COMPLETED", "Оставить отзыв можно только по выполненному заказу");
        }
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);
        Map<UUID, OrderItem> byId = orderItems.stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        Set<UUID> supplied = new HashSet<>();
        List<Review> saved = new ArrayList<>();
        for (ReviewItemRequest item : request.items()) {
            if (!supplied.add(item.orderItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_REVIEW_ITEM", "Позиция заказа указана несколько раз");
            }
            OrderItem orderItem = byId.get(item.orderItemId());
            if (orderItem == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_ITEM_NOT_IN_ORDER", "Позиция не принадлежит этому заказу");
            }
            if (reviewRepository.existsByOrderItemId(orderItem.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "На эту позицию уже оставлен отзыв");
            }
            String text = item.text() == null ? null : item.text().strip();
            saved.add(reviewRepository.save(new Review(
                    orderId, orderItem.getId(), userId, orderItem.getDishId(),
                    item.rating(), item.reordered(), text == null || text.isBlank() ? null : text
            )));
        }
        return saved.stream().map(ReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findVisibleByDish(UUID dishId) {
        if (!dishRepository.existsById(dishId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено");
        }
        return reviewRepository.findAllByDishIdAndVisibleTrueOrderByCreatedAtDesc(dishId)
                .stream().map(ReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findKitchenReviews(String email) {
        UUID ownerId = identityQueryService.getUserId(email);
        Kitchen kitchen = kitchenRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        List<UUID> dishIds = dishRepository.findAllByKitchenIdOrderByCreatedAtDesc(kitchen.getId())
                .stream().map(Dish::getId).toList();
        if (dishIds.isEmpty()) return List.of();
        return reviewRepository.findAllByDishIdInAndVisibleTrueOrderByCreatedAtDesc(dishIds)
                .stream().map(ReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findForAdmin(Boolean visible) {
        List<Review> reviews = visible == null
                ? reviewRepository.findAllByOrderByCreatedAtDesc()
                : reviewRepository.findAllByVisibleOrderByCreatedAtDesc(visible);
        return reviews.stream().map(ReviewResponse::from).toList();
    }

    @Transactional
    public ReviewResponse hide(String email, UUID reviewId, String reason) {
        UUID moderatorId = identityQueryService.getUserId(email);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Отзыв не найден"));
        if (!review.isVisible()) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_HIDDEN", "Отзыв уже скрыт");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODERATION_REASON_REQUIRED", "Укажите причину скрытия отзыва");
        }
        review.hide(moderatorId, reason.strip());
        return ReviewResponse.from(review);
    }
}
