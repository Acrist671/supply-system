package com.acrist.client.ui.shop;

import com.acrist.client.api.SupplyApiClient;
import com.acrist.client.ui.SupplyFrame;
import com.acrist.shared.supply.ShopSummary;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.math.BigDecimal;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class ShopSummaryPanel extends JPanel {
    private final SupplyApiClient apiClient;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Магазин", "Видов товара", "Количество", "Стоимость"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0 -> Integer.class;
                case 2 -> Long.class;
                case 3, 4 -> BigDecimal.class;
                default -> String.class;
            };
        }
    };

    private final JTable table = new JTable(model);
    private final JButton refreshButton = new JButton("Обновить");
    private final JLabel statusLabel = new JLabel("Загрузка сводки...");
    private long refreshVersion;

    public ShopSummaryPanel(SupplyApiClient apiClient) {
        super(new BorderLayout(0, 10));
        this.apiClient = apiClient;

        setBorder(new EmptyBorder(12, 12, 12, 12));
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);

        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Стоимость рассчитана по текущим ценам товаров."), BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refreshButton);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refresh());
    }

    public void refresh() {
        long version = ++refreshVersion;
        refreshButton.setEnabled(false);
        showStatus("Загрузка сводки...", false);

        apiClient.findShopSummaries().whenComplete((summaries, error) -> SwingUtilities.invokeLater(() -> {
            if (version != refreshVersion) {
                return;
            }

            refreshButton.setEnabled(true);

            if (error != null) {
                showStatus(SupplyFrame.message(error), true);
                return;
            }

            model.setRowCount(0);
            for (ShopSummary summary : summaries) {
                model.addRow(new Object[]{summary.shopId(), summary.shopName(),
                        summary.productKinds(), summary.totalQuantity(), summary.totalValue()});
            }

            showStatus("Магазинов: " + summaries.size(), false);
        }));
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? new Color(170, 35, 35) : Color.DARK_GRAY);
    }
}
