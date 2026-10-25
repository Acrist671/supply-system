package com.acrist.client.ui.product;

import com.acrist.client.api.SupplyApiClient;
import com.acrist.client.ui.SupplyFrame;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.text.ParseException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class ReceiveProductDialog extends JDialog {
    private final SupplyFrame owner;
    private final SupplyApiClient apiClient;
    private final int productId;
    private final long currentQuantity;
    private final JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(1L, 1L, Long.MAX_VALUE, 1L));
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("Учесть поступление");
    private final JButton cancelButton = new JButton("Отмена");

    public ReceiveProductDialog(SupplyFrame owner, SupplyApiClient apiClient,
                                int productId, String productName, String unit, long currentQuantity) {
        super(owner, "Поступление товара", ModalityType.APPLICATION_MODAL);
        this.owner = owner;
        this.apiClient = apiClient;
        this.productId = productId;
        this.currentQuantity = currentQuantity;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JLabel productLabel = new JLabel("Товар: " + productName);
        JLabel currentLabel = new JLabel("Текущий остаток: " + currentQuantity + " " + unit);
        JLabel amountLabel = new JLabel("Количество (" + unit + "):");

        JPanel amountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        amountPanel.add(amountLabel);
        amountPanel.add(amountSpinner);

        JPanel fields = new JPanel(new BorderLayout(0, 8));
        fields.add(productLabel, BorderLayout.NORTH);
        fields.add(currentLabel, BorderLayout.CENTER);
        fields.add(amountPanel, BorderLayout.SOUTH);

        messageLabel.setForeground(new Color(170, 35, 35));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(messageLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(new EmptyBorder(18, 18, 14, 18));
        content.add(fields, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        saveButton.addActionListener(event -> receive());
        cancelButton.addActionListener(event -> dispose());
        getRootPane().setDefaultButton(saveButton);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private void receive() {
        try {
            amountSpinner.commitEdit();
        } catch (ParseException exception) {
            messageLabel.setText("Введите целое положительное количество.");
            return;
        }

        long amount = ((Number) amountSpinner.getValue()).longValue();

        if (amount > Long.MAX_VALUE - currentQuantity) {
            messageLabel.setText("Остаток превысит максимально допустимое значение.");
            return;
        }

        setBusy(true);
        messageLabel.setText("Сохранение...");

        apiClient.receive(productId, amount)
                .whenComplete((product, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        messageLabel.setText(SupplyFrame.message(error));
                        return;
                    }

                    dispose();
                    owner.productReceived(product, amount);
                }));
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        amountSpinner.setEnabled(!busy);
        setDefaultCloseOperation(busy ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
    }
}
