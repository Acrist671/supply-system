package com.acrist.client.ui.auth;

import com.acrist.client.api.SupplyApiClient;
import com.acrist.client.ui.SupplyFrame;
import com.acrist.shared.auth.AppUser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class LoginDialog extends JDialog {
    private final SupplyApiClient apiClient;
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JButton loginButton = new JButton("Войти");
    private final JButton cancelButton = new JButton("Отмена");
    private final JLabel messageLabel = new JLabel(" ");
    private AppUser user;

    public LoginDialog(SupplyApiClient apiClient) {
        super((java.awt.Frame) null, "Вход в систему снабжения", ModalityType.APPLICATION_MODAL);
        this.apiClient = apiClient;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 10));
        fields.add(new JLabel("Логин:"));
        fields.add(usernameField);
        fields.add(new JLabel("Пароль:"));
        fields.add(passwordField);

        messageLabel.setForeground(new Color(170, 35, 35));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancelButton);
        actions.add(loginButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(messageLabel, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(18, 18, 14, 18));
        content.add(fields, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        loginButton.addActionListener(event -> login());
        cancelButton.addActionListener(event -> dispose());
        getRootPane().setDefaultButton(loginButton);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    public AppUser loggedInUser() {
        return user;
    }

    private void login() {
        char[] secret = passwordField.getPassword();
        String password = new String(secret);
        Arrays.fill(secret, '\0');

        setBusy(true);
        messageLabel.setText("Проверка логина...");

        apiClient.login(usernameField.getText().trim(), password)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        passwordField.setText("");
                        messageLabel.setText(SupplyFrame.message(error));
                        return;
                    }

                    user = result;
                    dispose();
                }));
    }

    private void setBusy(boolean busy) {
        usernameField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        loginButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        setDefaultCloseOperation(busy ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
    }
}
