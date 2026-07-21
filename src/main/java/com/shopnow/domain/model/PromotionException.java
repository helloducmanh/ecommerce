// src/main/java/com/shopnow/domain/model/PromotionException.java
package com.shopnow.domain.model;

public class PromotionException extends RuntimeException {

    public enum Code {
        NOT_FOUND,        // 404
        INACTIVE,         // 409
        EXPIRED,          // 409
        USAGE_EXCEEDED,   // 409
        ALREADY_USED,     // 409
        MIN_NOT_MET,      // 400
        INVALID_VALUE     // 400
    }

    private final Code code;

    public PromotionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
