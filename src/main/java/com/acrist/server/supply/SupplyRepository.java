package com.acrist.server.supply;

import com.acrist.shared.supply.OrderFilter;
import com.acrist.shared.supply.Shop;
import com.acrist.shared.supply.ShopStock;
import com.acrist.shared.supply.ShopSummary;
import com.acrist.shared.supply.StockMovement;
import com.acrist.shared.supply.SupplyOrder;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface SupplyRepository {
    List<Shop> findShops() throws SQLException;

    List<ShopSummary> findShopSummaries() throws SQLException;

    Shop createShop(String name, String address, String phone) throws SQLException;

    Optional<Shop> updateShop(int shopId, String name, String address, String phone) throws SQLException;

    boolean deleteShop(int shopId) throws SQLException;

    List<ShopStock> findShopStock(int shopId) throws SQLException;

    ShopStock decreaseShopStock(int shopId, int productId, long quantity) throws SQLException;

    List<StockMovement> findMovements() throws SQLException;

    List<SupplyOrder> findOrders(OrderFilter filter) throws SQLException;

    SupplyOrder createOrder(int shopId, int productId, long quantity) throws SQLException;

    boolean deleteOrder(int orderId) throws SQLException;

    Optional<SupplyOrder> startOrder(int orderId) throws SQLException;

    Optional<SupplyOrder> fulfillOrder(int orderId) throws SQLException;
}
