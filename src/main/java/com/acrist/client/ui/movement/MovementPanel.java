package com.acrist.client.ui.movement;

import com.acrist.client.api.SupplyApiClient;
import com.acrist.client.ui.SupplyFrame;
import com.acrist.shared.supply.StockMovement;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class MovementPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SupplyApiClient apiClient;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Дата и время", "Операция", "Товар", "Количество", "Ед.", "Магазин", "Заказ"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 || column == 4 ? Long.class : String.class;
        }
    };

    private final JButton refreshButton = new JButton("Обновить");
    private final JLabel statusLabel = new JLabel("Загрузка истории...");

    public MovementPanel(SupplyApiClient apiClient) {
        super(new BorderLayout(0, 10));
        this.apiClient = apiClient;

        setBorder(new EmptyBorder(12, 12, 12, 12));

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolbar.add(refreshButton);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refresh());
    }

    public void refresh() {
        refreshButton.setEnabled(false);
        showStatus("Загрузка истории...", false);

        apiClient.findMovements().whenComplete((movements, error) -> SwingUtilities.invokeLater(() -> {
            refreshButton.setEnabled(true);

            if (error != null) {
                showStatus(SupplyFrame.message(error), true);

                return;
            }

            showMovements(movements);
            showStatus("Записей: " + movements.size(), false);
        }));
    }

    private void showMovements(List<StockMovement> movements) {
        model.setRowCount(0);

        for (StockMovement movement : movements) {
            model.addRow(new Object[]{
                    movement.id(),
                    DATE_FORMAT.format(Instant.parse(movement.createdAt())),
                    switch (movement.type()) {
                        case "receipt" -> "Поступление";
                        case "shipment" -> "Отгрузка";
                        case "shop_increase" -> "Корректировка магазина +";
                        case "shop_decrease" -> "Списание в магазине";
                        default -> movement.type();
                    },
                    movement.productName(),
                    movement.quantity(),
                    movement.unit(),
                    movement.shopName() == null ? "" : movement.shopName(),
                    movement.orderId() == null ? "" : movement.orderId().toString()
            });
        }
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? new Color(170, 35, 35) : Color.DARK_GRAY);
    }
}
