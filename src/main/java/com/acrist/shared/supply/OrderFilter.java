package com.acrist.shared.supply;

public record OrderFilter(Integer shopId, Integer productId, String status, Integer year) {
    public static OrderFilter all() {
        return new OrderFilter(null, null, null, null);
    }
}
