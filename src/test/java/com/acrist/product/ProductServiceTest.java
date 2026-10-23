package com.acrist.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceTest {
    private final FakeRepository repository = new FakeRepository();
    private final ProductService service = new ProductService(repository);

    @Test
    void createsProductWithTrimmedText() throws SQLException {
        Product product = service.create("  Молоко  ", "л", "  Продукты  ");

        assertEquals("Молоко", product.name());
        assertEquals("Продукты", product.category());
        assertEquals(0, product.price().compareTo(BigDecimal.ZERO));
        assertEquals(1, repository.createCalls);
    }

    @Test
    void createsProductWithPrice() throws SQLException {
        Product product = service.create("Молоко", "л", "Продукты", new BigDecimal("89.50"));

        assertEquals(new BigDecimal("89.50"), product.price());
    }

    @Test
    void rejectsInvalidPriceOnCreateBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("Молоко", "л", "Продукты", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("Молоко", "л", "Продукты", new BigDecimal("12.345")));
        assertEquals(0, repository.createCalls);
    }

    @Test
    void rejectsInvalidUnitBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("Молоко", "ящик", "Продукты"));
        assertEquals(0, repository.createCalls);
    }

    @Test
    void receivesPositiveAmount() throws SQLException {
        Product product = service.receive(1, 5);

        assertEquals(5L, product.quantity());
        assertEquals(1, repository.receiveCalls);
    }

    @Test
    void receivesAmountAboveIntegerLimit() throws SQLException {
        long amount = (long) Integer.MAX_VALUE + 1;

        Product product = service.receive(1, amount);

        assertEquals(amount, product.quantity());
        assertEquals(1, repository.receiveCalls);
    }

    @Test
    void rejectsNonPositiveReceiptBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class, () -> service.receive(1, 0));
        assertEquals(0, repository.receiveCalls);
    }

    @Test
    void reportsMissingProduct() {
        assertThrows(ProductNotFoundException.class, () -> service.receive(99, 5));
    }

    @Test
    void updatesProductWithValidatedFields() throws SQLException {
        Product product = service.update(1, "  Сыр  ", "кг", "  Продукты  ",
                new BigDecimal("125.50"));

        assertEquals("Сыр", product.name());
        assertEquals("Продукты", product.category());
        assertEquals(new BigDecimal("125.50"), product.price());
        assertEquals(1, repository.updateCalls);
    }

    @Test
    void preservesPriceWhenUpdateOmitsIt() throws SQLException {
        Product product = service.update(1, "Сыр", "кг", "Продукты");

        assertEquals(new BigDecimal("7.00"), product.price());
    }

    @Test
    void rejectsInvalidPriceBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update(1, "Сыр", "кг", "Продукты", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(1, "Сыр", "кг", "Продукты", new BigDecimal("1.234")));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(1, "Сыр", "кг", "Продукты", new BigDecimal("100000")));
        assertEquals(0, repository.updateCalls);
    }

    @Test
    void rejectsInvalidUpdateBeforeDatabaseCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update(1, "Сыр", "ящик", "Продукты"));
        assertEquals(0, repository.updateCalls);
    }

    @Test
    void reportsMissingProductOnDelete() {
        assertThrows(ProductNotFoundException.class, () -> service.delete(99));
    }

    private static class FakeRepository implements ProductRepository {
        int createCalls;
        int receiveCalls;
        int updateCalls;

        @Override
        public List<Product> findAll() {
            return List.of();
        }

        @Override
        public Product create(String name, String unit, String category, BigDecimal price) {
            createCalls++;
            return new Product(1, name, unit, category, 0, price, "2026-10-22");
        }

        @Override
        public Optional<Product> update(int productId, String name, String unit, String category,
                                        BigDecimal price) {
            updateCalls++;
            return productId == 1 ? Optional.of(new Product(productId, name, unit, category,
                    0, price == null ? new BigDecimal("7.00") : price, "2026-10-22")) : Optional.empty();
        }

        @Override
        public Optional<Boolean> canChangeUnit(int productId) {
            return productId == 1 ? Optional.of(true) : Optional.empty();
        }

        @Override
        public boolean delete(int productId) {
            return productId == 1;
        }

        @Override
        public Optional<Product> receive(int productId, long amount) {
            receiveCalls++;
            if (productId != 1) {
                return Optional.empty();
            }
            return Optional.of(new Product(1, "Молоко", "л", "Продукты",
                    amount, BigDecimal.ZERO, "2026-10-22"));
        }
    }
}
