package com.shopnow.presentation.api;

import com.shopnow.domain.model.EmailAlreadyExistsException;
import com.shopnow.domain.model.InsufficientStockException;
import com.shopnow.domain.model.InvalidCredentialsException;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.model.ReviewExistsException;
import com.shopnow.domain.model.VerifiedPurchaseRequiredException;
import com.shopnow.presentation.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Object> envelope(HttpStatus status, String code, String message, Object details) {
        return ResponseEntity.status(status).body(Map.of("error", new ErrorResponse(code, message, details)));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Object> handleConflict(EmailAlreadyExistsException ex) {
        return envelope(HttpStatus.CONFLICT, "EMAIL_EXISTS", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleUnauthorized(InvalidCredentialsException ex) {
        return envelope(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), null);
    }

    @ExceptionHandler(ReviewExistsException.class)
    public ResponseEntity<Object> handleReviewExists(ReviewExistsException ex) {
        return envelope(HttpStatus.CONFLICT, "REVIEW_EXISTS", ex.getMessage(), null);
    }

    @ExceptionHandler(VerifiedPurchaseRequiredException.class)
    public ResponseEntity<Object> handleVerifiedPurchaseRequired(VerifiedPurchaseRequiredException ex) {
        return envelope(HttpStatus.FORBIDDEN, "VERIFIED_PURCHASE_REQUIRED", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();
        return envelope(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return envelope(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(PromotionException.class)
    public ResponseEntity<Object> handlePromotion(PromotionException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INACTIVE, EXPIRED, USAGE_EXCEEDED, ALREADY_USED -> HttpStatus.CONFLICT;
            case MIN_NOT_MET, INVALID_VALUE -> HttpStatus.BAD_REQUEST;
        };
        return envelope(status, ex.getCode().name(), ex.getMessage(), null);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Object> handleInsufficientStock(InsufficientStockException ex) {
        return envelope(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", ex.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Backstop for check-then-act races (e.g. duplicate review under concurrency)
        // where a service-level check is beaten by the DB UNIQUE constraint.
        return envelope(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with the current state of the resource.", null);
    }
}
