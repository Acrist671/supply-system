package com.acrist.supply;

import com.acrist.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class JdbcSupplyRepository implements SupplyRepository {
    private static final String ORDER_SELECT = "SELECT o.id, o.id_shop, s.name AS shop_name, "
            + "o.id_product, p.name AS product_name, o.quantity_req, o.date_order, o.status "
            + "FROM orders o JOIN shops s ON s.id = o.id_shop "
            + "JOIN products p ON p.id = o.id_product ";

    private final DatabaseConfig config;

    public JdbcSupplyRepository(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public List<Shop> findShops() throws SQLException {
        List<Shop> shops = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, address, phone FROM shops ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                shops.add(readShop(result));
            }
        }

        return shops;
    }

    @Override
    public List<ShopSummary> findShopSummaries() throws SQLException {
        String sql = "SELECT s.id, s.name, COUNT(sp.id) AS product_kinds, "
                + "COALESCE(SUM(sp.quantity), 0) AS total_quantity, "
                + "COALESCE(SUM(sp.quantity * p.price), 0) AS total_value "
                + "FROM shops s LEFT JOIN shop_products sp ON sp.id_shop = s.id "
                + "LEFT JOIN products p ON p.id = sp.id_product "
                + "GROUP BY s.id, s.name ORDER BY s.name, s.id";

        List<ShopSummary> summaries = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                summaries.add(new ShopSummary(result.getInt("id"), result.getString("name"),
                        result.getLong("product_kinds"), result.getBigDecimal("total_quantity"),
                        result.getBigDecimal("total_value")));
            }
        }

        return summaries;
    }

    @Override
    public Shop createShop(String name, String address, String phone) throws SQLException {
        String sql = "INSERT INTO shops (name, address, phone) VALUES (?, ?, ?) "
                + "RETURNING id, name, address, phone";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, address);
            statement.setString(3, phone);

            try (ResultSet result = statement.executeQuery()) {
                result.next();

                return readShop(result);
            }
        }
    }

    @Override
    public Optional<Shop> updateShop(int shopId, String name, String address, String phone)
            throws SQLException {
        String sql = "UPDATE shops SET name = ?, address = ?, phone = ? WHERE id = ? "
                + "RETURNING id, name, address, phone";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, address);
            statement.setString(3, phone);
            statement.setInt(4, shopId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readShop(result)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean deleteShop(int shopId) throws SQLException {
        String sql = "DELETE FROM shops s WHERE s.id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM shop_products sp WHERE sp.id_shop = s.id) "
                + "AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.id_shop = s.id) "
                + "RETURNING s.id";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
            try (PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM shops WHERE id = ?")) {
                exists.setInt(1, shopId);

                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) {
                        throw new IllegalStateException(
                                "Удалить магазин можно только без товаров и заказов");
                    }
                }
            }

            return false;
        }
    }

    @Override
    public List<ShopStock> findShopStock(int shopId) throws SQLException {
        String sql = "SELECT p.id, p.name, p.unit, sp.quantity "
                + "FROM shop_products sp JOIN products p ON p.id = sp.id_product "
                + "WHERE sp.id_shop = ? ORDER BY p.name";

        List<ShopStock> stock = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    stock.add(new ShopStock(result.getInt("id"), result.getString("name"),
                            result.getString("unit"), result.getLong("quantity")));
                }
            }
        }

        return stock;
    }

    @Override
    public ShopStock decreaseShopStock(int shopId, int productId, long quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество для списания должно быть положительным");
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                String productName;
                String unit;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT name, unit FROM products WHERE id = ? FOR SHARE")) {
                    statement.setInt(1, productId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new NoSuchElementException("Товар не найден: " + productId);
                        }
                        productName = result.getString("name");
                        unit = result.getString("unit");
                    }
                }

                String shopName;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT name FROM shops WHERE id = ? FOR SHARE")) {
                    statement.setInt(1, shopId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new NoSuchElementException("Магазин не найден: " + shopId);
                        }
                        shopName = result.getString("name");
                    }
                }

                long newQuantity;
                String sql = "UPDATE shop_products SET quantity = quantity - ? "
                        + "WHERE id_shop = ? AND id_product = ? AND quantity >= ? "
                        + "RETURNING quantity";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, quantity);
                    statement.setInt(2, shopId);
                    statement.setInt(3, productId);
                    statement.setLong(4, quantity);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Недостаточно товара в магазине для списания");
                        }
                        newQuantity = result.getLong("quantity");
                    }
                }
                if (newQuantity == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM shop_products WHERE id_shop = ? AND id_product = ? AND quantity = 0")) {
                        statement.setInt(1, shopId);
                        statement.setInt(2, productId);
                        statement.executeUpdate();
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO stock_movements "
                                + "(id_product, product_name, unit, id_shop, shop_name, movement_type, quantity) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setInt(1, productId);
                    statement.setString(2, productName);
                    statement.setString(3, unit);
                    statement.setInt(4, shopId);
                    statement.setString(5, shopName);
                    statement.setString(6, "shop_decrease");
                    statement.setLong(7, quantity);
                    statement.executeUpdate();
                }

                connection.commit();

                return new ShopStock(productId, productName, unit, newQuantity);
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

    @Override
    public List<StockMovement> findMovements() throws SQLException {
        String sql = "SELECT id, id_product, product_name, unit, movement_type, quantity, "
                + "id_shop, shop_name, id_order, created_at "
                + "FROM stock_movements ORDER BY created_at DESC, id DESC";

        List<StockMovement> movements = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                movements.add(new StockMovement(result.getLong("id"),
                        result.getObject("id_product", Integer.class), result.getString("product_name"),
                        result.getString("unit"), result.getString("movement_type"),
                        result.getLong("quantity"), result.getObject("id_shop", Integer.class),
                        result.getString("shop_name"), result.getObject("id_order", Integer.class),
                        result.getTimestamp("created_at").toInstant().toString()));
            }
        }

        return movements;
    }

    @Override
    public List<SupplyOrder> findOrders(OrderFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder(ORDER_SELECT);

        List<Object> parameters = new ArrayList<>();

        if (filter.shopId() != null) {
            sql.append("WHERE o.id_shop = ? ");
            parameters.add(filter.shopId());
        }
        if (filter.productId() != null) {
            sql.append(parameters.isEmpty() ? "WHERE " : "AND ").append("o.id_product = ? ");
            parameters.add(filter.productId());
        }
        if (filter.status() != null) {
            sql.append(parameters.isEmpty() ? "WHERE " : "AND ").append("o.status = ? ");
            parameters.add(filter.status());
        }
        if (filter.year() != null) {
            sql.append(parameters.isEmpty() ? "WHERE " : "AND ")
                    .append("EXTRACT(YEAR FROM o.date_order) = ? ");
            parameters.add(filter.year());
        }
        sql.append("ORDER BY o.id DESC");

        List<SupplyOrder> orders = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    orders.add(readOrder(result));
                }
            }
        }

        return orders;
    }

    @Override
    public SupplyOrder createOrder(int shopId, int productId, long quantity) throws SQLException {
        String sql = "INSERT INTO orders (id_shop, id_product, quantity_req) VALUES (?, ?, ?) RETURNING id";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setInt(2, productId);
            statement.setLong(3, quantity);

            try (ResultSet result = statement.executeQuery()) {
                result.next();

                return findOrder(connection, result.getInt("id"));
            }
        }
    }

    @Override
    public boolean deleteOrder(int orderId) throws SQLException {
        String sql = "DELETE FROM orders WHERE id = ? AND status = 'new' RETURNING id";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
            try (PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM orders WHERE id = ?")) {
                exists.setInt(1, orderId);

                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) {
                        throw new IllegalStateException("Удалить можно только новый заказ");
                    }
                }
            }

            return false;
        }
    }

    @Override
    public Optional<SupplyOrder> startOrder(int orderId) throws SQLException {
        String sql = "UPDATE orders SET status = 'in progress' "
                + "WHERE id = ? AND status = 'new' RETURNING id";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(findOrder(connection, orderId));
                }
            }
            try (PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM orders WHERE id = ?")) {
                exists.setInt(1, orderId);

                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) {
                        throw new IllegalStateException("В работу можно взять только новый заказ");
                    }
                }
            }

            return Optional.empty();
        }
    }

    @Override
    public Optional<SupplyOrder> fulfillOrder(int orderId) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                int shopId;
                int productId;
                long quantity;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id_shop, id_product, quantity_req, status FROM orders WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, orderId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();

                            return Optional.empty();
                        }
                        if (!"in progress".equals(result.getString("status"))) {
                            throw new IllegalStateException("Выполнить можно только заказ в работе");
                        }
                        shopId = result.getInt("id_shop");
                        productId = result.getInt("id_product");
                        quantity = result.getLong("quantity_req");
                    }
                }

                String productName;
                String unit;

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE products SET quantity = quantity - ? "
                                + "WHERE id = ? AND quantity >= ? RETURNING name, unit")) {
                    statement.setLong(1, quantity);
                    statement.setInt(2, productId);
                    statement.setLong(3, quantity);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Недостаточно товара на складе для выполнения заказа");
                        }
                        productName = result.getString("name");
                        unit = result.getString("unit");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO shop_products (id_shop, id_product, quantity) VALUES (?, ?, ?) "
                                + "ON CONFLICT (id_shop, id_product) DO UPDATE "
                                + "SET quantity = shop_products.quantity + EXCLUDED.quantity")) {
                    statement.setInt(1, shopId);
                    statement.setInt(2, productId);
                    statement.setLong(3, quantity);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET status = 'done' WHERE id = ?")) {
                    statement.setInt(1, orderId);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO stock_movements "
                                + "(id_product, product_name, unit, id_shop, shop_name, "
                                + "id_order, movement_type, quantity) "
                                + "VALUES (?, ?, ?, ?, (SELECT name FROM shops WHERE id = ?), ?, 'shipment', ?)")) {
                    statement.setInt(1, productId);
                    statement.setString(2, productName);
                    statement.setString(3, unit);
                    statement.setInt(4, shopId);
                    statement.setInt(5, shopId);
                    statement.setInt(6, orderId);
                    statement.setLong(7, quantity);
                    statement.executeUpdate();
                }

                SupplyOrder order = findOrder(connection, orderId);
                connection.commit();

                return Optional.of(order);
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

    private SupplyOrder findOrder(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ORDER_SELECT + "WHERE o.id = ?")) {
            statement.setInt(1, orderId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Созданный заказ не найден");
                }

                return readOrder(result);
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    private static Shop readShop(ResultSet result) throws SQLException {
        return new Shop(result.getInt("id"), result.getString("name"),
                result.getString("address"), result.getString("phone"));
    }

    private static SupplyOrder readOrder(ResultSet result) throws SQLException {
        return new SupplyOrder(result.getInt("id"), result.getInt("id_shop"),
                result.getString("shop_name"), result.getInt("id_product"),
                result.getString("product_name"), result.getLong("quantity_req"),
                result.getDate("date_order").toLocalDate().toString(), result.getString("status"));
    }
}
