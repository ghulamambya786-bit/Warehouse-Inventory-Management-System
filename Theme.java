import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** Shared colors, fonts and small UI helpers (Navy Blue + Turquoise + Golden Yellow + Orange + Red). */
public class Theme {
    public static final Color NAVY = new Color(0x082B49);
    public static final Color NAVY_LIGHT = new Color(0x0E3A62);
    public static final Color TEAL = new Color(0x00A6A6);
    public static final Color GOLD = new Color(0xFFBE0B);
    public static final Color ORANGE = new Color(0xFB8500);
    public static final Color RED = new Color(0xE63946);
    public static final Color LIGHT = new Color(0xEAF4FF);
    public static final Color BG = LIGHT;
    public static final Color WHITE = Color.WHITE;
    public static final Color GREEN = new Color(0x1FAA59);
    public static final Color BLUE = new Color(0x1E88E5);
    public static final Color PURPLE = new Color(0x7B3FBF);
    public static final Color TEXT = new Color(0x1D2B3C);
    public static final Color MUTED = new Color(0x6B7A90);
    public static final Color BORDER = new Color(0xD3E1F0);

    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 26);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);

    public static final String[] CATEGORIES =
            {"Electronics", "Stationery", "Furniture", "Accessories", "Tools", "Other"};

    private Theme() { }

    public static void antialias(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ------------------------------------------------------------- buttons
    public static JButton button(String text, Color bg, Color fg) { return button(text, bg, fg, null); }

    /** Rounded, flat button with a soft gradient and an optional vector icon. */
    public static JButton button(String text, Color bg, Color fg, String icon) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                antialias(g2);
                Color base = getModel().isPressed() ? bg.darker()
                        : getModel().isRollover() ? mix(bg, Color.WHITE, 0.12) : bg;
                g2.setPaint(new GradientPaint(0, 0, mix(base, Color.WHITE, 0.18), 0, getHeight(), base));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setForeground(fg);
        b.setFont(BOLD);
        b.setIconTextGap(8);
        if (icon != null) b.setIcon(Icons.icon(icon, 18, fg));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        return b;
    }

    // -------------------------------------------------------------- inputs
    /** Rounded outline used by text fields and search boxes. */
    public static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int pad;
        public RoundBorder(Color color, int pad) { this.color = color; this.pad = pad; }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) { return new Insets(pad, pad + 4, pad, pad + 4); }
        @Override
        public Insets getBorderInsets(Component c, Insets i) {
            i.set(pad, pad + 4, pad, pad + 4);
            return i;
        }
    }

    /** Text field with a rounded white background. */
    public static class RoundField extends JTextField {
        public RoundField() { setOpaque(false); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(isEditable() ? Color.WHITE : new Color(0xF0F4F9));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static JTextField field() {
        JTextField f = new RoundField();
        f.setFont(BODY);
        f.setForeground(TEXT);
        f.setCaretColor(NAVY);
        f.setBorder(new RoundBorder(BORDER, 7));
        f.setPreferredSize(new Dimension(240, 40));
        return f;
    }

    public static javax.swing.JPasswordField passwordField() {
        javax.swing.JPasswordField f = new javax.swing.JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                antialias(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        f.setOpaque(false);
        f.setFont(BODY);
        f.setForeground(TEXT);
        f.setCaretColor(NAVY);
        f.setBorder(new RoundBorder(BORDER, 7));
        f.setPreferredSize(new Dimension(260, 40));
        return f;
    }

    // -------------------------------------------------------------- labels
    public static JLabel pageTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TITLE);
        l.setForeground(NAVY);
        return l;
    }

    public static JLabel pageTitle(String text, String icon) {
        JLabel l = pageTitle(text);
        l.setIcon(Icons.icon(icon, 30, NAVY));
        l.setIconTextGap(12);
        return l;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(BODY);
        l.setForeground(TEXT);
        return l;
    }

    /** White rounded card with an optional bold title on top. */
    public static JPanel card(String title) {
        RoundPanel p = new RoundPanel(new BorderLayout(0, 10), WHITE, 18);
        p.setBorderColor(BORDER);
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 18, 18));
        if (title != null) {
            JLabel l = new JLabel(title);
            l.setFont(new Font("Segoe UI", Font.BOLD, 16));
            l.setForeground(NAVY);
            p.add(l, BorderLayout.NORTH);
        }
        return p;
    }

    // --------------------------------------------------------------- table
    public static void styleTable(JTable t) {
        t.setRowHeight(40);
        t.setFont(BODY);
        t.setForeground(TEXT);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xE6EEF7));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(new Color(0xD7F1F0));
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(tb, v, s, false, r, c);
                if (!s) setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xF6FAFF));
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return this;
            }
        });
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 40));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s,
                                                           boolean f, int r, int c) {
                super.getTableCellRendererComponent(tb, v, s, f, r, c);
                setBackground(NAVY);
                setForeground(WHITE);
                setFont(BOLD);
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return this;
            }
        });
    }

    public static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public static JPanel transparent(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }
}
