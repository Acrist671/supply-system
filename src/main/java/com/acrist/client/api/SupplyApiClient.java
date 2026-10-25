package com.acrist.client.api;

import com.acrist.shared.auth.AppUser;
import com.acrist.shared.auth.LoginResponse;
import com.acrist.shared.product.Product;
import com.acrist.shared.supply.OrderFilter;
import com.acrist.shared.supply.Shop;
import com.acrist.shared.supply.ShopStock;
import com.acrist.shared.supply.ShopSummary;
import com.acrist.shared.supply.StockMovement;
import com.acrist.shared.supply.SupplyOrder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class SupplyApiClient {
    private static final Gson JSON = new Gson();

    private final HttpClient httpClient;
    private final URI productsUri;
    private final URI shopsUri;
    private final URI ordersUri;
    private final URI movementsUri;
    private final URI authUri;
    private final URI usersUri;
    private volatile String authToken;

    public SupplyApiClient(String apiBaseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        String apiRoot = apiBaseUrl.replaceAll("/+$", "") + "/api";
        this.productsUri = URI.create(apiRoot + "/products");
        this.shopsUri = URI.create(apiRoot + "/shops");
        this.ordersUri = URI.create(apiRoot + "/orders");
        this.movementsUri = URI.create(apiRoot + "/movements");
        this.authUri = URI.create(apiRoot + "/auth");
        this.usersUri = URI.create(apiRoot + "/users");
    }

    public CompletableFuture<AppUser> login(String username, String password) {
        authToken = null;

        return post(URI.create(authUri + "/login"), new LoginRequest(username, password), LoginResponse.class)
                .thenApply(response -> {
                    authToken = response.token();

                    return response.user();
                });
    }

    public CompletableFuture<Void> logout() {
        HttpRequest request = request(URI.create(authUri + "/logout"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    authToken = null;
                    requireSuccess(response);

                    return null;
                });
    }

    public CompletableFuture<List<AppUser>> findUsers() {
        return getList(usersUri, AppUser[].class);
    }

    public CompletableFuture<AppUser> createUser(String username, String password, String role) {
        return post(usersUri, new CreateUserRequest(username, password, role), AppUser.class);
    }

    public CompletableFuture<List<Product>> findAll() {
        return getList(productsUri, Product[].class);
    }

    public CompletableFuture<List<Shop>> findShops() {
        return getList(shopsUri, Shop[].class);
    }

    public CompletableFuture<List<ShopSummary>> findShopSummaries() {
        return getList(URI.create(shopsUri + "/summary"), ShopSummary[].class);
    }

    public CompletableFuture<Shop> createShop(String name, String address, String phone) {
        return post(shopsUri, new CreateShopRequest(name, address, phone), Shop.class);
    }

    public CompletableFuture<Shop> updateShop(int shopId, String name, String address, String phone) {
        return sendObject(URI.create(shopsUri + "/" + shopId),
                new CreateShopRequest(name, address, phone), Shop.class, "PUT");
    }

    public CompletableFuture<Void> deleteShop(int shopId) {
        return delete(URI.create(shopsUri + "/" + shopId));
    }

    public CompletableFuture<List<ShopStock>> findShopStock(int shopId) {
        return getList(URI.create(shopsUri + "/" + shopId + "/products"), ShopStock[].class);
    }

    public CompletableFuture<ShopStock> decreaseShopStock(int shopId, int productId, long quantity) {
        URI uri = URI.create(shopsUri + "/" + shopId + "/products/" + productId + "/writeoffs");

        return post(uri, new ShopWriteoffRequest(quantity), ShopStock.class);
    }

    public CompletableFuture<List<SupplyOrder>> findOrders(OrderFilter filter) {
        StringJoiner query = new StringJoiner("&");

        if (filter.shopId() != null) {
            query.add("shopId=" + filter.shopId());
        }

        if (filter.productId() != null) {
            query.add("productId=" + filter.productId());
        }

        if (filter.status() != null) {
            query.add("status=" + URLEncoder.encode(filter.status(), StandardCharsets.UTF_8));
        }

        if (filter.year() != null) {
            query.add("year=" + filter.year());
        }

        URI uri = query.length() == 0 ? ordersUri : URI.create(ordersUri + "?" + query);

        return getList(uri, SupplyOrder[].class);
    }

    public CompletableFuture<List<StockMovement>> findMovements() {
        return getList(movementsUri, StockMovement[].class);
    }

    public CompletableFuture<SupplyOrder> createOrder(int shopId, int productId, long quantity) {
        return post(ordersUri, new CreateOrderRequest(shopId, productId, quantity), SupplyOrder.class);
    }

    public CompletableFuture<Void> deleteOrder(int orderId) {
        return delete(URI.create(ordersUri + "/" + orderId));
    }

    public CompletableFuture<SupplyOrder> startOrder(int orderId) {
        return post(URI.create(ordersUri + "/" + orderId + "/start"), new Object(), SupplyOrder.class);
    }

    public CompletableFuture<SupplyOrder> fulfillOrder(int orderId) {
        return post(URI.create(ordersUri + "/" + orderId + "/fulfill"), new Object(), SupplyOrder.class);
    }

    private <T> CompletableFuture<List<T>> getList(URI uri, Class<T[]> arrayType) {
        HttpRequest request = request(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    requireSuccess(response);
                    T[] items = JSON.fromJson(response.body(), arrayType);

                    if (items == null) {
                        throw new ApiException("Сервер вернул пустой ответ");
                    }

                    return List.copyOf(Arrays.asList(items));
                });
    }

    public CompletableFuture<Product> create(String name, String unit, String category,
                                             BigDecimal price) {
        return post(productsUri, new CreateProductRequest(name, unit, category, price), Product.class);
    }

    public CompletableFuture<Product> updateProduct(int productId, String name, String unit,
                                                    String category, BigDecimal price) {
        return sendObject(URI.create(productsUri + "/" + productId),
                new UpdateProductRequest(name, unit, category, price), Product.class, "PUT");
    }

    public CompletableFuture<Boolean> canChangeUnit(int productId) {
        URI uri = URI.create(productsUri + "/" + productId + "/unit-change");
        HttpRequest request = request(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    requireSuccess(response);

                    return JsonParser.parseString(response.body()).getAsJsonObject()
                            .get("allowed").getAsBoolean();
                });
    }

    public CompletableFuture<Void> deleteProduct(int productId) {
        return delete(URI.create(productsUri + "/" + productId));
    }

    public CompletableFuture<Product> receive(int productId, long quantity) {
        URI receiptUri = URI.create(productsUri + "/" + productId + "/receipts");

        return post(receiptUri, new ReceiveProductRequest(quantity), Product.class);
    }

    private <T> CompletableFuture<T> post(URI uri, Object body, Class<T> responseType) {
        return sendObject(uri, body, responseType, "POST");
    }

    private <T> CompletableFuture<T> sendObject(URI uri, Object body,
                                                 Class<T> responseType, String method) {
        String json = JSON.toJson(body);
        HttpRequest request = request(uri)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    requireSuccess(response);
                    T value = JSON.fromJson(response.body(), responseType);

                    if (value == null) {
                        throw new ApiException("Сервер вернул пустой ответ");
                    }

                    return value;
                });
    }

    private CompletableFuture<Void> delete(URI uri) {
        HttpRequest request = request(uri)
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    requireSuccess(response);

                    return null;
                });
    }

    private static void requireSuccess(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        String message = "Ошибка сервера: HTTP " + response.statusCode();
        try {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();

            if (body.has("error") && body.get("error").isJsonPrimitive()) {
                message = body.get("error").getAsString();
            }
        } catch (RuntimeException ignored) {
            // The HTTP status remains available when the response is not JSON.
        }

        throw new ApiException(message);
    }

    private HttpRequest.Builder request(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));
        String token = authToken;

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder;
    }

    private record LoginRequest(String username, String password) {
    }

    private record CreateUserRequest(String username, String password, String role) {
    }

    private record CreateProductRequest(String name, String unit, String category, BigDecimal price) {
    }

    private record UpdateProductRequest(String name, String unit, String category, BigDecimal price) {
    }

    private record ReceiveProductRequest(long quantity) {
    }

    private record CreateShopRequest(String name, String address, String phone) {
    }

    private record CreateOrderRequest(int shopId, int productId, long quantity) {
    }

    private record ShopWriteoffRequest(long quantity) {
    }

    public static class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }
    }
}
