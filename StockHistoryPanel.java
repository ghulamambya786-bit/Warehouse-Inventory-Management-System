import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Stock History: record of PUSH (Stock In) and POP (Stock Out) operations, plus product changes, loaded from MySQL. */
public class StockHistoryPanel extends JPanel implements Refreshable {
    private static final int PAGE_SIZE = 8;

    private final ProductManager pm;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Date & Time", "Product", "Action", "Quantity", "Batch", "By", "Note"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JComboBox<String> filter = new JComboBox<>(new String[]{
            "All actions", "Inventory (Added / Updated / Removed)", "Stack (PUSH / POP)"});
    private final SearchBox searchBox = new SearchBox("Search history...", 240);
    private final Pager pager = new Pager("records", this::goToPage);
    private List<Object[]> rows = new ArrayList<>();
    private int page;

    public StockHistoryPanel(ProductManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel top = Theme.transparent(new BorderLayout());
        top.add(Theme.pageTitle("Stock History", "clock"), BorderLayout.WEST);
        JPanel tools = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filter.setFont(Theme.BODY);
        filter.setPreferredSize(new Dimension(260, 40));
        JButton reload = Theme.button("Refresh", Theme.TEAL, Color.WHITE, "refresh");
        tools.add(searchBox);
        tools.add(filter);
        tools.add(reload);
        top.add(tools, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        filter.setSelectedIndex(2);        // PUSH / POP records first

        JTable table = new JTable(model);
        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setCellRenderer(new ActionRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(centered());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        RoundPanel card = new RoundPanel(new BorderLayout(0, 10), Color.WHITE, 18);
        card.setBorderColor(Theme.BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 14, 12));
        card.add(sp, BorderLayout.CENTER);
        card.add(pager, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        reload.addActionListener(e -> refresh());
        filter.addActionListener(e -> refresh());
        searchBox.getField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applySearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applySearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applySearch(); }
        });
    }

    private List<Object[]> all = new ArrayList<>();

    @Override
    public void refresh() {
        try {
            all = pm.getHistory(filter.getSelectedIndex(), 500);
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
            return;
        }
        applySearch();
    }

    private void applySearch() {
        String q = searchBox.getField().getText().trim().toLowerCase();
        rows = new ArrayList<>();
        for (Object[] r : all) {
            if (q.isEmpty() || (r[1] + " " + r[2] + " " + r[4] + " " + r[5] + " " + r[7]).toLowerCase().contains(q)) rows.add(r);
        }
        page = 0;
        fillPage();
    }

    private void goToPage(int p) {
        page = p;
        fillPage();
    }

    private void fillPage() {
        model.setRowCount(0);
        int from = page * PAGE_SIZE, to = Math.min(rows.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Object[] r = rows.get(i);
            model.addRow(new Object[]{r[0], r[1], r[2], r[3], r[7], r[4], r[5]});
        }
        pager.update(rows.size(), page, PAGE_SIZE);
    }

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

    /** Colors the action text: Added, Updated, Removed, Stock In, Stock Out, PUSH, POP. */
    private static class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v, s, false, r, c);
            if (!s) setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xF6FAFF));
            setFont(Theme.BOLD);
            switch (String.valueOf(v)) {
                case "Added":     setForeground(Theme.ORANGE); break;
                case "Updated":   setForeground(Theme.GREEN); break;
                case "Removed":   setForeground(Theme.RED); break;
                case "Stock In":  setForeground(Theme.TEAL); break;
                case "Stock Out": setForeground(new Color(0xD35400)); break;
                case "PUSH":      setForeground(Theme.BLUE); break;
                case "POP":       setForeground(Theme.PURPLE); break;
                default:          setForeground(Theme.TEXT);
            }
            return this;
        }
    }
}
