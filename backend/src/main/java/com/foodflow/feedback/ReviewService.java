package com.foodflow.feedback;

import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.CatalogQueryService;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.common.ApiException;
import com.foodflow.identity.IdentityQueryService;
import com.foodflow.ordering.OrderItem;
import com.foodflow.ordering.OrderQueryService;
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
    private final OrderQueryService orders;
    private final CatalogQueryService catalog;
    private final IdentityQueryService identityQueryService;

    public ReviewService(
            ReviewRepository reviewRepository,
            OrderQueryService orders,
            CatalogQueryService catalog,
            IdentityQueryService identityQueryService
    ) {
        this.reviewRepository = reviewRepository;
        this.orders = orders;
        this.catalog = catalog;
        this.identityQueryService = identityQueryService;
    }

    @Transactional
    public List<ReviewResponse> create(String email, UUID orderId, OrderReviewRequest request) {
        UUID userId = identityQueryService.getUserId(email);
        List<OrderItem> orderItems = orders.completedItemsForReview(orderId, userId);
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
        if (!catalog.dishExists(dishId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено");
        }
        return reviewRepository.findAllByDishIdAndStatusOrderByCreatedAtDesc(dishId, ReviewStatus.VISIBLE)
                .stream().map(ReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findKitchenReviews(String email) {
        UUID ownerId = identityQueryService.getUserId(email);
        Kitchen kitchen = catalog.findOwnedKitchen(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        List<UUID> dishIds = catalog.menu(kitchen.getId())
                .stream().map(Dish::getId).toList();
        if (dishIds.isEmpty()) return List.of();
        return reviewRepository.findAllByDishIdInAndStatusOrderByCreatedAtDesc(dishIds, ReviewStatus.VISIBLE)
                .stream().map(ReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findForAdmin(Boolean visible) {
        List<Review> reviews = visible == null
                ? reviewRepository.findAllByOrderByCreatedAtDesc()
                : reviewRepository.findAllByStatusOrderByCreatedAtDesc(visible ? ReviewStatus.VISIBLE : ReviewStatus.HIDDEN);
        return reviews.stream().map(ReviewResponse::from).toList();
    }

    @Transactional
    public ReviewResponse hide(String email, UUID reviewId, String reason) {
        UUID moderatorId = identityQueryService.getUserId(email);
        Review review = reviewRepository.findByIdForUpdate(reviewId)
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
