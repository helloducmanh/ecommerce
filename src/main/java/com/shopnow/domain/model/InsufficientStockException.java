// src/main/java/com/shopnow/domain/model/InsufficientStockException.java
package com.shopnow.domain.model;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
