package com.foodflow.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByOrderItemId(UUID orderItemId);
    List<Review> findAllByDishIdAndVisibleTrueOrderByCreatedAtDesc(UUID dishId);
    List<Review> findAllByDishIdInAndVisibleTrueOrderByCreatedAtDesc(List<UUID> dishIds);
    List<Review> findAllByVisibleOrderByCreatedAtDesc(boolean visible);
    List<Review> findAllByOrderByCreatedAtDesc();

    @Query("""
            select count(r) as reviewCount,
                   avg(r.rating) as averageRating,
                   coalesce(sum(case when r.reordered = true then 1 else 0 end), 0) as repeatCount,
                   count(r.reordered) as repeatOrderResponses
            from Review r
            where r.dishId = :dishId and r.visible = true
            """)
    ReviewAggregate summarize(@Param("dishId") UUID dishId);

    @Query("""
            select r.dishId as dishId,
                   count(r) as reviewCount,
                   avg(r.rating) as averageRating,
                   coalesce(sum(case when r.reordered = true then 1 else 0 end), 0) as repeatCount,
                   count(r.reordered) as repeatOrderResponses
            from Review r
            where r.dishId in :dishIds and r.visible = true
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
