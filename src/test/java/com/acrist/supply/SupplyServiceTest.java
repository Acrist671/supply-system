package com.acrist.supply;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupplyServiceTest {
    private final FakeRepository repository = new FakeRepository();
    private final SupplyService service = new SupplyService(repository);

    @Test
    void createsShopWithTrimmedFieldsAndOptionalPhone() throws SQLException {
        Shop shop = service.createShop("  Центральный  ", "  Ленина, 1  ", " ");

        assertEquals("Центральный", shop.name());
        assertEquals("Ленина, 1", shop.address());
        assertNull(shop.phone());
    }

    @Test
    void rejectsInvalidPhoneBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createShop("Магазин", "Адрес", "123"));
        assertEquals(0, repository.createShopCalls);
    }

    @Test
    void createsLargeOrderWithoutNarrowingQuantity() throws SQLException {
        long quantity = (long) Integer.MAX_VALUE + 10;

        SupplyOrder order = service.createOrder(1, 2, quantity);

        assertEquals(quantity, order.quantity());
        assertEquals(1, repository.createOrderCalls);
    }

    @Test
    void rejectsNonPositiveOrderBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class, () -> service.createOrder(1, 2, 0));
        assertEquals(0, repository.createOrderCalls);
    }

    @Test
    void forwardsOrderFiltersToRepository() throws SQLException {
        OrderFilter filter = new OrderFilter(1, 2, "in progress", 2026);

        service.findOrders(filter);

        assertEquals(filter, repository.lastOrderFilter);
    }

    @Test
    void rejectsInvalidOrderFiltersBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.findOrders(new OrderFilter(0, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.findOrders(new OrderFilter(null, -1, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.findOrders(new OrderFilter(null, null, "unknown", null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.findOrders(new OrderFilter(null, null, null, 0)));
        assertNull(repository.lastOrderFilter);
    }

    @Test
    void reportsMissingOrder() {
        assertThrows(NoSuchElementException.class, () -> service.fulfillOrder(99));
    }

    @Test
    void deletesNewOrder() throws SQLException {
        service.deleteOrder(1);

        assertEquals(1, repository.deleteOrderCalls);
    }

    @Test
    void rejectsInvalidOrderIdBeforeDeleting() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteOrder(0));
        assertEquals(0, repository.deleteOrderCalls);
    }

    @Test
    void reportsMissingOrderOnDelete() {
        assertThrows(NoSuchElementException.class, () -> service.deleteOrder(99));
    }

    @Test
    void refusesToDeleteOrderInProgress() {
        assertThrows(IllegalStateException.class, () -> service.deleteOrder(2));
    }

    @Test
    void startsOrderAndReturnsInProgressStatus() throws SQLException {
        SupplyOrder order = service.startOrder(1);

        assertEquals("in progress", order.status());
        assertEquals(1, repository.startOrderCalls);
    }

    @Test
    void rejectsInvalidOrderIdBeforeStarting() {
        assertThrows(IllegalArgumentException.class, () -> service.startOrder(0));
        assertEquals(0, repository.startOrderCalls);
    }

    @Test
    void reportsMissingOrderOnStart() {
        assertThrows(NoSuchElementException.class, () -> service.startOrder(99));
    }

    @Test
    void updatesShopWithValidatedFields() throws SQLException {
        Shop shop = service.updateShop(1, "  Южный  ", "  Невский, 1  ", " ");

        assertEquals("Южный", shop.name());
        assertEquals("Невский, 1", shop.address());
        assertNull(shop.phone());
        assertEquals(1, repository.updateShopCalls);
    }

    @Test
    void rejectsInvalidShopUpdateBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateShop(1, "Магазин", "Адрес", "123"));
        assertEquals(0, repository.updateShopCalls);
    }

    @Test
    void reportsMissingShopOnDelete() {
        assertThrows(NoSuchElementException.class, () -> service.deleteShop(99));
    }

    @Test
    void passesPositiveShopWriteoffToRepository() throws SQLException {
        ShopStock stock = service.decreaseShopStock(1, 2, 3);

        assertEquals(2L, stock.quantity());
        assertEquals(3L, repository.lastWriteoff);
    }

    @Test
    void rejectsZeroAndNegativeShopWriteoff() {
        assertThrows(IllegalArgumentException.class, () -> service.decreaseShopStock(1, 2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.decreaseShopStock(1, 2, -3));
        assertEquals(0, repository.writeoffCalls);
    }

    private static class FakeRepository implements SupplyRepository {
        int createShopCalls;
        int createOrderCalls;
        int startOrderCalls;
        int deleteOrderCalls;
        int updateShopCalls;
        int writeoffCalls;
        long lastWriteoff;
        OrderFilter lastOrderFilter;

        @Override
        public List<Shop> findShops() {
            return List.of();
        }

        @Override
        public List<ShopSummary> findShopSummaries() {
            return List.of(new ShopSummary(1, "Магазин", 0,
                    BigDecimal.ZERO, BigDecimal.ZERO));
        }

        @Override
        public Shop createShop(String name, String address, String phone) {
            createShopCalls++;
            return new Shop(1, name, address, phone);
        }

        @Override
        public Optional<Shop> updateShop(int shopId, String name, String address, String phone) {
            updateShopCalls++;
            return shopId == 1 ? Optional.of(new Shop(shopId, name, address, phone)) : Optional.empty();
        }

        @Override
        public boolean deleteShop(int shopId) {
            return shopId == 1;
        }

        @Override
        public List<ShopStock> findShopStock(int shopId) {
            return List.of();
        }

        @Override
        public ShopStock decreaseShopStock(int shopId, int productId, long quantity) {
            writeoffCalls++;
            lastWriteoff = quantity;
            return new ShopStock(productId, "Товар", "шт", 5 - quantity);
        }

        @Override
        public List<StockMovement> findMovements() {
            return List.of();
        }

        @Override
        public List<SupplyOrder> findOrders(OrderFilter filter) {
            lastOrderFilter = filter;
            return List.of();
        }

        @Override
        public SupplyOrder createOrder(int shopId, int productId, long quantity) {
            createOrderCalls++;
            return new SupplyOrder(1, shopId, "Магазин", productId, "Товар",
                    quantity, "2026-10-23", "new");
        }

        @Override
        public boolean deleteOrder(int orderId) {
            deleteOrderCalls++;
            if (orderId == 2) {
                throw new IllegalStateException("Удалить можно только новый заказ");
            }
            return orderId == 1;
        }

        @Override
        public Optional<SupplyOrder> startOrder(int orderId) {
            startOrderCalls++;
            return orderId == 1
                    ? Optional.of(new SupplyOrder(1, 1, "Магазин", 2, "Товар",
                    5, "2026-10-23", "in progress"))
                    : Optional.empty();
        }

        @Override
        public Optional<SupplyOrder> fulfillOrder(int orderId) {
            return Optional.empty();
        }
    }
}
