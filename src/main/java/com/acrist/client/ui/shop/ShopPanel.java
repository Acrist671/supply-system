package com.acrist.client.ui.shop;

import com.acrist.client.api.SupplyApiClient;
import com.acrist.client.ui.SupplyFrame;
import com.acrist.shared.auth.Role;
import com.acrist.shared.supply.Shop;
import com.acrist.shared.supply.ShopStock;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.ParseException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class ShopPanel extends JPanel {
    private final SupplyApiClient apiClient;
    private final Role role;
    private final Runnable onChanged;
    private final Runnable onStockAdjusted;
    private final DefaultTableModel shopModel = new DefaultTableModel(
            new String[]{"ID", "Название", "Адрес", "Телефон"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }
    };

    private final DefaultTableModel stockModel = new DefaultTableModel(
            new String[]{"ID товара", "Товар", "Ед.", "Остаток"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0 -> Integer.class;
                case 3 -> Long.class;
                default -> String.class;
            };
        }
    };

    private final JTable shopTable = new JTable(shopModel);
    private final JTable stockTable = new JTable(stockModel);
    private final JButton refreshButton = new JButton("Обновить");
    private final JButton addButton = new JButton("Добавить магазин");
    private final JButton editButton = new JButton("Изменить");
    private final JButton deleteButton = new JButton("Удалить");
    private final JButton writeOffButton = new JButton("Списать товар");
    private final JLabel statusLabel = new JLabel("Загрузка магазинов...");
    private boolean busy;

    public ShopPanel(SupplyApiClient apiClient, Role role, Runnable onChanged, Runnable onStockAdjusted) {
        super(new BorderLayout(0, 10));
        this.apiClient = apiClient;
        this.role = role;
        this.onChanged = onChanged;
        this.onStockAdjusted = onStockAdjusted;

        setBorder(new EmptyBorder(12, 12, 12, 12));
        shopTable.setAutoCreateRowSorter(true);
        shopTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shopTable.setRowHeight(26);
        shopTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                refreshStock();
                updateSelectionButtons();
            }
        });
        updateSelectionButtons();

        stockTable.setAutoCreateRowSorter(true);
        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stockTable.setRowHeight(26);
        stockTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelectionButtons();
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolbar.add(refreshButton);
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        add(toolbar, BorderLayout.NORTH);

        JPanel shopSection = new JPanel(new BorderLayout());
        shopSection.add(new JLabel("Магазины"), BorderLayout.NORTH);
        shopSection.add(new JScrollPane(shopTable), BorderLayout.CENTER);

        JPanel stockSection = new JPanel(new BorderLayout());
        JPanel stockHeader = new JPanel(new BorderLayout());
        stockHeader.add(new JLabel("Остаток выбранного магазина"), BorderLayout.WEST);
        stockHeader.add(writeOffButton, BorderLayout.EAST);
        stockSection.add(stockHeader, BorderLayout.NORTH);
        stockSection.add(new JScrollPane(stockTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, shopSection, stockSection);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refresh());
        addButton.addActionListener(event -> showAddDialog());
        editButton.addActionListener(event -> showEditDialog());
        deleteButton.addActionListener(event -> deleteSelected());
        writeOffButton.addActionListener(event -> showWriteOffDialog());

        setBusy(false);
    }

    public void refresh() {
        int selectedId = selectedShopId();
        setBusy(true);
        showStatus("Загрузка магазинов...", false);

        apiClient.findShops().whenComplete((shops, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                setBusy(false);
                showStatus(SupplyFrame.message(error), true);
                return;
            }

            shopModel.setRowCount(0);
            for (Shop shop : shops) {
                shopModel.addRow(new Object[]{shop.id(), shop.name(), shop.address(), shop.phone()});
            }

            selectShop(selectedId);
            setBusy(false);
            showStatus("Магазинов: " + shops.size(), false);
        }));
    }

    private void refreshStock() {
        int shopId = selectedShopId();
        stockModel.setRowCount(0);

        if (shopId < 0) {
            return;
        }

        apiClient.findShopStock(shopId).whenComplete((stock, error) -> SwingUtilities.invokeLater(() -> {
            if (shopId != selectedShopId()) {
                return;
            }

            if (error != null) {
                showStatus(SupplyFrame.message(error), true);
                return;
            }

            showStock(stock);
        }));
    }

    private void showStock(List<ShopStock> stock) {
        stockModel.setRowCount(0);
        for (ShopStock item : stock) {
            stockModel.addRow(new Object[]{item.productId(), item.productName(),
                    item.unit(), item.quantity()});
        }
    }

    private void showAddDialog() {
        JTextField name = new JTextField(22);
        JTextField address = new JTextField(22);
        JTextField phone = new JTextField(22);
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Название:"));
        form.add(name);
        form.add(new JLabel("Адрес:"));
        form.add(address);
        form.add(new JLabel("Телефон (необязательно):"));
        form.add(phone);

        if (JOptionPane.showConfirmDialog(this, form, "Новый магазин",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        setBusy(true);
        showStatus("Сохранение магазина...", false);
        apiClient.createShop(name.getText(), address.getText(), phone.getText())
                .whenComplete((shop, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(SupplyFrame.message(error), true);
                        return;
                    }
                    refresh();
                    onChanged.run();
                }));
    }

    private void showEditDialog() {
        int row = selectedModelRow();

        if (row < 0) {
            return;
        }
        int shopId = (Integer) shopModel.getValueAt(row, 0);
        JTextField name = new JTextField((String) shopModel.getValueAt(row, 1), 22);
        JTextField address = new JTextField((String) shopModel.getValueAt(row, 2), 22);
        String currentPhone = (String) shopModel.getValueAt(row, 3);
        JTextField phone = new JTextField(currentPhone == null ? "" : currentPhone, 22);
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Название:"));
        form.add(name);
        form.add(new JLabel("Адрес:"));
        form.add(address);
        form.add(new JLabel("Телефон (необязательно):"));
        form.add(phone);

        if (JOptionPane.showConfirmDialog(this, form, "Изменить магазин",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        setBusy(true);
        showStatus("Сохранение магазина...", false);
        apiClient.updateShop(shopId, name.getText(), address.getText(), phone.getText())
                .whenComplete((shop, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(SupplyFrame.message(error), true);
                        return;
                    }
                    refresh();
                    onChanged.run();
                }));
    }

    private void deleteSelected() {
        int row = selectedModelRow();

        if (row < 0) {
            return;
        }
        int shopId = (Integer) shopModel.getValueAt(row, 0);
        String shopName = (String) shopModel.getValueAt(row, 1);

        if (JOptionPane.showConfirmDialog(this, "Удалить магазин «" + shopName + "»?",
                "Удаление магазина", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true);
        showStatus("Удаление магазина...", false);
        apiClient.deleteShop(shopId).whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            setBusy(false);

            if (error != null) {
                showStatus(SupplyFrame.message(error), true);
                return;
            }
            refresh();
            onChanged.run();
        }));
    }

    private void showWriteOffDialog() {
        int shopId = selectedShopId();
        int viewRow = stockTable.getSelectedRow();

        if (shopId < 0 || viewRow < 0) {
            return;
        }
        int row = stockTable.convertRowIndexToModel(viewRow);
        int productId = (Integer) stockModel.getValueAt(row, 0);
        String productName = (String) stockModel.getValueAt(row, 1);
        String unit = (String) stockModel.getValueAt(row, 2);
        long currentQuantity = (Long) stockModel.getValueAt(row, 3);

        if (currentQuantity <= 0) {
            return;
        }
        JSpinner quantitySpinner = new JSpinner(
                new SpinnerNumberModel(1L, 1L, currentQuantity, 1L));

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Товар:"));
        form.add(new JLabel(productName));
        form.add(new JLabel("Сейчас в магазине:"));
        form.add(new JLabel(currentQuantity + " " + unit));
        form.add(new JLabel("Списать количество:"));
        form.add(quantitySpinner);

        if (JOptionPane.showConfirmDialog(this, form, "Списать товар из магазина",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            quantitySpinner.commitEdit();
        } catch (ParseException exception) {
            showStatus("Введите целое положительное число", true);
            return;
        }
        long quantity = ((Number) quantitySpinner.getValue()).longValue();

        if (quantity <= 0 || quantity > currentQuantity) {
            showStatus("Количество для списания должно быть от 1 до " + currentQuantity, true);
            return;
        }
        setBusy(true);
        showStatus("Списание товара...", false);
        apiClient.decreaseShopStock(shopId, productId, quantity)
                .whenComplete((stock, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(SupplyFrame.message(error), true);
                        return;
                    }
                    refreshStock();
                    onStockAdjusted.run();
                    showStatus("Новый остаток товара «" + stock.productName() + "»: "
                            + stock.quantity() + " " + stock.unit(), false);
                }));
    }

    private int selectedModelRow() {
        int viewRow = shopTable.getSelectedRow();

        return viewRow < 0 ? -1 : shopTable.convertRowIndexToModel(viewRow);
    }

    private int selectedShopId() {
        int row = selectedModelRow();

        return row < 0 ? -1 : (Integer) shopModel.getValueAt(row, 0);
    }

    private void selectShop(int shopId) {
        if (shopId < 0) {
            return;
        }
        for (int row = 0; row < shopModel.getRowCount(); row++) {
            if ((Integer) shopModel.getValueAt(row, 0) == shopId) {
                int viewRow = shopTable.convertRowIndexToView(row);
                shopTable.setRowSelectionInterval(viewRow, viewRow);
                return;
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
        boolean enabled = !busy && shopTable.getSelectedRow() >= 0;
        editButton.setEnabled(enabled && role.canWrite());
        deleteButton.setEnabled(enabled && role.canDelete());
        int stockRow = stockTable.getSelectedRow();
        writeOffButton.setEnabled(enabled && role.canWrite() && stockRow >= 0
                && (Long) stockModel.getValueAt(stockTable.convertRowIndexToModel(stockRow), 3) > 0);
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? new Color(170, 35, 35) : Color.DARK_GRAY);
    }

}
