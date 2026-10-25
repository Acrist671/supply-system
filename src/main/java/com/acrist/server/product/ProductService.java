package com.acrist.server.product;

import com.acrist.shared.product.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class ProductService {
    private static final Set<String> UNITS = Set.of("шт", "кг", "л", "м", "уп");

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> findAll() throws SQLException {
        return repository.findAll();
    }

    public Product create(String name, String unit, String category) throws SQLException {
        return create(name, unit, category, BigDecimal.ZERO);
    }

    public Product create(String name, String unit, String category, BigDecimal price) throws SQLException {
        String cleanName = required(name, 100, "Название товара");
        String cleanCategory = required(category, 50, "Категория");

        if (!UNITS.contains(unit)) {
            throw new IllegalArgumentException("Единица измерения должна быть: шт, кг, л, м или уп");
        }
        BigDecimal actualPrice = price == null ? BigDecimal.ZERO : price;
        validatePrice(actualPrice);

        return repository.create(cleanName, unit, cleanCategory, actualPrice);
    }

    public Product update(int productId, String name, String unit, String category) throws SQLException {
        return update(productId, name, unit, category, null);
    }

    public Product update(int productId, String name, String unit, String category,
                          BigDecimal price) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Некорректный ID товара");
        }
        String cleanName = required(name, 100, "Название товара");
        String cleanCategory = required(category, 50, "Категория");

        if (!UNITS.contains(unit)) {
            throw new IllegalArgumentException("Единица измерения должна быть: шт, кг, л, м или уп");
        }
        if (price != null) {
            validatePrice(price);
        }

        return repository.update(productId, cleanName, unit, cleanCategory, price)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public boolean canChangeUnit(int productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Некорректный ID товара");
        }

        return repository.canChangeUnit(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public void delete(int productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Некорректный ID товара");
        }
        if (!repository.delete(productId)) {
            throw new ProductNotFoundException(productId);
        }
    }

    public Product receive(int productId, long amount) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Некорректный ID товара");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Количество поступления должно быть больше нуля");
        }

        return repository.receive(productId, amount)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private static String required(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " не может быть пустым");
        }
        String cleanValue = value.trim();

        if (cleanValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + ": максимум " + maxLength + " символов");
        }

        return cleanValue;
    }

    private static void validatePrice(BigDecimal price) {
        if (price.signum() < 0 || price.compareTo(new BigDecimal("99999.99")) > 0
                || price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Цена должна быть от 0 до 99999,99 с точностью до копеек");
        }
    }
}
