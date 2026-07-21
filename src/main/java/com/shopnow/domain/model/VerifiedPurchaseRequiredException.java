package com.shopnow.domain.model;

public class VerifiedPurchaseRequiredException extends RuntimeException {
    public VerifiedPurchaseRequiredException() {
        super("Only verified purchasers can review this product");
    }
}
