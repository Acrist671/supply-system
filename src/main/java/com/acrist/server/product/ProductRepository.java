package com.acrist.server.product;

import com.acrist.shared.product.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findAll() throws SQLException;

    Product create(String name, String unit, String category, BigDecimal price) throws SQLException;

    Optional<Product> update(int productId, String name, String unit, String category,
                             BigDecimal price) throws SQLException;

    Optional<Boolean> canChangeUnit(int productId) throws SQLException;

    boolean delete(int productId) throws SQLException;

    Optional<Product> receive(int productId, long amount) throws SQLException;
}
