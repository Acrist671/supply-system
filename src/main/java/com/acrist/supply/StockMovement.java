package com.acrist.supply;

public record StockMovement(long id, Integer productId, String productName, String unit,
                            String type, long quantity, Integer shopId, String shopName,
                            Integer orderId, String createdAt) {
}
