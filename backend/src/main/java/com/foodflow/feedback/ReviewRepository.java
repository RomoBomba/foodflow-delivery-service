package com.foodflow.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") UUID id);

    List<Review> findAllByOrderIdAndUserIdOrderByCreatedAtDesc(UUID orderId, UUID userId);

    boolean existsByOrderItemId(UUID orderItemId);
    List<Review> findAllByDishIdAndStatusOrderByCreatedAtDesc(UUID dishId, ReviewStatus status);
    List<Review> findAllByDishIdInAndStatusOrderByCreatedAtDesc(List<UUID> dishIds, ReviewStatus status);
    List<Review> findAllByStatusOrderByCreatedAtDesc(ReviewStatus status);
    List<Review> findAllByOrderByCreatedAtDesc();

    @Query("""
            select count(r) as reviewCount,
                   avg(r.rating) as averageRating,
                   coalesce(sum(case when r.reordered = true then 1 else 0 end), 0) as repeatCount,
                   count(r.reordered) as repeatOrderResponses
            from Review r
            where r.dishId = :dishId and r.status = com.foodflow.feedback.ReviewStatus.VISIBLE
            """)
    ReviewAggregate summarize(@Param("dishId") UUID dishId);

    @Query("""
            select r.dishId as dishId,
                   count(r) as reviewCount,
                   avg(r.rating) as averageRating,
                   coalesce(sum(case when r.reordered = true then 1 else 0 end), 0) as repeatCount,
                   count(r.reordered) as repeatOrderResponses
            from Review r
            where r.dishId in :dishIds and r.status = com.foodflow.feedback.ReviewStatus.VISIBLE
            group by r.dishId
            """)
    List<DishReviewAggregate> summarizeForDishes(@Param("dishIds") List<UUID> dishIds);

    interface DishReviewAggregate extends ReviewAggregate {
        UUID getDishId();
    }

    interface ReviewAggregate {
        Long getReviewCount();
        Double getAverageRating();
        Long getRepeatCount();
        Long getRepeatOrderResponses();
    }
}
