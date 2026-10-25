package com.acrist.server.web;

import com.acrist.server.auth.AccessPolicy;
import com.acrist.server.auth.AuthService;
import com.acrist.server.auth.ForbiddenException;
import com.acrist.server.auth.UnauthorizedException;
import com.acrist.server.product.ProductNotFoundException;
import com.acrist.server.product.ProductService;
import com.acrist.server.supply.SupplyService;
import com.acrist.shared.auth.AppUser;
import com.acrist.shared.auth.Role;
import com.acrist.shared.product.Product;
import com.acrist.shared.supply.OrderFilter;
import com.acrist.shared.supply.Shop;
import com.acrist.shared.supply.SupplyOrder;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SupplyHttpServer {
    private static final int MAX_REQUEST_BYTES = 8192;
    private static final Set<String> ORDER_FILTER_FIELDS = Set.of("shopId", "productId", "status", "year");
    private static final Pattern RECEIPT_PATH = Pattern.compile("^/api/products/([1-9][0-9]*)/receipts$");
    private static final Pattern PRODUCT_PATH = Pattern.compile("^/api/products/([1-9][0-9]*)$");
    private static final Pattern UNIT_CHANGE_PATH = Pattern.compile("^/api/products/([1-9][0-9]*)/unit-change$");
    private static final Pattern SHOP_STOCK_PATH = Pattern.compile("^/api/shops/([1-9][0-9]*)/products$");
    private static final Pattern SHOP_WRITEOFF_PATH = Pattern.compile(
            "^/api/shops/([1-9][0-9]*)/products/([1-9][0-9]*)/writeoffs$");
    private static final Pattern SHOP_PATH = Pattern.compile("^/api/shops/([1-9][0-9]*)$");
    private static final Pattern ORDER_PATH = Pattern.compile("^/api/orders/([1-9][0-9]*)$");
    private static final Pattern START_ORDER_PATH = Pattern.compile("^/api/orders/([1-9][0-9]*)/start$");
    private static final Pattern FULFILL_PATH = Pattern.compile("^/api/orders/([1-9][0-9]*)/fulfill$");
    private static final Gson JSON = new Gson();

    private SupplyHttpServer() {
    }

    public static HttpServer create(InetSocketAddress address, ProductService productService,
                                    SupplyService supplyService, AuthService authService) throws IOException {
        HttpServer server = HttpServer.create(address, 0);
        server.createContext("/api", exchange -> handle(exchange, productService, supplyService, authService));

        return server;
    }

    private static void handle(HttpExchange exchange, ProductService productService,
                               SupplyService supplyService, AuthService authService) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();

            if ("/api/auth/login".equals(path)) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                JsonObject request = readJsonObject(exchange);

                if (request != null) {
                    respond(exchange, 200, authService.login(
                            stringField(request, "username"), stringField(request, "password")));
                }

                return;
            }

            AppUser currentUser = authorize(exchange, authService, path);

            if ("/api/auth/me".equals(path)) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    respond(exchange, 200, currentUser);
                } else {
                    methodNotAllowed(exchange, "GET");
                }

                return;
            }

            if ("/api/auth/logout".equals(path)) {
                if ("POST".equals(exchange.getRequestMethod())) {
                    authService.logout(exchange.getRequestHeaders().getFirst("Authorization"));
                    respondNoContent(exchange);
                } else {
                    methodNotAllowed(exchange, "POST");
                }

                return;
            }

            if ("/api/users".equals(path)) {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> respond(exchange, 200, authService.findUsers());
                    case "POST" -> createUser(exchange, authService);
                    default -> methodNotAllowed(exchange, "GET, POST");
                }

                return;
            }

            if ("/api/products".equals(path)) {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> respond(exchange, 200, productService.findAll());
                    case "POST" -> createProduct(exchange, productService);
                    default -> {
                        exchange.getResponseHeaders().set("Allow", "GET, POST");
                        respond(exchange, 405, Map.of("error", "Метод не поддерживается"));
                    }
                }

                return;
            }

            Matcher receiptPath = RECEIPT_PATH.matcher(path);

            if (receiptPath.matches()) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    respond(exchange, 405, Map.of("error", "Метод не поддерживается"));
                    return;
                }

                receiveProduct(exchange, productService, parseId(receiptPath.group(1)));
                return;
            }

            Matcher unitChangePath = UNIT_CHANGE_PATH.matcher(path);

            if (unitChangePath.matches()) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "GET");
                    return;
                }

                respond(exchange, 200, Map.of("allowed",
                        productService.canChangeUnit(parseId(unitChangePath.group(1)))));
                return;
            }

            Matcher productPath = PRODUCT_PATH.matcher(path);

            if (productPath.matches()) {
                int productId = parseId(productPath.group(1));
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> updateProduct(exchange, productService, productId);
                    case "DELETE" -> {
                        productService.delete(productId);
                        respondNoContent(exchange);
                    }
                    default -> methodNotAllowed(exchange, "PUT, DELETE");
                }

                return;
            }

            if ("/api/shops".equals(path)) {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> respond(exchange, 200, supplyService.findShops());
                    case "POST" -> createShop(exchange, supplyService);
                    default -> methodNotAllowed(exchange, "GET, POST");
                }

                return;
            }

            if ("/api/shops/summary".equals(path)) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "GET");
                    return;
                }

                respond(exchange, 200, supplyService.findShopSummaries());
                return;
            }

            Matcher shopStockPath = SHOP_STOCK_PATH.matcher(path);

            if (shopStockPath.matches()) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "GET");
                    return;
                }

                respond(exchange, 200, supplyService.findShopStock(parseId(shopStockPath.group(1))));
                return;
            }

            Matcher shopWriteoffPath = SHOP_WRITEOFF_PATH.matcher(path);

            if (shopWriteoffPath.matches()) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }

                decreaseShopStock(exchange, supplyService, parseId(shopWriteoffPath.group(1)),
                        parseId(shopWriteoffPath.group(2)));
                return;
            }

            Matcher shopPath = SHOP_PATH.matcher(path);

            if (shopPath.matches()) {
                int shopId = parseId(shopPath.group(1));
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> updateShop(exchange, supplyService, shopId);
                    case "DELETE" -> {
                        supplyService.deleteShop(shopId);
                        respondNoContent(exchange);
                    }
                    default -> methodNotAllowed(exchange, "PUT, DELETE");
                }

                return;
            }

            if ("/api/movements".equals(path)) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "GET");
                    return;
                }

                respond(exchange, 200, supplyService.findMovements());
                return;
            }

            if ("/api/orders".equals(path)) {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> respond(exchange, 200,
                            supplyService.findOrders(orderFilter(exchange.getRequestURI().getRawQuery())));
                    case "POST" -> createOrder(exchange, supplyService);
                    default -> methodNotAllowed(exchange, "GET, POST");
                }

                return;
            }

            Matcher orderPath = ORDER_PATH.matcher(path);

            if (orderPath.matches()) {
                if (!"DELETE".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "DELETE");
                    return;
                }

                supplyService.deleteOrder(parseId(orderPath.group(1)));
                respondNoContent(exchange);
                return;
            }

            Matcher startOrderPath = START_ORDER_PATH.matcher(path);

            if (startOrderPath.matches()) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }

                SupplyOrder order = supplyService.startOrder(parseId(startOrderPath.group(1)));
                respond(exchange, 200, order);
                return;
            }

            Matcher fulfillPath = FULFILL_PATH.matcher(path);

            if (fulfillPath.matches()) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }

                SupplyOrder order = supplyService.fulfillOrder(parseId(fulfillPath.group(1)));
                respond(exchange, 200, order);
                return;
            }

            respond(exchange, 404, Map.of("error", "Адрес не найден"));
        } catch (UnauthorizedException exception) {
            respond(exchange, 401, Map.of("error", exception.getMessage()));
        } catch (ForbiddenException exception) {
            respond(exchange, 403, Map.of("error", exception.getMessage()));
        } catch (ProductNotFoundException | NoSuchElementException exception) {
            respond(exchange, 404, Map.of("error", exception.getMessage()));
        } catch (IllegalStateException exception) {
            respond(exchange, 409, Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException | JsonParseException exception) {
            respond(exchange, 400, Map.of("error", Objects.toString(exception.getMessage(), "Некорректный запрос")));
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                respond(exchange, 409, Map.of("error", "/api/users".equals(exchange.getRequestURI().getPath())
                        ? "Пользователь с таким логином уже существует"
                        : "Запись с таким названием уже существует"));
            } else if ("22003".equals(exception.getSQLState())) {
                respond(exchange, 409, Map.of("error", "Превышен максимально допустимый остаток"));
            } else if ("23503".equals(exception.getSQLState())) {
                if ("DELETE".equals(exchange.getRequestMethod())) {
                    respond(exchange, 409, Map.of("error", "Запись используется и не может быть удалена"));
                } else {
                    respond(exchange, 400, Map.of("error", "Магазин или товар не найден"));
                }
            } else {
                exception.printStackTrace();
                respond(exchange, 500, Map.of("error", "Ошибка базы данных"));
            }
        } finally {
            exchange.close();
        }
    }

    private static AppUser authorize(HttpExchange exchange, AuthService authService, String path) {
        AppUser user = authService.requireUser(exchange.getRequestHeaders().getFirst("Authorization"));
        AccessPolicy.require(Role.fromValue(user.role()), exchange.getRequestMethod(), path);

        return user;
    }

    private static void createUser(HttpExchange exchange, AuthService authService)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        respond(exchange, 201, authService.createUser(stringField(request, "username"),
                stringField(request, "password"), Role.fromValue(stringField(request, "role"))));
    }

    private static void createProduct(HttpExchange exchange, ProductService service)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        Product product = service.create(
                stringField(request, "name"),
                stringField(request, "unit"),
                stringField(request, "category"),
                request.has("price") ? decimalField(request, "price") : null
        );
        respond(exchange, 201, product);
    }

    private static void receiveProduct(HttpExchange exchange, ProductService service, int productId)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        Product product = service.receive(productId, longField(request, "quantity"));
        respond(exchange, 200, product);
    }

    private static void updateProduct(HttpExchange exchange, ProductService service, int productId)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        Product product = service.update(productId, stringField(request, "name"),
                stringField(request, "unit"), stringField(request, "category"),
                request.has("price") ? decimalField(request, "price") : null);
        respond(exchange, 200, product);
    }

    private static void createShop(HttpExchange exchange, SupplyService service)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        JsonElement phone = request.get("phone");
        Shop shop = service.createShop(stringField(request, "name"),
                stringField(request, "address"), phone == null || phone.isJsonNull()
                        ? null : stringField(request, "phone"));
        respond(exchange, 201, shop);
    }

    private static void updateShop(HttpExchange exchange, SupplyService service, int shopId)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        JsonElement phone = request.get("phone");
        Shop shop = service.updateShop(shopId, stringField(request, "name"),
                stringField(request, "address"), phone == null || phone.isJsonNull()
                        ? null : stringField(request, "phone"));
        respond(exchange, 200, shop);
    }

    private static void decreaseShopStock(HttpExchange exchange, SupplyService service,
                                        int shopId, int productId) throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        respond(exchange, 200, service.decreaseShopStock(shopId, productId,
                longField(request, "quantity")));
    }

    private static void createOrder(HttpExchange exchange, SupplyService service)
            throws IOException, SQLException {
        JsonObject request = readJsonObject(exchange);

        if (request == null) {
            return;
        }
        SupplyOrder order = service.createOrder(intField(request, "shopId"),
                intField(request, "productId"), longField(request, "quantity"));
        respond(exchange, 201, order);
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        respond(exchange, 405, Map.of("error", "Метод не поддерживается"));
    }

    private static int parseId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректный ID");
        }
    }

    private static OrderFilter orderFilter(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return OrderFilter.all();
        }
        Map<String, String> fields = new HashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";

            if (!ORDER_FILTER_FIELDS.contains(name)
                    || value.isBlank() || fields.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Некорректный фильтр заказов: " + name);
            }
        }

        return new OrderFilter(filterNumber(fields, "shopId"), filterNumber(fields, "productId"),
                fields.get("status"), filterNumber(fields, "year"));
    }

    private static Integer filterNumber(Map<String, String> fields, String name) {
        String value = fields.get(name);

        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Поле " + name + " должно быть целым числом");
        }
    }

    private static JsonObject readJsonObject(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            respond(exchange, 415, Map.of("error", "Ожидается Content-Type: application/json"));

            return null;
        }

        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);

        if (body.length > MAX_REQUEST_BYTES) {
            respond(exchange, 413, Map.of("error", "Запрос слишком большой"));

            return null;
        }

        JsonElement element = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));

        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Ожидается JSON-объект");
        }

        return element.getAsJsonObject();
    }

    private static String stringField(JsonObject object, String field) {
        JsonElement element = object.get(field);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть строкой");
        }

        return element.getAsString();
    }

    private static long longField(JsonObject object, String field) {
        JsonElement element = object.get(field);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть целым числом");
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть целым числом");
        }
    }

    private static BigDecimal decimalField(JsonObject object, String field) {
        JsonElement element = object.get(field);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть числом");
        }
        try {
            return element.getAsBigDecimal();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть числом");
        }
    }

    private static int intField(JsonObject object, String field) {
        long value = longField(object, field);

        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Поле " + field + " вне допустимого диапазона");
        }

        return (int) value;
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void respondNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }
}
