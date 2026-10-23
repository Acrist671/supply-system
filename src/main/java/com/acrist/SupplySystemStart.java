package com.acrist;

import com.acrist.auth.AuthService;
import com.acrist.auth.JdbcAuthRepository;
import com.acrist.config.DatabaseConfig;
import com.acrist.product.JdbcProductRepository;
import com.acrist.product.ProductService;
import com.acrist.supply.JdbcSupplyRepository;
import com.acrist.supply.SupplyService;
import com.acrist.web.ProductHttpServer;
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
        HttpServer server = ProductHttpServer.create(
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
