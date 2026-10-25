package com.acrist.shared.supply;

public record SupplyOrder(int id, int shopId, String shopName, int productId,
                          String productName, long quantity, String dateOrder, String status) {
}
