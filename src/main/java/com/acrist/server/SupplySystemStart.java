package com.acrist.server;

import com.acrist.server.auth.AuthService;
import com.acrist.server.auth.JdbcAuthRepository;
import com.acrist.server.config.DatabaseConfig;
import com.acrist.server.product.JdbcProductRepository;
import com.acrist.server.product.ProductService;
import com.acrist.server.supply.JdbcSupplyRepository;
import com.acrist.server.supply.SupplyService;
import com.acrist.server.web.SupplyHttpServer;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class SupplySystemStart {
    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.load(Path.of(".env"));
        JdbcProductRepository repository = new JdbcProductRepository(config);
        repository.verifyConnection();

        AuthService authService = new AuthService(new JdbcAuthRepository(config));
        authService.ensureInitialAdmin(System.getenv("SUPPLY_ADMIN_USERNAME"),
                        System.getenv("SUPPLY_ADMIN_PASSWORD"))
                .ifPresent(account -> {
                    System.out.println("Создан первый администратор: " + account.username());
                    if (account.generatedPassword() != null) {
                        System.out.println("Временный пароль администратора: " + account.generatedPassword());
                        System.out.println("Сохраните пароль: он показывается только при первом запуске.");
                    }
                });

        int port = Integer.parseInt(System.getenv().getOrDefault("SUPPLY_API_PORT", "8080"));
        HttpServer server = SupplyHttpServer.create(
                new InetSocketAddress("127.0.0.1", port),
                new ProductService(repository),
                new SupplyService(new JdbcSupplyRepository(config)),
                authService
        );

        server.setExecutor(Executors.newFixedThreadPool(10));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();

        System.out.println("API запущен: http://127.0.0.1:" + port + "/api/products");
    }
}
