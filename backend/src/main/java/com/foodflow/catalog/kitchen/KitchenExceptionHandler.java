package com.foodflow.catalog.kitchen;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {
        KitchenController.class,
        AdminKitchenController.class
})
public class KitchenExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fields = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        return ResponseEntity.badRequest().body(Map.of(
                "code", "VALIDATION_ERROR",
                "message", "Проверьте поля заявки",
                "fields", fields
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleStatus(
            ResponseStatusException exception
    ) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of(
                        "code", "KITCHEN_REQUEST_REJECTED",
                        "message", exception.getReason() == null
                                ? "Запрос отклонён"
                                : exception.getReason()
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "DATA_CONFLICT",
                "message", "Заявка конфликтует с существующими данными"
        ));
    }
}