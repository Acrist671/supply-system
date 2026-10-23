package com.acrist.client;

import com.acrist.auth.AppUser;
import com.acrist.auth.Role;
import com.acrist.product.Product;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CompletionException;

public class ProductFrame extends JFrame {
    private static final Color ERROR_COLOR = new Color(170, 35, 35);
    private static final String[] COLUMNS = {
            "ID", "Название", "Ед.", "Категория", "Остаток", "Цена", "Стоимость", "Добавлен"
    };

    private final ProductApiClient apiClient;
    private final Role role;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JButton refreshButton = new JButton("Обновить");
    private final JButton addButton = new JButton("Добавить товар");
    private final JButton editButton = new JButton("Изменить");
    private final JButton deleteButton = new JButton("Удалить");
    private final JButton receiveButton = new JButton("Поступление");
    private final JLabel statusLabel = new JLabel("Загрузка товаров...");
    private final ShopPanel shopPanel;
    private final OrderPanel orderPanel;
    private final MovementPanel movementPanel;
    private final ShopSummaryPanel summaryPanel;
    private final UserPanel userPanel;
    private boolean busy;

    public ProductFrame(ProductApiClient apiClient, AppUser user, Runnable onLogout) {
        super("Система снабжения — " + user.username() + " (" + user.role() + ")");
        this.apiClient = apiClient;
        this.role = Role.fromValue(user.role());
        this.tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return switch (column) {
                    case 0 -> Integer.class;
                    case 4 -> Long.class;
                    case 5, 6 -> BigDecimal.class;
                    default -> String.class;
                };
            }
        };

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setMinimumSize(getSize());
        setLocationRelativeTo(null);

        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(event -> updateSelectionButtons());
        updateSelectionButtons();

        JLabel heading = new JLabel("Товары на складе");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        toolbar.add(refreshButton);
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(receiveButton);
        JPanel top = new JPanel(new BorderLayout());
        top.add(heading, BorderLayout.WEST);
        top.add(toolbar, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(statusLabel, BorderLayout.SOUTH);
        movementPanel = new MovementPanel(apiClient);
        summaryPanel = new ShopSummaryPanel(apiClient);
        userPanel = role == Role.ADMIN ? new UserPanel(apiClient) : null;
        shopPanel = new ShopPanel(apiClient, role, this::refreshOrders, movementPanel::refresh);
        orderPanel = new OrderPanel(apiClient, role, () -> {
            refreshProducts();
            shopPanel.refresh();
            movementPanel.refresh();
        });
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Товары", content);
        tabs.addTab("Магазины", shopPanel);
        tabs.addTab("Заказы", orderPanel);
        tabs.addTab("Движения", movementPanel);
        tabs.addTab("Сводка", summaryPanel);

        if (userPanel != null) {
            tabs.addTab("Пользователи", userPanel);
        }
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedComponent() == summaryPanel) {
                summaryPanel.refresh();
            }
        });
        setContentPane(tabs);

        JMenuBar menuBar = new JMenuBar();
        JMenu sessionMenu = new JMenu("Сеанс");
        JMenuItem logoutItem = new JMenuItem("Сменить пользователя");
        logoutItem.addActionListener(event -> {
            logoutItem.setEnabled(false);
            apiClient.logout().whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                dispose();
                onLogout.run();
            }));
        });
        sessionMenu.add(logoutItem);
        menuBar.add(sessionMenu);
        setJMenuBar(menuBar);

        refreshButton.addActionListener(event -> refreshProducts());
        addButton.addActionListener(event -> new AddProductDialog(this, apiClient).setVisible(true));
        editButton.addActionListener(event -> showEditDialog());
        deleteButton.addActionListener(event -> deleteSelected());
        receiveButton.addActionListener(event -> showReceiveDialog());
        setBusy(false);
    }

    public void refreshProducts() {
        refreshProducts(null);
    }

    public void refreshAll() {
        refreshProducts();
        shopPanel.refresh();
        orderPanel.refresh();
        movementPanel.refresh();
        summaryPanel.refresh();

        if (userPanel != null) {
            userPanel.refresh();
        }
    }

    private void refreshOrders() {
        orderPanel.refresh();
    }

    void productCreated(Product product) {
        refreshProducts("Товар «" + product.name() + "» добавлен");
    }

    void productReceived(Product product, long amount) {
        refreshProducts("Поступило " + amount + " " + product.unit() + " товара «" + product.name() + "»");
        movementPanel.refresh();
    }

    private void showReceiveDialog() {
        int selectedRow = table.getSelectedRow();

        if (selectedRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        int productId = (Integer) tableModel.getValueAt(modelRow, 0);
        String productName = (String) tableModel.getValueAt(modelRow, 1);
        String unit = (String) tableModel.getValueAt(modelRow, 2);
        long currentQuantity = (Long) tableModel.getValueAt(modelRow, 4);
        new ReceiveProductDialog(this, apiClient, productId, productName, unit, currentQuantity)
                .setVisible(true);
    }

    private void showEditDialog() {
        int row = selectedModelRow();

        if (row < 0) {
            return;
        }
        int productId = (Integer) tableModel.getValueAt(row, 0);
        String currentName = (String) tableModel.getValueAt(row, 1);
        String currentUnit = (String) tableModel.getValueAt(row, 2);
        String currentCategory = (String) tableModel.getValueAt(row, 3);
        BigDecimal currentPrice = (BigDecimal) tableModel.getValueAt(row, 5);
        setBusy(true);
        showStatus("Проверка товара...", false);
        apiClient.canChangeUnit(productId).whenComplete((allowed, error) -> SwingUtilities.invokeLater(() -> {
            setBusy(false);

            if (error != null) {
                showStatus(message(error), true);
                return;
            }
            openEditDialog(productId, currentName, currentUnit, currentCategory, currentPrice, allowed);
        }));
    }

    private void openEditDialog(int productId, String currentName, String currentUnit,
                                String currentCategory, BigDecimal currentPrice, boolean unitEditable) {
        JTextField name = new JTextField(currentName, 22);
        JComboBox<String> unit = new JComboBox<>(new String[]{"шт", "кг", "л", "м", "уп"});
        unit.setSelectedItem(currentUnit);
        unit.setEnabled(unitEditable);
        JTextField category = new JTextField(currentCategory, 22);
        JTextField price = new JTextField(currentPrice.toPlainString(), 22);
        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Название:"));
        form.add(name);
        form.add(new JLabel("Единица измерения:"));
        form.add(unit);
        form.add(new JLabel("Категория:"));
        form.add(category);
        form.add(new JLabel("Цена за единицу:"));
        form.add(price);
        JPanel dialogContent = new JPanel(new BorderLayout(0, 8));
        dialogContent.add(form, BorderLayout.CENTER);
        dialogContent.add(new JLabel("Единицу можно менять только без остатков, товаров в магазинах и заказов."),
                BorderLayout.SOUTH);

        if (JOptionPane.showConfirmDialog(this, dialogContent, "Изменить товар",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        BigDecimal newPrice;
        try {
            newPrice = new BigDecimal(price.getText().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            showStatus("Введите корректную цену", true);
            return;
        }
        setBusy(true);
        showStatus("Сохранение товара...", false);
        apiClient.updateProduct(productId, name.getText(), (String) unit.getSelectedItem(),
                        category.getText(), newPrice)
                .whenComplete((product, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(message(error), true);
                        return;
                    }
                    refreshProducts("Товар «" + product.name() + "» изменён");
                    shopPanel.refresh();
                    orderPanel.refresh();
                }));
    }

    private void deleteSelected() {
        int row = selectedModelRow();

        if (row < 0) {
            return;
        }
        int productId = (Integer) tableModel.getValueAt(row, 0);
        String productName = (String) tableModel.getValueAt(row, 1);

        if (JOptionPane.showConfirmDialog(this, "Удалить товар «" + productName + "»?",
                "Удаление товара", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true);
        showStatus("Удаление товара...", false);
        apiClient.deleteProduct(productId).whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            setBusy(false);

            if (error != null) {
                showStatus(message(error), true);
                return;
            }
            refreshProducts("Товар «" + productName + "» удалён");
        }));
    }

    private int selectedModelRow() {
        int viewRow = table.getSelectedRow();

        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
    }

    private void refreshProducts(String successMessage) {
        setBusy(true);
        showStatus("Загрузка товаров...", false);
        apiClient.findAll().whenComplete((products, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                setBusy(false);
                showStatus(message(error), true);
                return;
            }
            showProducts(products);
            setBusy(false);
            String count = "Товаров: " + products.size();
            showStatus(successMessage == null ? count : successMessage + ". " + count, false);
        }));
    }

    private void showProducts(List<Product> products) {
        int selectedRow = selectedModelRow();
        int selectedId = selectedRow < 0 ? -1 : (Integer) tableModel.getValueAt(selectedRow, 0);
        tableModel.setRowCount(0);
        for (Product product : products) {
            tableModel.addRow(new Object[]{
                    product.id(), product.name(), product.unit(), product.category(),
                    product.quantity(), product.price(),
                    BigDecimal.valueOf(product.quantity()).multiply(product.price()), product.dateAdded()
            });
        }
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if ((Integer) tableModel.getValueAt(row, 0) == selectedId) {
                int viewRow = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(viewRow, viewRow);
                break;
            }
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshButton.setEnabled(!busy);
        addButton.setEnabled(!busy && role.canWrite());
        updateSelectionButtons();
    }

    private void updateSelectionButtons() {
        boolean enabled = !busy && table.getSelectedRow() >= 0;
        receiveButton.setEnabled(enabled && role.canWrite());
        editButton.setEnabled(enabled && role.canWrite());
        deleteButton.setEnabled(enabled && role.canDelete());
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.setForeground(error ? ERROR_COLOR : Color.DARK_GRAY);
    }

    static String message(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ConnectException || cause instanceof HttpTimeoutException) {
            return "Нет связи с сервером. Проверьте, что API запущен.";
        }

        return cause.getMessage() == null ? "Не удалось выполнить запрос" : cause.getMessage();
    }
}
