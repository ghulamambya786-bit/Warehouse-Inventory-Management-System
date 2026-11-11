import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Shared page for Stock In and Stock Out.
 *   Stock In  = PUSH: a new batch (Batch ID, product, quantity, date, supplier) goes on top of the stack.
 *   Stock Out = POP : the latest (TOP) batch is removed from the stack.
 */
public abstract class StockMovementPanel extends JPanel implements Refreshable {

    /** Combo box entry: shows "P001 - Laptop" but keeps the product. */
    private static class Item {
        final Product product;
        Item(Product p) { this.product = p; }
        @Override public String toString() { return product.getId() + " - " + product.getName(); }
    }

    private static final String[] REASONS =
            {"Sale", "Damaged", "Expired", "Returned to Supplier", "Internal Use", "Other"};

    private final ProductManager pm;
    private final boolean stockIn;

    // Stock In (PUSH) fields
    private final JTextField batchField = Theme.field();
    private final JComboBox<Item> productBox = new JComboBox<>();
    private final JTextField qtyField = Theme.field();
    private final JTextField priceField = Theme.field();
    private final JTextField dateField = Theme.field();
    private final JTextField supplierField = Theme.field();
    private final JLabel infoLbl = new JLabel(" ");

    // Stock Out (POP) widgets
    private final JComboBox<String> reasonBox = new JComboBox<>(REASONS);
    private final JLabel[] topValues = new JLabel[6];
    private final JLabel stackInfo = new JLabel(" ");
    private JButton goButton;

    private final JLabel banner = new JLabel(" ");
    private final JPanel bannerWrap = new JPanel(new BorderLayout());
    private boolean loading;

