package com.acrist.server.product;

import com.acrist.server.config.DatabaseConfig;
import com.acrist.shared.product.Product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcProductRepository implements ProductRepository {
    private final DatabaseConfig config;

    public JdbcProductRepository(DatabaseConfig config) {
        this.config = config;
    }

    public void verifyConnection() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM products LIMIT 1")) {
            statement.executeQuery();
        }
    }

    @Override
    public List<Product> findAll() throws SQLException {
        String sql = "SELECT id, name, unit, category, quantity, price, date_added "
                + "FROM products ORDER BY id";

        List<Product> products = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                products.add(readProduct(result));
            }
        }

        return products;
    }

    @Override
    public Product create(String name, String unit, String category, BigDecimal price) throws SQLException {
        String sql = "INSERT INTO products (name, unit, category, price) VALUES (?, ?, ?, ?) "
                + "RETURNING id, name, unit, category, quantity, price, date_added";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, unit);
            statement.setString(3, category);
            statement.setBigDecimal(4, price);

            try (ResultSet result = statement.executeQuery()) {
                result.next();

                return readProduct(result);
            }
        }
    }

    @Override
    public Optional<Product> update(int productId, String name, String unit, String category,
                                    BigDecimal price) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentUnit;
                long currentQuantity;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT unit, quantity FROM products WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, productId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();

                            return Optional.empty();
                        }
                        currentUnit = result.getString("unit");
                        currentQuantity = result.getLong("quantity");
                    }
                }

                if (!currentUnit.equals(unit)
                        && (currentQuantity != 0 || hasShopProductsOrOrders(connection, productId))) {
                    throw new IllegalStateException(
                            "Единицу измерения нельзя менять, пока есть остатки, товары в магазинах или заказы");
                }

                String sql = "UPDATE products SET name = ?, unit = ?, category = ?, "
                        + "price = COALESCE(?, price) WHERE id = ? "
                        + "RETURNING id, name, unit, category, quantity, price, date_added";
                Product product;

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, name);
                    statement.setString(2, unit);
                    statement.setString(3, category);

                    if (price == null) {
                        statement.setNull(4, Types.NUMERIC);
                    } else {
                        statement.setBigDecimal(4, price);
                    }

                    statement.setInt(5, productId);

                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        product = readProduct(result);
                    }
                }

                connection.commit();

                return Optional.of(product);
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        }
    }

    private static boolean hasShopProductsOrOrders(Connection connection, int productId) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM shop_products WHERE id_product = ?) "
                + "OR EXISTS (SELECT 1 FROM orders WHERE id_product = ?) AS linked";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            statement.setInt(2, productId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();

                return result.getBoolean("linked");
            }
        }
    }

    @Override
    public Optional<Boolean> canChangeUnit(int productId) throws SQLException {
        String sql = "SELECT p.quantity = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM shop_products sp WHERE sp.id_product = p.id) "
                + "AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.id_product = p.id) AS allowed "
                + "FROM products p WHERE p.id = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getBoolean("allowed")) : Optional.empty();
            }
        }
    }

    @Override
    public boolean delete(int productId) throws SQLException {
        String sql = "DELETE FROM products p WHERE p.id = ? AND p.quantity = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM shop_products sp WHERE sp.id_product = p.id) "
                + "AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.id_product = p.id) "
                + "RETURNING p.id";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
            try (PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM products WHERE id = ?")) {
                exists.setInt(1, productId);

                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) {
                        throw new IllegalStateException(
                                "Удалить товар можно только при нулевом остатке и без связей с магазинами и заказами");
                    }
                }
            }

            return false;
        }
    }

    @Override
    public Optional<Product> receive(int productId, long amount) throws SQLException {
        String sql = "UPDATE products SET quantity = quantity + ? WHERE id = ? "
                + "RETURNING id, name, unit, category, quantity, price, date_added";

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Product product;

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, amount);
                    statement.setInt(2, productId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();

                            return Optional.empty();
                        }
                        product = readProduct(result);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO stock_movements "
                                + "(id_product, product_name, unit, movement_type, quantity) "
                                + "VALUES (?, ?, ?, 'receipt', ?)")) {
                    statement.setInt(1, productId);
                    statement.setString(2, product.name());
                    statement.setString(3, product.unit());
                    statement.setLong(4, amount);
                    statement.executeUpdate();
                }

                connection.commit();

                return Optional.of(product);
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    private static Product readProduct(ResultSet result) throws SQLException {
        return new Product(
                result.getInt("id"),
                result.getString("name"),
                result.getString("unit"),
                result.getString("category"),
                result.getLong("quantity"),
                result.getBigDecimal("price"),
                result.getDate("date_added").toLocalDate().toString()
        );
    }
}
