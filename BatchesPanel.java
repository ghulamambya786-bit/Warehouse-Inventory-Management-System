import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Batch Management: every batch on the stack with Batch ID, quantity, date and supplier (search, sort, edit). */
public class BatchesPanel extends JPanel implements Refreshable {
    private static final int PAGE_SIZE = 8;
    private static final String[] SORTS = {"Stack order (TOP first)", "Batch ID", "Product name", "Quantity (High to Low)",
            "Quantity (Low to High)", "Date (Newest first)", "Date (Oldest first)", "Supplier"};

    private final ProductManager pm;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"From TOP", "Batch ID", "Product", "Quantity", "Batch Date", "Supplier", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final SearchBox searchBox = new SearchBox("Search batch, product, supplier...", 300);
    private final JComboBox<String> sortBox = new JComboBox<>(SORTS);
    private final Pager pager = new Pager("batches", this::goToPage);
    private final List<Batch> shown = new ArrayList<>();
    private List<Batch> filtered = new ArrayList<>();
    private int page;

    public BatchesPanel(ProductManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel top = Theme.transparent(new BorderLayout(0, 12));
        top.add(Theme.pageTitle("Batch Management", "box"), BorderLayout.NORTH);
        JPanel tools = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        sortBox.setFont(Theme.BODY);
        sortBox.setPreferredSize(new Dimension(210, 40));
        JButton edit = Theme.button("Edit Batch", Theme.GOLD, Theme.NAVY, "edit");
        JButton export = Theme.button("Export", Theme.TEAL, Color.WHITE, "download");
        tools.add(searchBox);
        tools.add(sortBox);
        tools.add(edit);
        tools.add(export);
        top.add(tools, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        Theme.styleTable(table);
        int[] widths = {80, 90, 190, 90, 110, 200, 110};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        RoundPanel card = new RoundPanel(new BorderLayout(0, 10), Color.WHITE, 18);
        card.setBorderColor(Theme.BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 14, 12));
        card.add(sp, BorderLayout.CENTER);
        card.add(pager, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        searchBox.getField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { load(true); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { load(true); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { load(true); }
        });
        sortBox.addActionListener(e -> load(true));
        edit.addActionListener(e -> editSelected());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) editSelected(); }
        });
        export.addActionListener(e -> {
            List<Object[]> rows = new ArrayList<>();
            for (Batch b : filtered) rows.add(new Object[]{b.getBatchId(), b.getProductId(), b.getProductName(),
                    b.getQuantity(), b.getDate(), b.getSupplier(), String.format("%.2f", b.getValue())});
            CsvUtil.export(this, "batches.csv", new String[]{"Batch ID", "Product ID", "Product", "Quantity",
                    "Batch Date", "Supplier", "Value"}, rows);
        });
    }

    @Override
    public void refresh() { load(false); }

    private Comparator<Batch> comparator() {
        switch (sortBox.getSelectedIndex()) {
            case 1: return Comparator.comparing(Batch::getBatchId);
            case 2: return Comparator.comparing(b -> b.getProductName().toLowerCase());
            case 3: return Comparator.comparingInt(Batch::getQuantity).reversed();
            case 4: return Comparator.comparingInt(Batch::getQuantity);
            case 5: return Comparator.comparing(Batch::getDate).reversed();
            case 6: return Comparator.comparing(Batch::getDate);
            case 7: return Comparator.comparing(b -> b.getSupplier().toLowerCase());
            default: return null;                       // keep stack order
        }
    }

    private void load(boolean resetPage) {
        try {
            String q = searchBox.getField().getText().trim().toLowerCase();
            List<Batch> all = pm.getBatches();           // TOP first
            filtered = new ArrayList<>();
            for (Batch b : all) {
                if (q.isEmpty() || (b.getBatchId() + " " + b.getProductId() + " " + b.getProductName() + " "
                        + b.getSupplier()).toLowerCase().contains(q)) filtered.add(b);
            }
            Comparator<Batch> c = comparator();
            final List<Batch> order = all;
            if (c != null) filtered.sort(c);
            if (resetPage) page = 0;
            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = Math.min(page, pages - 1);
            fillPage(order);
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void goToPage(int p) {
        page = p;
        fillPage(pm.getBatches());
    }

    private void fillPage(List<Batch> all) {
        model.setRowCount(0);
        shown.clear();
        int from = page * PAGE_SIZE, to = Math.min(filtered.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Batch b = filtered.get(i);
            shown.add(b);
            model.addRow(new Object[]{all.indexOf(b) + 1, b.getBatchId(), b.getProductId() + " - " + b.getProductName(),
                    b.getQuantity(), b.getDate(), b.getSupplier().isEmpty() ? "-" : b.getSupplier(),
                    String.format("₨%,.2f", b.getValue())});
        }
        pager.update(filtered.size(), page, PAGE_SIZE);
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            Theme.info(this, "Select a batch in the table first.");
            return;
        }
        Batch b = shown.get(row);
        JTextField dateF = Theme.field();
        dateF.setText(b.getDate().toString());
        JTextField supF = Theme.field();
        supF.setText(b.getSupplier());
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(Theme.label("Batch ID"));
        form.add(new javax.swing.JLabel(b.getBatchId() + "  (" + b.getProductName() + " x" + b.getQuantity() + ")"));
        form.add(Theme.label("Batch Date"));
        form.add(dateF);
        form.add(Theme.label("Supplier"));
        form.add(supF);
        while (true) {
            int ans = JOptionPane.showConfirmDialog(this, form, "Edit Batch", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (ans != JOptionPane.OK_OPTION) return;
            try {
                pm.updateBatch(b.getBatchId(), LocalDate.parse(dateF.getText().trim()), supF.getText());
                load(false);
                return;
            } catch (DateTimeParseException ex) {
                Theme.error(this, "Batch date must look like 2026-11-08 (yyyy-MM-dd).");
            } catch (IllegalArgumentException ex) {
                Theme.error(this, ex.getMessage());
            } catch (ProductManager.DbException ex) {
                Theme.error(this, ex.getMessage());
                return;
            }
        }
    }
}
