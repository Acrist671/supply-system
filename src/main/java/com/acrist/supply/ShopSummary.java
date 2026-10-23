package com.acrist.supply;

import java.math.BigDecimal;

public record ShopSummary(int shopId, String shopName, long productKinds,
                          BigDecimal totalQuantity, BigDecimal totalValue) {
}
