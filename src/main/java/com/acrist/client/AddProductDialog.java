package com.acrist.client;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;

class AddProductDialog extends JDialog {
    private final ProductFrame owner;
    private final ProductApiClient apiClient;
    private final JTextField nameField = new JTextField(24);
    private final JComboBox<String> unitBox = new JComboBox<>(new String[]{"шт", "кг", "л", "м", "уп"});
    private final JTextField categoryField = new JTextField(24);
    private final JTextField priceField = new JTextField("0.00", 24);
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("Добавить");
    private final JButton cancelButton = new JButton("Отмена");

    AddProductDialog(ProductFrame owner, ProductApiClient apiClient) {
        super(owner, "Добавить товар", ModalityType.APPLICATION_MODAL);
        this.owner = owner;
        this.apiClient = apiClient;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel fields = new JPanel(new GridLayout(4, 2, 10, 12));
        fields.add(new JLabel("Название:"));
        fields.add(nameField);
        fields.add(new JLabel("Единица измерения:"));
        fields.add(unitBox);
        fields.add(new JLabel("Категория:"));
        fields.add(categoryField);
        fields.add(new JLabel("Цена за единицу:"));
        fields.add(priceField);

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

        saveButton.addActionListener(event -> addProduct());
        cancelButton.addActionListener(event -> dispose());
        getRootPane().setDefaultButton(saveButton);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void addProduct() {
        String name = nameField.getText().trim();
        String category = categoryField.getText().trim();

        if (name.isEmpty() || category.isEmpty()) {
            messageLabel.setText("Заполните название и категорию.");
            return;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(priceField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            messageLabel.setText("Введите корректную цену.");
            return;
        }

        setBusy(true);
        messageLabel.setText("Сохранение...");
        apiClient.create(name, (String) unitBox.getSelectedItem(), category, price)
                .whenComplete((product, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        messageLabel.setText(ProductFrame.message(error));
                        return;
                    }
                    dispose();
                    owner.productCreated(product);
                }));
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        setDefaultCloseOperation(busy ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
    }
}
