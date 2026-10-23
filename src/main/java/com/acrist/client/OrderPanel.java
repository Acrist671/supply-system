package com.acrist.client;

import com.acrist.auth.Role;
import com.acrist.product.Product;
import com.acrist.supply.OrderFilter;
import com.acrist.supply.Shop;
import com.acrist.supply.SupplyOrder;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.ParseException;
import java.util.List;

class OrderPanel extends JPanel {
    private final ProductApiClient apiClient;
    private final Role role;
    private final Runnable onFulfilled;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Магазин", "Товар", "Количество", "Дата", "Статус"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : column == 3 ? Long.class : String.class;
        }
    };
    private final JTable table = new JTable(model);
    private final JButton refreshButton = new JButton("Обновить");
    private final JButton createButton = new JButton("Новый заказ");
    private final JButton startButton = new JButton("Взять в работу");
    private final JButton fulfillButton = new JButton("Выполнить заказ");
    private final JButton deleteButton = new JButton("Удалить заказ");
    private final JComboBox<Choice> shopFilter = new JComboBox<>();
    private final JComboBox<Choice> productFilter = new JComboBox<>();
    private final JComboBox<StatusChoice> statusFilter = new JComboBox<>(new StatusChoice[]{
            new StatusChoice(null, "Все статусы"), new StatusChoice("new", "Новый"),
            new StatusChoice("in progress", "В работе"), new StatusChoice("done", "Выполнен")});
    private final JTextField yearFilter = new JTextField(5);
    private final JButton applyFilterButton = new JButton("Применить");
    private final JButton clearFilterButton = new JButton("Сбросить");
    private final JLabel statusLabel = new JLabel("Загрузка заказов...");
    private boolean busy;
    private long refreshVersion;

    OrderPanel(ProductApiClient apiClient, Role role, Runnable onFulfilled) {
        super(new BorderLayout(0, 10));
        this.apiClient = apiClient;
        this.role = role;
        this.onFulfilled = onFulfilled;
        setBorder(new EmptyBorder(12, 12, 12, 12));
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.getSelectionModel().addListSelectionListener(event -> updateOrderButtons());
        updateOrderButtons();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolbar.add(refreshButton);
        toolbar.add(createButton);
        toolbar.add(startButton);
        toolbar.add(fulfillButton);
        toolbar.add(deleteButton);
        shopFilter.addItem(new Choice(0, "Все магазины"));
        productFilter.addItem(new Choice(0, "Все товары"));
        shopFilter.setPreferredSize(new Dimension(150, 26));
        productFilter.setPreferredSize(new Dimension(150, 26));
        statusFilter.setPreferredSize(new Dimension(130, 26));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.add(new JLabel("Магазин:"));
        filters.add(shopFilter);
        filters.add(new JLabel("Товар:"));
        filters.add(productFilter);
        filters.add(new JLabel("Статус:"));
        filters.add(statusFilter);
        filters.add(new JLabel("Год:"));
        filters.add(yearFilter);
        filters.add(applyFilterButton);
        filters.add(clearFilterButton);
        JPanel top = new JPanel(new BorderLayout());
        top.add(toolbar, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refresh());
        createButton.addActionListener(event -> showCreateDialog());
        startButton.addActionListener(event -> startSelected());
        fulfillButton.addActionListener(event -> fulfillSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        applyFilterButton.addActionListener(event -> refresh());
        clearFilterButton.addActionListener(event -> {
            shopFilter.setSelectedIndex(0);
            productFilter.setSelectedIndex(0);
            statusFilter.setSelectedIndex(0);
            yearFilter.setText("");
            refresh();
        });
        yearFilter.addActionListener(event -> refresh());
        setBusy(false);
    }

    void refresh() {
        refresh(selectedOrderId());
    }

    private void refresh(int preferredId) {
        OrderFilter filter;
        try {
            filter = selectedFilter();
        } catch (IllegalArgumentException exception) {
            showStatus(exception.getMessage(), true);
            return;
        }
        long version = ++refreshVersion;
        setBusy(true);
        showStatus("Загрузка заказов...", false);
        apiClient.findShops().thenCombine(apiClient.findAll(), OrderOptions::new)
                .thenCombine(apiClient.findOrders(filter), OrderView::new)
                .whenComplete((view, error) -> SwingUtilities.invokeLater(() -> {
            if (version != refreshVersion) {
                return;
            }
            if (error != null) {
                setBusy(false);
                showStatus(ProductFrame.message(error), true);
                return;
            }
            updateFilterOptions(view.options(), filter);
            model.setRowCount(0);
            for (SupplyOrder order : view.orders()) {
                model.addRow(new Object[]{order.id(), order.shopName(), order.productName(),
                        order.quantity(), order.dateOrder(), order.status()});
            }
            selectOrder(preferredId);
            setBusy(false);
            showStatus("Заказов: " + view.orders().size(), false);
        }));
    }

    private OrderFilter selectedFilter() {
        Choice shop = (Choice) shopFilter.getSelectedItem();
        Choice product = (Choice) productFilter.getSelectedItem();
        StatusChoice status = (StatusChoice) statusFilter.getSelectedItem();
        String yearText = yearFilter.getText().trim();
        Integer year = null;

        if (!yearText.isEmpty()) {
            try {
                year = Integer.valueOf(yearText);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Год должен быть целым числом");
            }
            if (year < 1 || year > 9999) {
                throw new IllegalArgumentException("Год должен быть от 1 до 9999");
            }
        }

        return new OrderFilter(shop == null || shop.id() == 0 ? null : shop.id(),
                product == null || product.id() == 0 ? null : product.id(),
                status == null ? null : status.code(), year);
    }

    private void updateFilterOptions(OrderOptions options, OrderFilter filter) {
        shopFilter.removeAllItems();
        shopFilter.addItem(new Choice(0, "Все магазины"));
        for (Shop shop : options.shops()) {
            Choice choice = new Choice(shop.id(), shop.name());
            shopFilter.addItem(choice);

            if (filter.shopId() != null && filter.shopId() == shop.id()) {
                shopFilter.setSelectedItem(choice);
            }
        }
        productFilter.removeAllItems();
        productFilter.addItem(new Choice(0, "Все товары"));
        for (Product product : options.products()) {
            Choice choice = new Choice(product.id(), product.name());
            productFilter.addItem(choice);

            if (filter.productId() != null && filter.productId() == product.id()) {
                productFilter.setSelectedItem(choice);
            }
        }
    }

    private void showCreateDialog() {
        setBusy(true);
        showStatus("Загрузка магазинов и товаров...", false);
        apiClient.findShops().thenCombine(apiClient.findAll(), OrderOptions::new)
                .whenComplete((options, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(ProductFrame.message(error), true);
                        return;
                    }
                    if (options.shops().isEmpty() || options.products().isEmpty()) {
                        showStatus("Сначала добавьте магазин и товар", true);
                        return;
                    }
                    openCreateForm(options);
                }));
    }

    private void openCreateForm(OrderOptions options) {
        JComboBox<Choice> shopBox = new JComboBox<>();
        for (Shop shop : options.shops()) {
            shopBox.addItem(new Choice(shop.id(), shop.name()));
        }
        JComboBox<Choice> productBox = new JComboBox<>();
        for (Product product : options.products()) {
            productBox.addItem(new Choice(product.id(), product.name() + " (" + product.unit() + ")"));
        }
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1L, 1L, Long.MAX_VALUE, 1L));
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Магазин:"));
        form.add(shopBox);
        form.add(new JLabel("Товар:"));
        form.add(productBox);
        form.add(new JLabel("Количество:"));
        form.add(quantity);

        if (JOptionPane.showConfirmDialog(this, form, "Новый заказ",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            quantity.commitEdit();
        } catch (ParseException exception) {
            showStatus("Введите целое положительное количество", true);
            return;
        }
        Choice shop = (Choice) shopBox.getSelectedItem();
        Choice product = (Choice) productBox.getSelectedItem();
        long amount = ((Number) quantity.getValue()).longValue();
        setBusy(true);
        showStatus("Создание заказа...", false);
        apiClient.createOrder(shop.id(), product.id(), amount)
                .whenComplete((order, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(ProductFrame.message(error), true);
                        return;
                    }
                    refresh(order.id());
                }));
    }

    private void startSelected() {
        int orderId = selectedOrderId();

        if (orderId < 0) {
            return;
        }
        setBusy(true);
        showStatus("Передача заказа в работу...", false);
        apiClient.startOrder(orderId)
                .whenComplete((order, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(ProductFrame.message(error), true);
                        return;
                    }
                    refresh(order.id());
                }));
    }

    private void deleteSelected() {
        int orderId = selectedOrderId();

        if (orderId < 0) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Удалить новый заказ №" + orderId + "?",
                "Удаление заказа", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true);
        showStatus("Удаление заказа...", false);
        apiClient.deleteOrder(orderId)
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(ProductFrame.message(error), true);
                        return;
                    }
                    refresh(-1);
                }));
    }

    private void fulfillSelected() {
        int orderId = selectedOrderId();

        if (orderId < 0) {
            return;
        }
        setBusy(true);
        showStatus("Выполнение заказа...", false);
        apiClient.fulfillOrder(orderId)
                .whenComplete((order, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        showStatus(ProductFrame.message(error), true);
                        return;
                    }
                    refresh(order.id());
                    onFulfilled.run();
                }));
    }

    private int selectedOrderId() {
        int viewRow = table.getSelectedRow();

        return viewRow < 0 ? -1 : (Integer) model.getValueAt(table.convertRowIndexToModel(viewRow), 0);
    }

    private void selectOrder(int orderId) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if ((Integer) model.getValueAt(row, 0) == orderId) {
                int viewRow = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshButton.setEnabled(!busy);
        createButton.setEnabled(!busy && role.canWrite());
        shopFilter.setEnabled(!busy);
        productFilter.setEnabled(!busy);
        statusFilter.setEnabled(!busy);
        yearFilter.setEnabled(!busy);
        applyFilterButton.setEnabled(!busy);
        clearFilterButton.setEnabled(!busy);
        updateOrderButtons();
    }

    private void updateOrderButtons() {
        int row = table.getSelectedRow();
        String status = row < 0 ? null : (String) model.getValueAt(table.convertRowIndexToModel(row), 5);
        startButton.setEnabled(!busy && role.canWrite() && "new".equals(status));
        deleteButton.setEnabled(!busy && role.canDelete() && "new".equals(status));
        fulfillButton.setEnabled(!busy && role.canWrite() && "in progress".equals(status));
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? new Color(170, 35, 35) : Color.DARK_GRAY);
    }

    private record OrderOptions(List<Shop> shops, List<Product> products) {
    }

    private record OrderView(OrderOptions options, List<SupplyOrder> orders) {
    }

    private record StatusChoice(String code, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record Choice(int id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
