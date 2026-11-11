import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.IntConsumer;

/** "Showing 1 to 8 of 25 products" label plus numbered page buttons. */
public class Pager extends JPanel {
    private final JLabel info = new JLabel();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    private final IntConsumer onPage;
    private final String noun;

    public Pager(String noun, IntConsumer onPage) {
        super(new BorderLayout());
        this.noun = noun;
        this.onPage = onPage;
        setOpaque(false);
        buttons.setOpaque(false);
        info.setFont(Theme.SMALL);
        info.setForeground(Theme.MUTED);
        add(info, BorderLayout.WEST);
        add(buttons, BorderLayout.EAST);
    }

    /** @param page zero based current page */
    public void update(int total, int page, int pageSize) {
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        int from = total == 0 ? 0 : page * pageSize + 1;
        int to = Math.min(total, (page + 1) * pageSize);
        info.setText("Showing " + from + " to " + to + " of " + total + " " + noun);
        buttons.removeAll();
        buttons.add(pageButton("\u2039", Math.max(0, page - 1), false, page > 0));
        int start = Math.max(0, Math.min(page - 2, pages - 5));
        int end = Math.min(pages, start + 5);
        for (int i = start; i < end; i++) buttons.add(pageButton(String.valueOf(i + 1), i, i == page, true));
        buttons.add(pageButton("\u203A", Math.min(pages - 1, page + 1), false, page < pages - 1));
        buttons.revalidate();
        buttons.repaint();
    }

    private JButton pageButton(String text, int target, boolean current, boolean enabled) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.antialias(g2);
                g2.setColor(current ? Theme.NAVY : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(current ? Theme.NAVY : Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setFont(Theme.BOLD);
        b.setForeground(current ? Color.WHITE : enabled ? Theme.NAVY : Theme.MUTED);
        b.setPreferredSize(new Dimension(30, 28));
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setEnabled(enabled);
        if (enabled) b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> onPage.accept(target));
        return b;
    }
}
