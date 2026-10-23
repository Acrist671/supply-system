package com.acrist.client;

import com.acrist.auth.AppUser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class SupplyDesktopStart {
    public static void main(String[] args) {
        String apiUrl = System.getenv().
                getOrDefault("SUPPLY_API_URL", "http://127.0.0.1:8080");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Не лучшая практика, но смысла отрабатывать исключения нет
            }

            showLogin(new ProductApiClient(apiUrl));
        });
    }

    private static void showLogin(ProductApiClient apiClient) {
        LoginDialog dialog = new LoginDialog(apiClient);
        dialog.setVisible(true);
        AppUser user = dialog.loggedInUser();

        if (user != null) {
            ProductFrame frame = new ProductFrame(apiClient, user, () -> showLogin(apiClient));
            frame.setVisible(true);
            frame.refreshAll();
        }
    }
}
