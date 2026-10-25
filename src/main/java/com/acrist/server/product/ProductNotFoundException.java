package com.acrist.server.product;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(int productId) {
        super("Товар с ID " + productId + " не найден");
    }
}
