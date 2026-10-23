package com.acrist.product;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(int productId) {
        super("Товар с ID " + productId + " не найден");
    }
}
