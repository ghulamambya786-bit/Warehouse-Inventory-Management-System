import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.util.HashSet;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JComboBox;
import java.util.Set;
import java.util.function.Consumer;

/** Stack operations (PUSH, POP, PEEK, isEmpty, isFull, search) + live TOP-to-BOTTOM view of the batch stack. */
public class StackPanel extends JPanel implements Refreshable {
    private final ProductManager pm;
    private final Consumer<String> nav;

    private final StackVisual visual = new StackVisual();
    private final JScrollPane visualScroll = new JScrollPane(visual);
    private final JTextField searchField = Theme.field();
    private final JLabel statusLabel = new JLabel();
    private final RoundPanel resultBody = new RoundPanel(null, new Color(0xDDF3EC), 18);
    private final ResultIcon resultIcon = new ResultIcon();
    private final JLabel resultTitle = new JLabel("", SwingConstants.CENTER);
    private final JLabel resultDetail = new JLabel("", SwingConstants.CENTER);
    private final Set<String> highlight = new HashSet<>();

    public StackPanel(ProductManager pm, Consumer<String> nav) {
        this.pm = pm;
        this.nav = nav;

        setLayout(new BorderLayout(0, 18));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        add(Theme.pageTitle("Stack Visualization & Operations", "layers"), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridy = 0;
        gc.insets = new java.awt.Insets(0, 0, 0, 16);
        gc.gridx = 0; gc.weightx = 0.27;
        center.add(buildOperationsCard(), gc);
        gc.gridx = 1; gc.weightx = 0.38;
        center.add(buildVisualCard(), gc);
        gc.gridx = 2; gc.weightx = 0.35;
        gc.insets = new java.awt.Insets(0, 0, 0, 0);
        center.add(buildResultCard(), gc);
        add(center, BorderLayout.CENTER);

        showResult(true, "Ready", "Use POP, PEEK, isEmpty, isFull or search a batch.<br>New batches are added with PUSH (Stock In).");
    }

    private CustomStack stack() { return pm.getStack(); }

    // ------------------------------------------------------------------ UI
    private JPanel buildOperationsCard() {
        JPanel card = Theme.card("Operations");

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchField.setPreferredSize(new Dimension(100, 40));
        searchField.setToolTipText("Batch ID, product or supplier");
        JButton find = Theme.button("Search", Theme.TEAL, Color.WHITE, "search");
        find.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(find, BorderLayout.EAST);
        JPanel searchBox = new JPanel(new GridLayout(0, 1, 0, 6));
        searchBox.setOpaque(false);
        searchBox.add(Theme.label("Search batch in stack:"));
        searchBox.add(searchRow);

        JPanel btns = new JPanel(new GridLayout(0, 1, 0, 10));
        btns.setOpaque(false);
        JButton push = Theme.button("PUSH  |  Add Batch", Theme.GREEN, Color.WHITE);
        JButton pop = Theme.button("POP  |  Remove TOP", Theme.RED, Color.WHITE);
        JButton peek = Theme.button("PEEK", Theme.BLUE, Color.WHITE);
        JButton empty = Theme.button("isEmpty()?", Theme.PURPLE, Color.WHITE);
        JButton full = Theme.button("isFull()?", Theme.ORANGE, Color.WHITE);
        for (JButton b : new JButton[]{push, pop, peek, empty, full}) {
            b.setPreferredSize(new Dimension(200, 44));
            btns.add(b);
        }

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(searchBox, BorderLayout.NORTH);
        JPanel btnWrap = Theme.transparent(new BorderLayout());
        btnWrap.add(btns, BorderLayout.NORTH);
        body.add(btnWrap, BorderLayout.CENTER);
        card.add(body, BorderLayout.CENTER);

        push.addActionListener(e -> doPush());
        pop.addActionListener(e -> doPop());
        peek.addActionListener(e -> doPeek());
        find.addActionListener(e -> doSearch());
        searchField.addActionListener(e -> doSearch());
        empty.addActionListener(e -> showResult(true, "isEmpty() = " + stack().isEmpty(),
                stack().isEmpty() ? "TOP == -1, the stack is empty." : "The stack has " + stack().size() + " batch(es)."));
        full.addActionListener(e -> showResult(true, "isFull() = " + stack().isFull(),
                stack().isFull() ? "TOP == capacity - 1, no more PUSH possible."
                        : "There is room for " + (stack().getCapacity() - stack().size()) + " more batch(es)."));
        return card;
    }

    private JPanel buildVisualCard() {
        JPanel card = Theme.card("Stack Visualization (TOP to BOTTOM)");
        visualScroll.setBorder(BorderFactory.createEmptyBorder());
        visualScroll.getVerticalScrollBar().setUnitIncrement(18);
        visualScroll.getViewport().setBackground(new Color(0xE3EDF9));
        card.add(visualScroll, BorderLayout.CENTER);
        statusLabel.setFont(Theme.BOLD);
        statusLabel.setForeground(Theme.NAVY);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(statusLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildResultCard() {
        JPanel card = Theme.card("Result");
        resultBody.setShadow(false);
        resultBody.setLayout(new BoxLayout(resultBody, BoxLayout.Y_AXIS));
        resultBody.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        resultTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        resultTitle.setForeground(Theme.NAVY);
        resultDetail.setFont(Theme.BODY);
        resultDetail.setForeground(Theme.TEXT);
        for (Component l : new Component[]{resultIcon, resultTitle, resultDetail}) {
            ((javax.swing.JComponent) l).setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        resultIcon.setPreferredSize(new Dimension(84, 84));
        resultIcon.setMaximumSize(new Dimension(84, 84));
        resultBody.add(Box.createVerticalGlue());
        resultBody.add(resultIcon);
        resultBody.add(Box.createVerticalStrut(12));
        resultBody.add(resultTitle);
        resultBody.add(Box.createVerticalStrut(8));
        resultBody.add(resultDetail);
        resultBody.add(Box.createVerticalGlue());
        card.add(resultBody, BorderLayout.CENTER);
        return card;
    }

    private void showResult(boolean ok, String title, String detail) {
        resultIcon.set(ok);
        resultTitle.setText("<html><div style='text-align:center;width:200px'>" + Theme.esc(title) + "</div></html>");
        resultDetail.setText("<html><div style='text-align:center;width:210px'>" + detail + "</div></html>");
        resultBody.setFill(ok ? new Color(0xDDF3EC) : new Color(0xFDE3E1));
        updateStatus();
        visual.sync();
    }

    private void updateStatus() {
        Batch topBatch = stack().isEmpty() ? null : stack().peek();
        statusLabel.setText("Capacity: " + stack().size() + " / " + stack().getCapacity()
                + "    |    TOP: " + (topBatch == null ? "EMPTY" : topBatch.getBatchId())
                + "    |    Empty slots: " + (stack().getCapacity() - stack().size())
                + "    |    " + (stack().isFull() ? "FULL" : "NOT FULL"));
    }

    @Override
    public void refresh() {
        highlight.clear();
        updateStatus();
        visual.sync();
    }

    // ---------------------------------------------------------- operations
    private static String batchHtml(Batch b) {
        return "<b>" + Theme.esc(b.getBatchId()) + "</b> - " + Theme.esc(b.getProductName()) + "<br>Quantity: " + b.getQuantity()
                + "<br>Date: " + b.getDate() + "<br>Supplier: " + (b.getSupplier().isEmpty() ? "-" : Theme.esc(b.getSupplier()));
    }

    /** PUSH is performed directly from this page; Stock In is not a separate navigation page. */
    private void doPush() {
        if (stack().isFull()) {
            showResult(false, "Stack Overflow!", "Capacity is 10 batches. The 11th batch cannot be pushed.");
            return;
        }
        try {
            List<Product> products = pm.getAll();
            if (products.isEmpty()) { showResult(false, "No products", "Add a product before pushing a batch."); return; }
            JComboBox<Product> productBox = new JComboBox<>();
            for (Product product : products) productBox.addItem(product);
            JTextField qty = Theme.field(); qty.setText("1");
            JTextField price = Theme.field(); price.setText("0");
            JTextField supplier = Theme.field();
            JTextField date = Theme.field(); date.setText(LocalDate.now().toString());
            JTextField batchId = Theme.field(); batchId.setText(pm.nextBatchId());
            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Batch ID")); form.add(batchId);
            form.add(new JLabel("Product")); form.add(productBox);
            form.add(new JLabel("Quantity")); form.add(qty);
            form.add(new JLabel("Unit price (PKR)")); form.add(price);
            form.add(new JLabel("Batch date (yyyy-MM-dd)")); form.add(date);
            form.add(new JLabel("Supplier")); form.add(supplier);
            int answer = JOptionPane.showConfirmDialog(this, form, "PUSH — Add Batch to TOP", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            Product selected = (Product) productBox.getSelectedItem();
            if (selected == null) return;
            Batch b = pm.pushBatch(batchId.getText(), selected.getId(), Integer.parseInt(qty.getText().trim()),
                    LocalDate.parse(date.getText().trim()), supplier.getText(), Double.parseDouble(price.getText().trim()));
            showResult(true, "PUSH Successful!", batchHtml(b) + "<br>added to the TOP of the stack.");
        } catch (CustomStack.StackOverflowException ex) {
            showResult(false, "Stack Overflow!", Theme.esc(ex.getMessage()));
        } catch (Exception ex) {
            showResult(false, "PUSH Failed", Theme.esc(ex.getMessage() == null ? "Please check the entered values." : ex.getMessage()));
        }
    }

    private void doPop() {
        if (stack().isEmpty()) {
            showResult(false, "Stack Underflow!", "The stack is empty, nothing to pop.");
            return;
        }
        Batch top = pm.peekBatch();
        int ans = JOptionPane.showConfirmDialog(this, "POP batch " + top.getBatchId() + " (" + top.getQuantity()
                        + " x " + top.getProductName() + ") from the TOP of the stack?",
                "Confirm POP", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;
        try {
            Batch b = pm.popBatch("Stack Visualization");
            highlight.clear();
            showResult(true, "POP Successful!", batchHtml(b) + "<br>removed from the TOP.");
        } catch (CustomStack.StackUnderflowException ex) {
            showResult(false, "Stack Underflow!", Theme.esc(ex.getMessage()));
        } catch (ProductManager.DbException ex) {
            showResult(false, "Database Error", Theme.esc(ex.getMessage()));
        }
    }

    private void doPeek() {
        try {
            Batch b = pm.peekBatch();                        // PEEK: read only, TOP unchanged
            highlight.clear();
            highlight.add(b.getBatchId());
            showResult(true, "TOP Batch: " + b.getBatchId(), batchHtml(b) + "<br>TOP = " + stack().getTop());
        } catch (CustomStack.StackUnderflowException ex) {
            showResult(false, "Stack Underflow!", Theme.esc(ex.getMessage()));
        }
    }

    private void doSearch() {
        String k = searchField.getText().trim();
        highlight.clear();
        if (k.isEmpty()) {
            showResult(false, "Nothing to search", "Type a Batch ID, product name or supplier first.");
            return;
        }
        try {
            CustomStack.SearchResult r = pm.searchBatches(k);
            if (r.matches.isEmpty()) {
                showResult(false, "No batch found", "Nothing matches <b>" + Theme.esc(k) + "</b>.<br>Checked "
                        + r.examined + " batch(es) from the TOP.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (int i = 0; i < r.matches.size(); i++) {
                Batch b = r.matches.get(i);
                highlight.add(b.getBatchId());
                if (shown++ < 4) {
                    sb.append("<b>").append(Theme.esc(b.getBatchId())).append("</b> - ").append(Theme.esc(b.getProductName()))
                            .append(" x").append(b.getQuantity()).append("<br>position ").append(r.depths.get(i))
                            .append(" from TOP<br>");
                }
            }
            if (r.matches.size() > shown && shown >= 4) sb.append("+ ").append(r.matches.size() - 4).append(" more<br>");
            sb.append("Checked ").append(r.examined).append(" batch(es) from the TOP.");
            showResult(true, r.matches.size() + (r.matches.size() == 1 ? " batch found" : " batches found"), sb.toString());
            visual.scrollToFirstMatch();
        } catch (ProductManager.DbException ex) {
            showResult(false, "Database Error", Theme.esc(ex.getMessage()));
        }
    }

    // --------------------------------------------------------- drawing
    /** Round badge with a check mark (success) or a cross (error). */
    private static class ResultIcon extends JPanel {
        private boolean ok = true;
        private double scale = 1;
        private javax.swing.Timer timer;

        ResultIcon() { setOpaque(false); }

        void set(boolean ok) {
            this.ok = ok;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            double d = (Math.min(getWidth(), getHeight()) - 4) * Math.max(0, scale);
            if (d > 1) {
                double x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
                g2.setColor(ok ? Theme.GREEN : Theme.RED);
                g2.fill(new Ellipse2D.Double(x, y, d, d));
                Icons.paint(g2, ok ? "check" : "plus", x + d * 0.2, y + d * 0.2, d * 0.6, Color.WHITE);
            }
            g2.dispose();
        }
    }

    /**
     * One bar per batch, the TOP batch first; matching batches of a search are highlighted.
     * Animation (it compares the stack with the previous drawing):
     *   PUSH: the new batch drops in from above and fades in, the older bars slide down one place.
     *   POP : the removed batch flies up and fades out, the remaining bars slide up one place.
     *   PEEK / search: the highlight border is shown.
     */
    private class StackVisual extends JPanel {
        private static final int ROW = 40, GAP = 6, PAD = 14;
        private static final int NONE = 0, PUSHING = 1, POPPING = 2;

        private Batch[] lastBatches;          // the stack at the previous drawing, BOTTOM to TOP
        private int mode = NONE;
        private double eased = 1;             // animation progress 0..1 (already eased)
        private Batch ghost;                  // the batch that flies out during a POP
        private javax.swing.Timer anim;

        StackVisual() {
            setBackground(new Color(0xE3EDF9));
            setPreferredSize(new Dimension(280, PAD * 2 + ProductManager.STACK_CAPACITY * (ROW + GAP) + 60));
        }

        private boolean samePrefix(Batch[] a, Batch[] b, int len) {
            for (int i = 0; i < len; i++) if (!a[i].getBatchId().equals(b[i].getBatchId())) return false;
            return true;
        }

        /** Called whenever the stack may have changed: starts a PUSH or POP animation when it did. */
        void sync() {
            Batch[] cur = stack().toArray();
            if (lastBatches != null) {
                int newMode = NONE;
                if (cur.length == lastBatches.length + 1 && samePrefix(lastBatches, cur, lastBatches.length)) {
                    newMode = PUSHING;
                    ghost = null;
                } else if (cur.length == lastBatches.length - 1 && samePrefix(cur, lastBatches, cur.length)) {
                    newMode = POPPING;
                    ghost = lastBatches[lastBatches.length - 1];
                }
                if (newMode != NONE) {
                    mode = newMode;
                    eased = 0;
                    if (anim != null) anim.stop();
                    anim = Anim.play(400, 0, p -> {
                        eased = Anim.easeOut(p);
                        if (p >= 1) {
                            mode = NONE;
                            ghost = null;
                        }
                        repaint();
                    });
                }
            }
            lastBatches = cur;
            setPreferredSize(new Dimension(280, PAD * 2 + ProductManager.STACK_CAPACITY * (ROW + GAP) + 60));
            revalidate();
            repaint();
        }

        void scrollToFirstMatch() {
            Batch[] arr = stack().toArray();
            for (int k = 0; k < arr.length; k++) {
                if (highlight.contains(arr[arr.length - 1 - k].getBatchId())) {
                    final int y = PAD + k * (ROW + GAP);
                    javax.swing.SwingUtilities.invokeLater(() ->
                            scrollRectToVisible(new Rectangle(0, Math.max(0, y - 40), 10, ROW + 80)));
                    return;
                }
            }
        }

        private void drawRow(Graphics2D g2, Batch b, int x, int y, int boxW, boolean top, boolean hit, float alpha) {
            java.awt.Composite old = g2.getComposite();
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
            g2.setColor(top ? Theme.GOLD : hit ? Theme.TEAL : Theme.NAVY);
            g2.fillRoundRect(x, y, boxW, ROW, 12, 12);
            if (hit) {
                g2.setColor(top ? Theme.RED : Theme.GOLD);
                g2.setStroke(new java.awt.BasicStroke(3f));
                g2.drawRoundRect(x, y, boxW, ROW, 12, 12);
                g2.setStroke(new java.awt.BasicStroke(1f));
            }
            g2.setColor(top ? Theme.NAVY : Color.WHITE);
            g2.setFont(Theme.BOLD);
            FontMetrics fm = g2.getFontMetrics();
            String t = b.getBatchId() + "  " + b.getProductName() + "  x" + b.getQuantity();
            while (fm.stringWidth(t) > boxW - 16 && t.length() > 4) t = t.substring(0, t.length() - 2);
            g2.drawString(t, x + 12, y + (ROW + fm.getAscent() - fm.getDescent()) / 2);
            g2.setComposite(old);
        }

        private void tag(Graphics2D g2, String text, int x, int y, float alpha) {
            java.awt.Composite old = g2.getComposite();
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
            g2.setColor(Theme.NAVY);
            g2.setFont(Theme.BOLD);
            g2.drawString(text, x, y);
            g2.setComposite(old);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            int w = getWidth(), h = getHeight();
            Batch[] arr = stack().toArray();
            int n = arr.length;
            int boxW = Math.max(150, w - 130);
            int x = PAD;
            if (n == 0) {
                g2.setColor(Theme.MUTED);
                g2.setFont(Theme.BODY);
                String msg = "Stack is empty";
                g2.drawString(msg, (w - g2.getFontMetrics().stringWidth(msg)) / 2, h / 2);
            }
            for (int k = 0; k < n; k++) {
                Batch b = arr[n - 1 - k];                          // k = 0 is the TOP batch
                int y = PAD + k * (ROW + GAP);
                float alpha = 1f;
                if (mode == PUSHING) {
                    if (k == 0) {                                  // the new batch drops in and fades in
                        y -= (int) ((1 - eased) * (ROW + GAP + 30));
                        alpha = (float) eased;
                    } else {                                       // older batches slide down one place
                        y -= (int) ((1 - eased) * (ROW + GAP));
                    }
                } else if (mode == POPPING) {                      // remaining batches slide up one place
                    y += (int) ((1 - eased) * (ROW + GAP));
                }
                drawRow(g2, b, x, y, boxW, k == 0, highlight.contains(b.getBatchId()), alpha);
                if (k == 0) tag(g2, "\u2190 TOP", x + boxW + 10, y + ROW / 2 + 5, alpha);
                else if (k == n - 1) tag(g2, "\u2190 BOTTOM", x + boxW + 10, y + ROW / 2 + 5, 1f);
            }
            // Draw all remaining fixed-capacity positions as empty slots.
            for (int k = n; k < ProductManager.STACK_CAPACITY; k++) {
                int y = PAD + k * (ROW + GAP);
                g2.setColor(new Color(0xC7D4E4));
                g2.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
                g2.drawRoundRect(x, y, boxW, ROW, 12, 12);
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.setColor(Theme.MUTED);
                g2.setFont(Theme.SMALL);
                String emptyText = "EMPTY SLOT";
                g2.drawString(emptyText, x + 12, y + 25);
            }
            if (mode == POPPING && ghost != null) {                // the popped batch flies up and fades out
                drawRow(g2, ghost, x, PAD - (int) (eased * (ROW + 20)), boxW, true, false, (float) (1 - eased));
            }
            g2.dispose();
        }
    }
}
