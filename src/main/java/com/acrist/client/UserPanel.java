package com.acrist.client;

import com.acrist.auth.AppUser;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;

class UserPanel extends JPanel {
    private final ProductApiClient apiClient;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Логин", "Роль"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }
    };
    private final JTable table = new JTable(model);
    private final JButton refreshButton = new JButton("Обновить");
    private final JButton addButton = new JButton("Добавить пользователя");
    private final JLabel statusLabel = new JLabel("Загрузка пользователей...");

    UserPanel(ProductApiClient apiClient) {
        super(new BorderLayout(0, 10));
        this.apiClient = apiClient;
        setBorder(new EmptyBorder(12, 12, 12, 12));
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolbar.add(refreshButton);
        toolbar.add(addButton);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        refreshButton.addActionListener(event -> refresh());
        addButton.addActionListener(event -> showAddDialog());
    }

    void refresh() {
        setBusy(true);
        showStatus("Загрузка пользователей...", false);
        apiClient.findUsers().whenComplete((users, error) -> SwingUtilities.invokeLater(() -> {
            setBusy(false);

            if (error != null) {
                showStatus(ProductFrame.message(error), true);
                return;
            }
            model.setRowCount(0);
            for (AppUser user : users) {
                model.addRow(new Object[]{user.id(), user.username(), roleName(user.role())});
            }
            showStatus("Пользователей: " + users.size(), false);
        }));
    }

    private void showAddDialog() {
        JTextField username = new JTextField(22);
        JPasswordField password = new JPasswordField(22);
        JComboBox<RoleChoice> role = new JComboBox<>(new RoleChoice[]{
                new RoleChoice("viewer", "Просмотр"),
                new RoleChoice("operator", "Оператор"),
                new RoleChoice("admin", "Администратор")});
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Логин:"));
        form.add(username);
        form.add(new JLabel("Пароль (не менее 8 символов):"));
        form.add(password);
        form.add(new JLabel("Роль:"));
        form.add(role);

        if (JOptionPane.showConfirmDialog(this, form, "Новый пользователь",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        char[] secret = password.getPassword();
        String passwordValue = new String(secret);
        Arrays.fill(secret, '\0');
        RoleChoice selectedRole = (RoleChoice) role.getSelectedItem();
        setBusy(true);
        showStatus("Создание пользователя...", false);
        apiClient.createUser(username.getText().trim(), passwordValue, selectedRole.value())
                .whenComplete((user, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(ProductFrame.message(error), true);
                        return;
                    }
                    refresh();
                }));
    }

    private void setBusy(boolean busy) {
        refreshButton.setEnabled(!busy);
        addButton.setEnabled(!busy);
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? new Color(170, 35, 35) : Color.DARK_GRAY);
    }

    private static String roleName(String value) {
        return switch (value) {
            case "admin" -> "Администратор";
            case "operator" -> "Оператор";
            case "viewer" -> "Просмотр";
            default -> value;
        };
    }

    private record RoleChoice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