    protected StockMovementPanel(ProductManager pm, boolean stockIn) {
        this.pm = pm;
        this.stockIn = stockIn;

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));
        add(Theme.pageTitle(stockIn ? "Stock In (PUSH Batch)" : "Stock Out (POP Batch)", stockIn ? "in" : "out"),
                BorderLayout.NORTH);

        Dimension fieldSize = new Dimension(280, 40);
        productBox.setFont(Theme.BODY);
        productBox.setPreferredSize(fieldSize);
        reasonBox.setFont(Theme.BODY);
        reasonBox.setEditable(true);
        reasonBox.setPreferredSize(fieldSize);
        for (JTextField f : new JTextField[]{batchField, qtyField, priceField, dateField, supplierField}) {
            f.setPreferredSize(fieldSize);
        }

        JPanel form = Theme.transparent(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        int row = 0;
        if (stockIn) {
            addRow(form, gc, row++, "Batch ID:", batchField);
            addRow(form, gc, row++, "Product:", productBox);
            addRow(form, gc, row++, "Quantity:", qtyField);
            addRow(form, gc, row++, "Unit Price (PKR):", priceField);
            addRow(form, gc, row++, "Batch Date:", dateField);
            addRow(form, gc, row++, "Supplier:", supplierField);
            dateField.setToolTipText("Format: yyyy-MM-dd, for example 2026-11-08");
        } else {
            form.add(buildTopBatchCard(), cardConstraints(gc, row++));
            addRow(form, gc, row++, "Reason:", reasonBox);
        }
        infoLbl.setFont(Theme.SMALL);
        infoLbl.setForeground(Theme.MUTED);
        gc.gridx = 1;
        gc.gridy = row++;
        gc.gridwidth = 1;
        gc.insets = new Insets(2, 6, 2, 6);
        form.add(infoLbl, gc);

        goButton = Theme.button(stockIn ? "PUSH Batch" : "POP Top Batch", stockIn ? Theme.GOLD : Theme.RED,
                stockIn ? Theme.NAVY : Color.WHITE, stockIn ? "check" : "out");
        JButton reset = Theme.button("Reset", Theme.NAVY, Color.WHITE, "refresh");
        JPanel btns = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        btns.add(goButton);
        btns.add(reset);
        gc.gridy = row;
        gc.insets = new Insets(14, 6, 4, 6);
        form.add(btns, gc);

        JPanel formCard = Theme.card(null);
        formCard.add(form, BorderLayout.NORTH);
        formCard.setPreferredSize(new Dimension(590, stockIn ? 430 : 470));

        // picture card (box for Stock In, shopping cart for Stock Out)
        RoundPanel pic = new RoundPanel(new BorderLayout(), new Color(0xE9F1FC), 18);
        pic.setShadow(false);
        BufferedImage img = Images.get(stockIn ? "box" : "cart");
        if (img != null) {
            JLabel l = new JLabel(new javax.swing.ImageIcon(img.getScaledInstance(
                    img.getWidth() * 3 / 2, img.getHeight() * 3 / 2, java.awt.Image.SCALE_SMOOTH)));
            pic.add(l, BorderLayout.CENTER);
        }
        pic.setPreferredSize(new Dimension(250, 250));
        JPanel picWrap = Theme.transparent(new BorderLayout());
        picWrap.add(pic, BorderLayout.NORTH);

        JPanel top = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 18, 0));
        top.add(formCard);
        top.add(picWrap);

        banner.setFont(Theme.BOLD);
        banner.setOpaque(true);
        banner.setVisible(false);
        banner.setIconTextGap(10);
        banner.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JPanel center = Theme.transparent(new BorderLayout(0, 14));
        center.add(top, BorderLayout.NORTH);
        bannerWrap.setOpaque(false);
        bannerWrap.add(banner, BorderLayout.NORTH);
        center.add(bannerWrap, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        productBox.addActionListener(e -> { if (!loading) showSelected(); });
        goButton.addActionListener(e -> { if (stockIn) push(); else pop(); });
        reset.addActionListener(e -> { clearForm(); hideBanner(); });
    }

    private GridBagConstraints cardConstraints(GridBagConstraints gc, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 6, 12, 6);
        return c;
    }

    /** Stock Out: the card that shows the TOP batch, i.e. the one that will be removed. */
    private JPanel buildTopBatchCard() {
        RoundPanel card = new RoundPanel(new BorderLayout(0, 8), new Color(0xEAF3FF), 16);
        card.setShadow(false);
        card.setBorderColor(Theme.BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("Latest batch on TOP of the stack (this one will be removed)");
        title.setFont(Theme.BOLD);
        title.setForeground(Theme.NAVY);
        card.add(title, BorderLayout.NORTH);
        JPanel grid = Theme.transparent(new GridLayout(0, 2, 10, 4));
        String[] names = {"Batch ID", "Product", "Quantity", "Batch Date", "Supplier", "Batch Value"};
        for (int i = 0; i < names.length; i++) {
            JLabel k = new JLabel(names[i] + ":");
            k.setFont(Theme.SMALL);
            k.setForeground(Theme.MUTED);
            topValues[i] = new JLabel("-");
            topValues[i].setFont(Theme.BOLD);
            topValues[i].setForeground(Theme.TEXT);
            grid.add(k);
            grid.add(topValues[i]);
        }
        card.add(grid, BorderLayout.CENTER);
        stackInfo.setFont(Theme.SMALL);
        stackInfo.setForeground(Theme.MUTED);
        card.add(stackInfo, BorderLayout.SOUTH);
        return card;
    }

    private void addRow(JPanel form, GridBagConstraints gc, int row, String label, java.awt.Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        gc.insets = new Insets(7, 6, 7, 14);
        JLabel l = Theme.label(label);
        l.setPreferredSize(new Dimension(110, 24));
        form.add(l, gc);
        gc.gridx = 1;
        gc.insets = new Insets(7, 6, 7, 6);
        form.add(field, gc);
    }

    // ----------------------------------------------------------------- data
    @Override
    public void refresh() {
        if (stockIn) loadProducts(null);
        clearForm();
        showTopBatch();
        hideBanner();
    }

    /** Reloads the product list; keeps the given product (or the current one) selected. */
    private void loadProducts(String keepId) {
        try {
            if (keepId == null) {
                Item cur = (Item) productBox.getSelectedItem();
                keepId = cur == null ? null : cur.product.getId();
            }
            List<Product> all = pm.getAll();
            loading = true;
            productBox.removeAllItems();
            int select = 0, i = 0;
            for (Product p : all) {
                productBox.addItem(new Item(p));
                if (p.getId().equals(keepId)) select = i;
                i++;
            }
            if (!all.isEmpty()) productBox.setSelectedIndex(select);
            loading = false;
            showSelected();
        } catch (ProductManager.DbException ex) {
            loading = false;
            Theme.error(this, ex.getMessage());
        }
    }

    private void showSelected() {
        if (!stockIn) return;
        Item it = (Item) productBox.getSelectedItem();
        if (it == null) {
            infoLbl.setText(" ");
            return;
        }
        Product p = it.product;
        infoLbl.setText("Current stock: " + p.getQuantity() + " units  |  Status: " + p.getStatus()
                + "  |  Stack: " + pm.getStack().size() + " / " + pm.getStack().getCapacity() + " batches");
        priceField.setText(String.format("%.2f", p.getPrice()));
    }

    private void showTopBatch() {
        if (stockIn) return;
        CustomStack s = pm.getStack();
        stackInfo.setText("Stack: " + s.size() + " / " + s.getCapacity() + " batches (LIFO - last in, first out)");
        if (s.isEmpty()) {
            for (JLabel l : topValues) l.setText("-");
            topValues[0].setText("The stack is empty - nothing to POP");
            goButton.setEnabled(false);
            return;
        }
        Batch b = pm.peekBatch();
        topValues[0].setText(b.getBatchId());
        topValues[1].setText(b.getProductId() + " - " + b.getProductName());
        topValues[2].setText(b.getQuantity() + " units");
        topValues[3].setText(String.valueOf(b.getDate()));
        topValues[4].setText(b.getSupplier().isEmpty() ? "-" : b.getSupplier());
        topValues[5].setText(String.format("₨%,.2f", b.getValue()));
        goButton.setEnabled(true);
    }

    private void clearForm() {
        qtyField.setText("");
        supplierField.setText("");
        dateField.setText(LocalDate.now().toString());
        reasonBox.setSelectedIndex(0);
        try {
            batchField.setText(pm.nextBatchId());
        } catch (ProductManager.DbException ex) {
            batchField.setText("");
        }
        Item it = (Item) productBox.getSelectedItem();
        if (it != null) priceField.setText(String.format("%.2f", it.product.getPrice()));
        qtyField.requestFocusInWindow();
    }

    // --------------------------------------------------------------- actions
    private void push() {
        hideBanner();
        Item it = (Item) productBox.getSelectedItem();
        if (it == null) {
            Theme.error(this, "There are no products yet. Add a product first.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
        } catch (NumberFormatException ex) {
            Theme.error(this, "Quantity must be a whole number.");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(priceField.getText().trim());
        } catch (NumberFormatException ex) {
            Theme.error(this, "Price must be a number.");
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateField.getText().trim());
        } catch (DateTimeParseException ex) {
            Theme.error(this, "Batch date must look like 2026-11-08 (yyyy-MM-dd).");
            return;
        }
        if (supplierField.getText().trim().isEmpty()) {
            Theme.error(this, "Supplier is required.");
            return;
        }
        try {
            String id = it.product.getId();
            Batch b = pm.pushBatch(batchField.getText(), id, qty, date, supplierField.getText(), price);
            loadProducts(id);
            clearForm();
            showBanner(true, "PUSH Successful!  Batch " + b.getBatchId() + " (" + qty + " x " + b.getProductName()
                    + ") is now on the TOP of the stack.");
        } catch (CustomStack.StackOverflowException ex) {
            Theme.error(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void pop() {
        hideBanner();
        if (pm.getStack().isEmpty()) {
            Theme.error(this, "Stack Underflow! The stack is empty, nothing to pop.");
            return;
        }
        Batch top = pm.peekBatch();
        String reason = String.valueOf(reasonBox.getSelectedItem()).trim();
        int ans = JOptionPane.showConfirmDialog(this, "POP batch " + top.getBatchId() + " (" + top.getQuantity()
                        + " x " + top.getProductName() + ") from the TOP of the stack?\n"
                        + "The product quantity will go down by " + top.getQuantity() + ".",
                "Confirm POP", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;
        try {
            Batch b = pm.popBatch(reason);
            clearForm();
            showTopBatch();
            showBanner(true, "POP Successful!  Batch " + b.getBatchId() + " (" + b.getQuantity() + " x "
                    + b.getProductName() + ") was removed from the TOP of the stack.");
        } catch (CustomStack.StackUnderflowException ex) {
            Theme.error(this, ex.getMessage());
            showTopBatch();
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void showBanner(boolean ok, String text) {
        Color fg = ok ? new Color(0x14663A) : new Color(0x9B2C20);
        banner.setText(text);
        banner.setIcon(Icons.icon(ok ? "check" : "warning", 18, fg));
        banner.setBackground(ok ? new Color(0xD8F2E2) : new Color(0xFBE0DD));
        banner.setForeground(fg);
        banner.setVisible(true);
    }

    private void hideBanner() { banner.setVisible(false); }
}
