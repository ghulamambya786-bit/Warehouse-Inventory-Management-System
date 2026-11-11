import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Page 2: product table with search, category/status filters, paging, edit, delete and CSV export. */
public class InventoryPanel extends JPanel implements Refreshable {
    private static final int PAGE_SIZE = 8;
    private static final int ACTION_BTN = 28, ACTION_GAP = 8;

    private final ProductManager pm;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Name", "Category", "Quantity", "Price", "Status", "Actions"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final SearchBox searchBox = new SearchBox("Search Product...", 200);
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JComboBox<String> statusBox =
            new JComboBox<>(new String[]{"All Status", "In Stock", "Low Stock", "Out of Stock"});
    private static final String[] SORTS = {"Sort: Product ID", "Name (A to Z)", "Name (Z to A)",
            "Quantity (Low to High)", "Quantity (High to Low)", "Price (Low to High)", "Price (High to Low)"};
    private final JComboBox<String> sortBox = new JComboBox<>(SORTS);
    private final Pager pager = new Pager("products", this::goToPage);
    private List<Product> filtered = new ArrayList<>();
    private int page;
    private boolean loading;

    public InventoryPanel(ProductManager pm, Runnable goToAdd) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        // ---- header + toolbar
        JPanel top = Theme.transparent(new BorderLayout(0, 14));
        top.add(Theme.pageTitle("Product Inventory", "box"), BorderLayout.NORTH);
        JPanel bar = Theme.transparent(new BorderLayout(10, 0));
        JPanel filters = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        categoryBox.setFont(Theme.BODY);
        statusBox.setFont(Theme.BODY);
        sortBox.setFont(Theme.BODY);
        categoryBox.setPreferredSize(new Dimension(140, 40));
        statusBox.setPreferredSize(new Dimension(120, 40));
        sortBox.setPreferredSize(new Dimension(170, 40));
        filters.add(searchBox);
        filters.add(categoryBox);
        filters.add(statusBox);
        filters.add(sortBox);
        JButton add = Theme.button("Add Product", Theme.GOLD, Theme.NAVY, "plus");
        JButton export = Theme.button("Export", Theme.TEAL, Color.WHITE, "download");
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.add(add);
        buttons.add(export);
        bar.add(filters, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        top.add(bar, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // ---- table
        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        table.getColumnModel().getColumn(6).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setCellRenderer(centered());
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(new ActionsRenderer());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        JPanel tableCard = new RoundPanel(new BorderLayout(0, 10), Color.WHITE, 18);
        ((RoundPanel) tableCard).setBorderColor(Theme.BORDER);
        tableCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 14, 12));
        tableCard.add(sp, BorderLayout.CENTER);
        tableCard.add(pager, BorderLayout.SOUTH);
        add(tableCard, BorderLayout.CENTER);

