import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction History and the Stock In / Stock Out / Available Stock reports:
 * date and details of every stock operation, filterable by type, date range and keyword; CSV export.
 */
public class TransactionsPanel extends JPanel implements Refreshable {
    private static final int PAGE_SIZE = 9;
    private static final String[] MODES = {"All Transactions", "Stock In Report (PUSH)", "Stock Out Report (POP)",
            "Product Changes", "Available Stock Report"};
    private static final String[] TX_COLUMNS = {"Date & Time", "Product", "Action", "Quantity", "Batch", "By", "Details"};
    private static final String[] STOCK_COLUMNS = {"Product ID", "Product", "Category", "Batches", "Quantity",
            "Unit Price", "Value", "Status"};

    private final ProductManager pm;
    private final DefaultTableModel txModel = new DefaultTableModel(TX_COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel stockModel = new DefaultTableModel(STOCK_COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(txModel);
    private final JComboBox<String> modeBox = new JComboBox<>(MODES);
    private final JTextField fromF = Theme.field();
    private final JTextField toF = Theme.field();
    private final SearchBox searchBox = new SearchBox("Search...", 150);
    private final JLabel totals = new JLabel(" ");
    private final Pager pager = new Pager("records", this::goToPage);
    private List<Object[]> rows = new ArrayList<>();
    private int page;

    public TransactionsPanel(ProductManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel top = Theme.transparent(new BorderLayout(0, 12));
        top.add(Theme.pageTitle("Transactions & Reports", "list"), BorderLayout.NORTH);
        JPanel tools = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeBox.setFont(Theme.BODY);
        modeBox.setPreferredSize(new Dimension(215, 40));
        fromF.setPreferredSize(new Dimension(105, 40));
        toF.setPreferredSize(new Dimension(105, 40));
        fromF.setToolTipText("From date (yyyy-MM-dd), empty = no limit");
        toF.setToolTipText("To date (yyyy-MM-dd), empty = no limit");
        JButton apply = Theme.button("Apply", Theme.TEAL, Color.WHITE, "refresh");
        JButton export = Theme.button("Export CSV", Theme.NAVY, Color.WHITE, "download");
        tools.add(modeBox);
        tools.add(Theme.label("From"));
        tools.add(fromF);
        tools.add(Theme.label("To"));
        tools.add(toF);
        tools.add(searchBox);
        tools.add(apply);
        tools.add(export);
        top.add(tools, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        Theme.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        totals.setFont(Theme.BOLD);
        totals.setForeground(Theme.NAVY);
        JPanel bottom = Theme.transparent(new BorderLayout());
        bottom.add(totals, BorderLayout.NORTH);
        bottom.add(pager, BorderLayout.SOUTH);
        RoundPanel card = new RoundPanel(new BorderLayout(0, 10), Color.WHITE, 18);
        card.setBorderColor(Theme.BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 14, 12));
        card.add(sp, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        modeBox.addActionListener(e -> load());
        apply.addActionListener(e -> load());
        searchBox.getField().addActionListener(e -> load());
        export.addActionListener(e -> export());
    }

    @Override
    public void refresh() { load(); }

    private boolean stockMode() { return modeBox.getSelectedIndex() == 4; }

    private LocalDate parse(JTextField f, String label) {
        String t = f.getText().trim();
        if (t.isEmpty()) return null;
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(label + " date must look like 2026-11-08 (yyyy-MM-dd).");
        }
    }

    private void load() {
        try {
            boolean stock = stockMode();
            fromF.setEnabled(!stock);
            toF.setEnabled(!stock);
            rows = new ArrayList<>();
            String kw = searchBox.getField().getText().trim();
            if (stock) {
                java.util.Map<String, Integer> batchCount = new java.util.HashMap<>();
                for (Batch b : pm.getBatches()) batchCount.merge(b.getProductId(), 1, Integer::sum);
                int units = 0;
                double value = 0;
                for (Product p : pm.getAll()) {
                    if (!kw.isEmpty() && !(p.getId() + " " + p.getName() + " " + p.getCategory()).toLowerCase()
                            .contains(kw.toLowerCase())) continue;
                    rows.add(new Object[]{p.getId(), p.getName(), p.getCategory(), batchCount.getOrDefault(p.getId(), 0),
                            p.getQuantity(), String.format("%.2f", p.getPrice()), String.format("%.2f", p.getValue()),
                            p.getStatus()});
                    units += p.getQuantity();
                    value += p.getValue();
                }
                totals.setText(String.format("Available: %,d units in %d products, value ₨%,.2f", units, rows.size(), value));
            } else {
                String[] actions;
                switch (modeBox.getSelectedIndex()) {
                    case 1: actions = new String[]{"PUSH", "Stock In"}; break;
                    case 2: actions = new String[]{"POP", "Stock Out"}; break;
                    case 3: actions = new String[]{"Added", "Updated", "Removed"}; break;
                    default: actions = null;
                }
                LocalDate from = parse(fromF, "From"), to = parse(toF, "To");
                long units = 0;
                for (Object[] r : pm.getTransactions(actions, from, to, kw, 5000)) {
                    rows.add(new Object[]{r[0], r[1], r[2], r[3], r[7], r[4], r[5]});
                    units += ((Number) r[3]).longValue();
                }
                int m = modeBox.getSelectedIndex();
                totals.setText(m == 1 ? String.format("Total received: %,d units in %d batch(es)", units, rows.size())
                        : m == 2 ? String.format("Total sent out: %,d units in %d batch(es)", units, rows.size())
                        : rows.size() + " record(s)");
            }
            table.setModel(stock ? stockModel : txModel);
            int[] w = stock ? new int[]{80, 150, 120, 70, 80, 90, 100, 100} : new int[]{135, 120, 70, 70, 70, 80, 330};
            for (int i = 0; i < w.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
            page = 0;
            fillPage();
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void goToPage(int p) {
        page = p;
        fillPage();
    }

    private void fillPage() {
        DefaultTableModel m = stockMode() ? stockModel : txModel;
        m.setRowCount(0);
        int from = page * PAGE_SIZE, to = Math.min(rows.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) m.addRow(rows.get(i));
        pager.update(rows.size(), page, PAGE_SIZE);
    }

    private void export() {
        String name = MODES[modeBox.getSelectedIndex()].toLowerCase().replaceAll("[^a-z]+", "_") + ".csv";
        CsvUtil.export(this, name, stockMode() ? STOCK_COLUMNS : TX_COLUMNS, rows);
    }
}
