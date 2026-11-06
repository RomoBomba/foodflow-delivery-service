package com.foodflow.common;

import com.foodflow.feedback.ReviewController;
import com.foodflow.ordering.CartController;
import com.foodflow.ordering.KitchenOrderController;
import com.foodflow.ordering.OrderController;
import com.foodflow.recommendation.PreferenceController;
import com.foodflow.recommendation.RecommendationController;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {
        PreferenceController.class,
        RecommendationController.class,
        CartController.class,
        OrderController.class,
        KitchenOrderController.class,
        ReviewController.class
})
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handle(ApiException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", exception.getCode());
        body.put("message", exception.getMessage());
        body.putAll(exception.getDetails());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "VALIDATION_ERROR");
        body.put("message", "Проверьте поля запроса");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "DATA_CONFLICT",
                "message", "Операция конфликтует с существующими данными"
        ));
    }
}