        // ---- events
        searchBox.getField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { reload(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { reload(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { reload(); }
        });
        categoryBox.addActionListener(e -> { if (!loading) reload(); });
        statusBox.addActionListener(e -> { if (!loading) reload(); });
        sortBox.addActionListener(e -> { if (!loading) reload(); });
        add.addActionListener(e -> goToAdd.run());
        export.addActionListener(e -> exportCsv());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0) return;
                String id = (String) model.getValueAt(row, 0);
                if (col == 6) {
                    Rectangle r = table.getCellRect(row, col, false);
                    int total = ACTION_BTN * 2 + ACTION_GAP;
                    int x0 = r.x + (r.width - total) / 2;
                    int dx = e.getX() - x0;
                    if (dx >= 0 && dx < ACTION_BTN) editProduct(id);
                    else if (dx >= ACTION_BTN + ACTION_GAP && dx < total) deleteProduct(id);
                } else if (e.getClickCount() == 2) {
                    viewProduct(id);
                }
            }
        });
    }

    /** @param status "All Status", "In Stock", "Low Stock" or "Out of Stock" */
    public void setStatusFilter(String status) {
        loading = true;
        statusBox.setSelectedItem(status);
        loading = false;
    }

    @Override
    public void refresh() {
        loading = true;
        String keep = (String) categoryBox.getSelectedItem();
        Set<String> cats = new LinkedHashSet<>();
        try {
            cats.addAll(pm.categoryNames());
            for (Product p : pm.getAll()) cats.add(p.getCategory());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
        categoryBox.removeAllItems();
        categoryBox.addItem("All Categories");
        for (String c : cats) categoryBox.addItem(c);
        categoryBox.setSelectedItem(keep == null ? "All Categories" : keep);
        loading = false;
        load(false);
    }

    private void reload() { load(true); }

    private void load(boolean resetPage) {
        try {
            String cat = (String) categoryBox.getSelectedItem();
            String st = (String) statusBox.getSelectedItem();
            filtered = new ArrayList<>();
            for (Product p : pm.search(searchBox.getField().getText())) {
                if (cat != null && !cat.equals("All Categories") && !cat.equalsIgnoreCase(p.getCategory())) continue;
                if (st != null && !st.equals("All Status") && !st.equals(p.getStatus())) continue;
                filtered.add(p);
            }
            filtered.sort(sortComparator());
            if (resetPage) page = 0;
            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = Math.min(page, pages - 1);
            fillPage();
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    /** Sorting by name, quantity or price (product id order is the default from the database). */
    private java.util.Comparator<Product> sortComparator() {
        switch (sortBox.getSelectedIndex()) {
            case 1: return java.util.Comparator.comparing(p -> p.getName().toLowerCase());
            case 2: return java.util.Comparator.comparing((Product p) -> p.getName().toLowerCase()).reversed();
            case 3: return java.util.Comparator.comparingInt(Product::getQuantity);
            case 4: return java.util.Comparator.comparingInt(Product::getQuantity).reversed();
            case 5: return java.util.Comparator.comparingDouble(Product::getPrice);
            case 6: return java.util.Comparator.comparingDouble(Product::getPrice).reversed();
            default: return java.util.Comparator.comparing(Product::getId);
        }
    }

    private void goToPage(int p) {
        page = p;
        fillPage();
    }

    private void fillPage() {
        model.setRowCount(0);
        int from = page * PAGE_SIZE, to = Math.min(filtered.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Product p = filtered.get(i);
            model.addRow(new Object[]{p.getId(), p.getName(), p.getCategory(), p.getQuantity(),
                    String.format("₨%,.0f", p.getPrice()), p.getStatus(), "actions"});
        }
        pager.update(filtered.size(), page, PAGE_SIZE);
    }

    // ------------------------------------------------------------- actions
    private void viewProduct(String id) {
        try {
            Product p = pm.find(id);
            if (p == null) { Theme.error(this, "Product no longer exists."); load(false); return; }
            String desc = p.getDescription().isEmpty() ? "-" : Theme.esc(p.getDescription());
            JLabel text = new JLabel("<html><table cellpadding='2'>"
                    + "<tr><td><b>Product ID</b></td><td>" + Theme.esc(p.getId()) + "</td></tr>"
                    + "<tr><td><b>Name</b></td><td>" + Theme.esc(p.getName()) + "</td></tr>"
                    + "<tr><td><b>Category</b></td><td>" + Theme.esc(p.getCategory()) + "</td></tr>"
                    + "<tr><td><b>Description</b></td><td>" + desc + "</td></tr>"
                    + "<tr><td><b>Quantity</b></td><td>" + p.getQuantity() + "</td></tr>"
                    + "<tr><td><b>Unit Price</b></td><td>" + String.format("₨%,.2f", p.getPrice()) + "</td></tr>"
                    + "<tr><td><b>Total Value</b></td><td>" + String.format("₨%,.2f", p.getValue()) + "</td></tr>"
                    + "<tr><td><b>Minimum Stock</b></td><td>" + p.getMinStock() + "</td></tr>"
                    + "<tr><td><b>Status</b></td><td>" + p.getStatus() + "</td></tr></table></html>");
            text.setFont(Theme.BODY);
            JPanel box = new JPanel(new BorderLayout(16, 0));
            BufferedImage img = ImageUtil.load(p.getImagePath());
            if (img == null) img = Images.get("laptop");
            if (img != null) box.add(new JLabel(ImageUtil.fit(img, 130, 130)), BorderLayout.WEST);
            box.add(text, BorderLayout.CENTER);
            JOptionPane.showMessageDialog(this, box, "Product Details", JOptionPane.PLAIN_MESSAGE);
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void editProduct(String id) {
        try {
            Product p = pm.find(id);
            if (p == null) { Theme.error(this, "Product no longer exists."); load(false); return; }

            JTextField idF = Theme.field();
            idF.setText(p.getId());
            idF.setEditable(false);
            JTextField nameF = Theme.field();
            nameF.setText(p.getName());
            JComboBox<String> catF = new JComboBox<>(pm.categoryNames().toArray(new String[0]));
            catF.setEditable(true);
            catF.setSelectedItem(p.getCategory());
            JTextField descF = Theme.field();
            descF.setText(p.getDescription());
            JTextField qtyF = Theme.field();
            qtyF.setText(String.valueOf(p.getQuantity()));
            qtyF.setEditable(false);
            qtyF.setToolTipText("Quantity changes only through Stock In (PUSH) and Stock Out (POP).");
            JTextField priceF = Theme.field();
            priceF.setText(String.valueOf(p.getPrice()));
            JTextField minF = Theme.field();
            minF.setText(String.valueOf(p.getMinStock()));

            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Product ID"));       form.add(idF);
            form.add(new JLabel("Product Name"));     form.add(nameF);
            form.add(new JLabel("Category"));         form.add(catF);
            form.add(new JLabel("Description"));      form.add(descF);
            form.add(new JLabel("Quantity (read-only)")); form.add(qtyF);
            form.add(new JLabel("Price (PKR)"));        form.add(priceF);
            form.add(new JLabel("Minimum Stock"));    form.add(minF);

            while (true) {
                int ans = JOptionPane.showConfirmDialog(this, form, "Update Product",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (ans != JOptionPane.OK_OPTION) return;
                try {
                    Product updated = Product.parse(idF.getText(), nameF.getText(),
                            String.valueOf(catF.getSelectedItem()), qtyF.getText(), priceF.getText(),
                            descF.getText(), minF.getText(), p.getImagePath());
                    pm.update(updated);
                    load(false);
                    return;
                } catch (IllegalArgumentException ex) {
                    Theme.error(this, ex.getMessage());      // loop again with the same input
                }
            }
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void deleteProduct(String id) {
        if (!pm.isAdmin()) {
            Theme.error(this, "Only an Admin can delete products.");
            return;
        }
        int ans = JOptionPane.showConfirmDialog(this, "Delete product " + id + " and all of its batches on the stack?\n"
                        + "This cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;
        try {
            pm.delete(id);
            refresh();
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("inventory.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) f = new File(f.getParentFile(), f.getName() + ".csv");
        if (f.exists() && JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?",
                "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            out.print('\uFEFF');                       // BOM so Excel reads UTF-8 correctly
            out.println("Product ID,Product Name,Category,Description,Quantity,Price,Minimum Stock,Status");
            for (Product p : filtered) {
                out.println(csv(p.getId()) + "," + csv(p.getName()) + "," + csv(p.getCategory()) + ","
                        + csv(p.getDescription()) + "," + p.getQuantity() + "," + String.format("%.2f", p.getPrice())
                        + "," + p.getMinStock() + "," + csv(p.getStatus()));
            }
            if (out.checkError()) throw new IOException("write error");
        } catch (IOException ex) {
            Theme.error(this, "Could not write the file: " + ex.getMessage());
            return;
        }
        Theme.info(this, filtered.size() + " product(s) exported to:\n" + f.getAbsolutePath());
    }

    static String csv(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;   // neutralise spreadsheet formulas
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ------------------------------------------------------------ renderers
    private static DefaultTableCellRenderer centered() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, false, r, c);
                if (!s) setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xF6FAFF));
                return this;
            }
        };
        r.setHorizontalAlignment(SwingConstants.CENTER);
        return r;
    }

    /** Colored "pill" showing In Stock / Low Stock / Out of Stock. */
    private static class StatusRenderer extends JPanel implements TableCellRenderer {
        private String text = "";
        private Color fg = Theme.GREEN;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            text = String.valueOf(v);
            fg = "In Stock".equals(text) ? Theme.GREEN : "Low Stock".equals(text) ? Theme.ORANGE : Theme.RED;
            setBackground(s ? t.getSelectionBackground() : r % 2 == 0 ? Color.WHITE : new Color(0xF6FAFF));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            g2.setFont(Theme.BOLD.deriveFont(12f));
            int tw = g2.getFontMetrics().stringWidth(text);
            int w = tw + 26, h = 24;
            int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            g2.setColor(Theme.alpha(fg, 34));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(fg);
            g2.drawString(text, x + 13, y + (h + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Two small rounded buttons: edit (blue) and delete (red). */
    private static class ActionsRenderer extends JPanel implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            setBackground(s ? t.getSelectionBackground() : r % 2 == 0 ? Color.WHITE : new Color(0xF6FAFF));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            int total = ACTION_BTN * 2 + ACTION_GAP;
            int x = (getWidth() - total) / 2, y = (getHeight() - ACTION_BTN) / 2;
            g2.setColor(Theme.BLUE);
            g2.fillRoundRect(x, y, ACTION_BTN, ACTION_BTN, 8, 8);
            Icons.paint(g2, "edit", x + 6, y + 6, 16, Color.WHITE);
            int x2 = x + ACTION_BTN + ACTION_GAP;
            g2.setColor(Theme.RED);
            g2.fillRoundRect(x2, y, ACTION_BTN, ACTION_BTN, 8, 8);
            Icons.paint(g2, "trash", x2 + 6, y + 6, 16, Color.WHITE);
            g2.dispose();
        }
    }
}
