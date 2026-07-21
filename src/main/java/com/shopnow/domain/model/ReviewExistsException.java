package com.shopnow.domain.model;

public class ReviewExistsException extends RuntimeException {
    public ReviewExistsException() {
        super("You have already reviewed this product");
    }
}
