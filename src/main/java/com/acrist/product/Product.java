package com.acrist.product;

import java.math.BigDecimal;

public record Product(
        int id,
        String name,
        String unit,
        String category,
        long quantity,
        BigDecimal price,
        String dateAdded
) {
}
