package com.acrist.supply;

import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

public class SupplyService {
    private static final Pattern PHONE = Pattern.compile("[-0-9+() ]{10,20}");
    private final SupplyRepository repository;

    public SupplyService(SupplyRepository repository) {
        this.repository = repository;
    }

    public List<Shop> findShops() throws SQLException {
        return repository.findShops();
    }

    public List<ShopSummary> findShopSummaries() throws SQLException {
        return repository.findShopSummaries();
    }

    public Shop createShop(String name, String address, String phone) throws SQLException {
        String cleanName = required(name, 100, "Название магазина");
        String cleanAddress = required(address, 200, "Адрес магазина");
        String cleanPhone = phone == null || phone.isBlank() ? null : phone.trim();

        if (cleanPhone != null && !PHONE.matcher(cleanPhone).matches()) {
            throw new IllegalArgumentException("Телефон должен содержать 10–20 допустимых символов");
        }

        return repository.createShop(cleanName, cleanAddress, cleanPhone);
    }

    public Shop updateShop(int shopId, String name, String address, String phone) throws SQLException {
        positiveId(shopId);
        String cleanName = required(name, 100, "Название магазина");
        String cleanAddress = required(address, 200, "Адрес магазина");
        String cleanPhone = phone == null || phone.isBlank() ? null : phone.trim();

        if (cleanPhone != null && !PHONE.matcher(cleanPhone).matches()) {
            throw new IllegalArgumentException("Телефон должен содержать 10–20 допустимых символов");
        }

        return repository.updateShop(shopId, cleanName, cleanAddress, cleanPhone)
                .orElseThrow(() -> new NoSuchElementException("Магазин не найден: " + shopId));
    }

    public void deleteShop(int shopId) throws SQLException {
        positiveId(shopId);

        if (!repository.deleteShop(shopId)) {
            throw new NoSuchElementException("Магазин не найден: " + shopId);
        }
    }

    public List<ShopStock> findShopStock(int shopId) throws SQLException {
        positiveId(shopId);

        return repository.findShopStock(shopId);
    }

    public ShopStock decreaseShopStock(int shopId, int productId, long quantity) throws SQLException {
        positiveId(shopId);
        positiveId(productId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество для списания должно быть положительным");
        }

        return repository.decreaseShopStock(shopId, productId, quantity);
    }

    public List<StockMovement> findMovements() throws SQLException {
        return repository.findMovements();
    }

    public List<SupplyOrder> findOrders(OrderFilter filter) throws SQLException {
        if (filter == null) {
            throw new IllegalArgumentException("Фильтр заказов не задан");
        }
        if (filter.shopId() != null) {
            positiveId(filter.shopId());
        }
        if (filter.productId() != null) {
            positiveId(filter.productId());
        }
        if (filter.status() != null && !List.of("new", "in progress", "done").contains(filter.status())) {
            throw new IllegalArgumentException("Неизвестный статус заказа");
        }
        if (filter.year() != null && (filter.year() < 1 || filter.year() > 9999)) {
            throw new IllegalArgumentException("Год должен быть от 1 до 9999");
        }

        return repository.findOrders(filter);
    }

    public SupplyOrder createOrder(int shopId, int productId, long quantity) throws SQLException {
        positiveId(shopId);
        positiveId(productId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество заказа должно быть больше нуля");
        }

        return repository.createOrder(shopId, productId, quantity);
    }

    public void deleteOrder(int orderId) throws SQLException {
        positiveId(orderId);

        if (!repository.deleteOrder(orderId)) {
            throw new NoSuchElementException("Заказ не найден: " + orderId);
        }
    }

    public SupplyOrder startOrder(int orderId) throws SQLException {
        positiveId(orderId);

        return repository.startOrder(orderId)
                .orElseThrow(() -> new NoSuchElementException("Заказ не найден: " + orderId));
    }

    public SupplyOrder fulfillOrder(int orderId) throws SQLException {
        positiveId(orderId);

        return repository.fulfillOrder(orderId)
                .orElseThrow(() -> new NoSuchElementException("Заказ не найден: " + orderId));
    }

    private static void positiveId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Некорректный ID");
        }
    }

    private static String required(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " не может быть пустым");
        }
        String clean = value.trim();

        if (clean.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + ": максимум " + maxLength + " символов");
        }

        return clean;
    }
}
