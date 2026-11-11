import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Main application window: top bar + navy sidebar + CardLayout content area. */
public class Dashboard extends JFrame {
    private static final String[] ALL_NAMES = {"Dashboard", "Inventory", "Add Product", "Categories",
            "Stack Visualization", "Batches", "Reports", "Stock History", "Transactions", "Users", "Settings"};
    private static final String[] ALL_ICONS = {"home", "box", "add", "tag", "layers", "box", "report",
            "clock", "list", "users", "gear"};
    /** Pages that only an Admin may open. */
    private static final java.util.Set<String> ADMIN_ONLY = new java.util.HashSet<>(
            java.util.Arrays.asList("Categories", "Users"));

    private final String[] NAMES;
    private final String[] ICONS;

    private final ProductManager pm;
    private final String user;
    private final CardLayout cards = new CardLayout();
    private final TransitionPanel content = new TransitionPanel(cards);
    private String currentPage = "";
    private final Map<String, JPanel> pages = new LinkedHashMap<>();
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final Map<String, String> iconOf = new LinkedHashMap<>();

    public Dashboard(ProductManager pm, String user) {
        super("Warehouse Inventory Management System");
        this.pm = pm;
        this.user = user;
        java.util.List<String> names = new java.util.ArrayList<>(), icons = new java.util.ArrayList<>();
        for (int i = 0; i < ALL_NAMES.length; i++) {
            if (ADMIN_ONLY.contains(ALL_NAMES[i]) && !pm.isAdmin()) continue;   // staff do not see these pages
            names.add(ALL_NAMES[i]);
            icons.add(ALL_ICONS[i]);
        }
        NAMES = names.toArray(new String[0]);
        ICONS = icons.toArray(new String[0]);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1180, 740));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        BufferedImage logo = Images.get("logo");
        if (logo != null) setIconImage(logo);

        content.setBackground(Theme.BG);
        InventoryPanel inventory = new InventoryPanel(pm, () -> navigate("Add Product"));
        pages.put("Dashboard", new DashboardPage(pm, this::navigate, () -> {
            inventory.setStatusFilter("Low Stock");
            navigate("Inventory");
        }));
        pages.put("Inventory", inventory);
        pages.put("Add Product", new AddProductPanel(pm, () -> navigate("Inventory")));
        if (pm.isAdmin()) pages.put("Categories", new CategoriesPanel(pm));
        pages.put("Stack Visualization", new StackPanel(pm, this::navigate));
        pages.put("Batches", new BatchesPanel(pm));
        pages.put("Reports", new ReportsPanel(pm));
        pages.put("Stock History", new StockHistoryPanel(pm));
        pages.put("Transactions", new TransactionsPanel(pm));
        if (pm.isAdmin()) pages.put("Users", new UsersPanel(pm));
        pages.put("Settings", new SettingsPanel(pm, user));
        for (Map.Entry<String, JPanel> e : pages.entrySet()) content.add(e.getValue(), e.getKey());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildSidebar(inventory), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        navigate("Dashboard");
    }

    // --------------------------------------------------------------- header
    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x06223A), getWidth(), 0, Theme.NAVY));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.alpha(Theme.GOLD, 160));
                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(100, 54));
        JPanel left = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 9));
        BufferedImage logo = Images.get("logo");
        if (logo != null) left.add(new JLabel(new ImageIcon(logo.getScaledInstance(40, 34, Image.SCALE_SMOOTH))));
        JLabel title = new JLabel("Warehouse Inventory Management System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);
        left.add(title);
        left.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        bar.add(left, BorderLayout.WEST);

        JPanel right = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 14));
        String shown = user.isEmpty() ? user : Character.toUpperCase(user.charAt(0)) + user.substring(1);
        JLabel chip = new JLabel(shown.equalsIgnoreCase("admin") && pm.isAdmin() ? shown : shown + "  (" + (pm.isAdmin() ? "Admin" : "Staff") + ")");
        chip.setIcon(Icons.icon("user", 20, Color.WHITE));
        chip.setIconTextGap(8);
        chip.setFont(Theme.BOLD);
        chip.setForeground(Color.WHITE);
        right.add(chip);
        right.add(new JLabel(Icons.icon("chevron", 14, Color.WHITE)));
        right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // -------------------------------------------------------------- sidebar
    private JPanel buildSidebar(InventoryPanel inventory) {
        JPanel side = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Theme.NAVY, 0, getHeight(), new Color(0x05213A)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(212, 0));
        side.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));

        for (int i = 0; i < NAMES.length; i++) {
            String name = NAMES[i];
            iconOf.put(name, ICONS[i]);
            JButton b = navButton(name, ICONS[i]);
            b.addActionListener(e -> {
                if (name.equals("Inventory")) inventory.setStatusFilter("All Status");
                navigate(name);
            });
            navButtons.put(name, b);
            side.add(b);
            side.add(Box.createVerticalStrut(2));
        }
        side.add(Box.createVerticalGlue());

        JButton logout = navButton("Logout", "logout");
        logout.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
        side.add(logout);
        return side;
    }

    private JButton navButton(String name, String icon) {
        NavButton b = new NavButton(name, icon);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setIcon(Icons.icon(icon, 20, Color.WHITE));
        b.setIconTextGap(14);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(8, 30, 8, 10));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void navigate(String name) {
        for (Map.Entry<String, JButton> e : navButtons.entrySet()) {
            ((NavButton) e.getValue()).setSelectedAnimated(e.getKey().equals(name));
        }
        if (name.equals(currentPage)) {                              // same page again: just reload it
            ((Refreshable) pages.get(name)).refresh();
            return;
        }
        currentPage = name;
        content.transition(() -> {                                   // old page fades out, new page appears
            cards.show(content, name);
            ((Refreshable) pages.get(name)).refresh();
        });
    }

    /** Sidebar button: the hover glow fades in/out and the gold "selected" pill grows from the left. */
    private static class NavButton extends JButton {
        private final String iconName;
        private double hover, selected;          // both 0..1
        private boolean isSelected;
        private javax.swing.Timer hoverTimer, selectTimer;

        NavButton(String text, String iconName) {
            super(text);
            this.iconName = iconName;
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { animateHover(1); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { animateHover(0); }
            });
        }

        private void animateHover(double target) {      // plain hover highlight (no fade)
            hover = target;
            repaint();
        }

        void setSelectedAnimated(boolean sel) {
            if (sel == isSelected) return;
            isSelected = sel;
            if (selectTimer != null) selectTimer.stop();
            final double from = selected, target = sel ? 1 : 0;
            selectTimer = Anim.play(160, 0, p -> {
                selected = from + (target - from) * Anim.easeOut(p);
                boolean dark = selected > 0.45;                       // text turns navy once the pill is under it
                setForeground(dark ? Theme.NAVY : Color.WHITE);
                setIcon(Icons.icon(iconName, 20, dark ? Theme.NAVY : Color.WHITE));
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            int w = getWidth() - 24, h = getHeight() - 4;
            if (hover > 0.01 && selected < 0.99) {
                g2.setColor(new Color(255, 255, 255, (int) (22 * hover * (1 - selected))));
                g2.fillRoundRect(12, 2, w, h, 16, 16);
            }
            if (selected > 0.01) {
                g2.setPaint(new GradientPaint(0, 0, new Color(0xFFD23F), 0, getHeight(), Theme.GOLD));
                g2.fillRoundRect(12, 2, Math.max(16, (int) (w * selected)), h, 16, 16);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Content area: when the page changes, the old page fades out quickly over the new page. */
    private static class TransitionPanel extends JPanel {
        private BufferedImage oldPage;
        private double t = 1;
        private boolean snapshotting;
        private javax.swing.Timer timer;

        TransitionPanel(java.awt.LayoutManager layout) { super(layout); }

        void transition(Runnable switchPage) {
            BufferedImage snap = null;
            if (isShowing() && getWidth() > 0 && getHeight() > 0) {
                snap = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = snap.createGraphics();
                snapshotting = true;
                paint(g);
                snapshotting = false;
                g.dispose();
            }
            switchPage.run();
            if (snap == null) return;
            if (timer != null) timer.stop();
            oldPage = snap;
            t = 0;
            timer = Anim.play(160, 0, p -> {
                t = Anim.easeOut(p);
                if (p >= 1) oldPage = null;
                repaint();
            });
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (oldPage != null && !snapshotting) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) (1 - t)));
                g2.drawImage(oldPage, 0, 0, null);
                g2.dispose();
            }
        }
    }

    // ===================================================================
    /** Page 1: summary cards, stock overview, monthly trend, recent activity and low-stock alert. */
    static class DashboardPage extends JPanel implements Refreshable {
        private final ProductManager pm;
        private final Consumer<String> nav;
        private final JLabel[] values = new JLabel[5];
        private final PieChartPanel pie = new PieChartPanel(true);
        private final LineChartPanel trend = new LineChartPanel();
        private final JPanel activityList = new JPanel();
        private final RoundPanel alert = new RoundPanel(new BorderLayout(14, 0), Theme.GOLD, 16);
        private final JLabel alertText = new JLabel();
        private final JLabel alertIcon = new JLabel();
        private final double[] from = new double[5], to = new double[5], shown = new double[5];
        private javax.swing.Timer countTimer;

        DashboardPage(ProductManager pm, Consumer<String> nav, Runnable showLowStock) {
            this.pm = pm;
            this.nav = nav;
            setLayout(new BorderLayout(0, 16));
            setBackground(Theme.BG);
            setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));
            add(Theme.pageTitle("Dashboard", "home"), BorderLayout.NORTH);

            JPanel body = Theme.transparent(new BorderLayout(0, 16));

            JPanel stats = new JPanel(new GridLayout(1, 5, 14, 0));
            stats.setOpaque(false);
            stats.setPreferredSize(new Dimension(100, 98));
            stats.add(statCard(0, "Total Products", "box", Theme.NAVY_LIGHT, Theme.NAVY, Color.WHITE));
            stats.add(statCard(1, "Total Batches", "layers", new Color(0x4AA3F0), Theme.BLUE, Color.WHITE));
            stats.add(statCard(2, "Total Stock Units", "cubes", new Color(0x12C2C0), Theme.TEAL, Color.WHITE));
            stats.add(statCard(3, "Inventory Value", "money", new Color(0xFFD23F), Theme.GOLD, Theme.NAVY));
            stats.add(statCard(4, "Low Stock", "warning", new Color(0xF2586A), Theme.RED, Color.WHITE));
            body.add(stats, BorderLayout.NORTH);

            JPanel middle = new JPanel(new GridBagLayout());
            middle.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.BOTH;
            gc.weighty = 1;
            gc.gridy = 0;
            JPanel overview = Theme.card("Stock Overview");
            overview.add(pie, BorderLayout.CENTER);
            JPanel trendCard = Theme.card("Monthly Stock Trend");
            trendCard.add(trend, BorderLayout.CENTER);
            JPanel activity = Theme.card("Recent Activities");
            activityList.setLayout(new BoxLayout(activityList, BoxLayout.Y_AXIS));
            activityList.setOpaque(false);
            activity.add(activityList, BorderLayout.CENTER);
            JButton viewAll = Theme.button("View All  \u2192", Theme.NAVY, Color.WHITE);
            viewAll.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
            JPanel viewAllWrap = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            viewAllWrap.add(viewAll);
            activity.add(viewAllWrap, BorderLayout.SOUTH);
            gc.gridx = 0; gc.weightx = 0.30; gc.insets = new Insets(0, 0, 0, 16);
            middle.add(overview, gc);
            gc.gridx = 1; gc.weightx = 0.38;
            middle.add(trendCard, gc);
            gc.gridx = 2; gc.weightx = 0.32; gc.insets = new Insets(0, 0, 0, 0);
            middle.add(activity, gc);
            body.add(middle, BorderLayout.CENTER);

            alert.setBorder(BorderFactory.createEmptyBorder(8, 18, 12, 14));
            alert.setPreferredSize(new Dimension(100, 62));
            alertText.setFont(new Font("Segoe UI", Font.BOLD, 15));
            alertText.setForeground(Theme.NAVY);
            JButton details = Theme.button("View Details", Theme.NAVY, Color.WHITE);
            JPanel left = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 8));
            left.add(alertIcon);
            left.add(alertText);
            alert.add(left, BorderLayout.CENTER);
            JPanel dWrap = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 2));
            dWrap.add(details);
            alert.add(dWrap, BorderLayout.EAST);
            body.add(alert, BorderLayout.SOUTH);
            add(body, BorderLayout.CENTER);

            viewAll.addActionListener(e -> nav.accept("Stock History"));
            details.addActionListener(e -> showLowStock.run());
        }

        /** The five numbers count up (or down) from what is shown now to the new value. */
        private void animateNumbers(double products, double batches, double units, double value, double low) {
            double[] target = {products, batches, units, value, low};
            for (int i = 0; i < 5; i++) {
                from[i] = shown[i];
                to[i] = target[i];
            }
            if (countTimer != null) countTimer.stop();
            countTimer = Anim.play(400, 0, p -> {
                double e = Anim.easeOut(p);
                for (int i = 0; i < 5; i++) {
                    shown[i] = from[i] + (to[i] - from[i]) * e;
                    long v = Math.round(shown[i]);
                    values[i].setText(i == 2 ? String.format("%,d", v) : i == 3 ? String.format("₨%,d", v) : String.valueOf(v));
                }
            });
        }

        private JPanel statCard(int index, String title, String icon, Color top, Color bottom, Color fg) {
            RoundPanel card = new RoundPanel(new BorderLayout(10, 0), top, 18);
            card.setGradient(top, bottom);
            card.setBorder(BorderFactory.createEmptyBorder(12, 12, 16, 8));
            JLabel ic = new JLabel(Icons.icon(icon, 24, fg));
            ic.setPreferredSize(new Dimension(42, 42));
            ic.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel circle = new RoundPanel(new BorderLayout(), Theme.alpha(fg, fg == Color.WHITE ? 38 : 40), 40) {
                { setShadow(false); }
            };
            circle.add(ic, BorderLayout.CENTER);
            circle.setPreferredSize(new Dimension(42, 42));
            JPanel circleWrap = Theme.transparent(new java.awt.GridBagLayout());
            circleWrap.add(circle);

            JLabel t = new JLabel(title);
            t.setFont(new Font("Segoe UI", Font.BOLD, 13));
            t.setForeground(fg);
            values[index] = new JLabel("-");
            values[index].setFont(new Font("Segoe UI", Font.BOLD, 23));
            values[index].setForeground(fg);
            JPanel text = Theme.transparent(new GridLayout(2, 1, 0, 0));
            text.add(t);
            text.add(values[index]);
            card.add(circleWrap, BorderLayout.WEST);
            card.add(text, BorderLayout.CENTER);
            return card;
        }

        @Override
        public void refresh() {
            try {
                List<Product> list = pm.getAll();
                int units = 0, low = 0, out = 0, in = 0;
                double value = 0;
                for (Product p : list) {
                    units += p.getQuantity();
                    value += p.getValue();
                    if (p.isLowStock()) low++;
                    if (p.getQuantity() == 0) out++;
                    else if (!p.isLowStock()) in++;
                }
                int lowOnly = low - out;
                animateNumbers(list.size(), pm.getStack().size(), units, value, low);

                Map<String, Double> status = new LinkedHashMap<>();
                status.put("In Stock", (double) in);
                status.put("Low Stock", (double) lowOnly);
                status.put("Out of Stock", (double) out);
                pie.setData(status, Theme.TEAL, Theme.GOLD, Theme.RED);
                trend.setData(ProductManager.monthLabels(6), pm.monthlyStockTrend(6));

                if (low > 0) {
                    alert.setGradient(new Color(0xFFD23F), Theme.GOLD);
                    alertIcon.setIcon(Icons.icon("bell", 24, Theme.NAVY));
                    alertText.setText("Low Stock Alert: " + low + (low == 1 ? " product is" : " products are")
                            + " running low!");
                } else {
                    alert.setGradient(new Color(0x12C2C0), Theme.TEAL);
                    alertIcon.setIcon(Icons.icon("check", 24, Color.WHITE));
                    alertText.setText("All products are well stocked.");
                }
                alertText.setForeground(low > 0 ? Theme.NAVY : Color.WHITE);

                activityList.removeAll();
                for (Object[] row : pm.getHistory(0, 5)) activityList.add(activityRow(row));
                activityList.revalidate();
                activityList.repaint();
            } catch (ProductManager.DbException ex) {
                Theme.error(this, ex.getMessage());
            }
        }

        private static Anim.FadePanel activityRow(Object[] row) {
            String name = Theme.esc(String.valueOf(row[1]));
            String action = String.valueOf(row[2]);
            int qty = row[3] instanceof Integer ? (Integer) row[3] : 0;
            String text;
            Color dot;
            switch (action) {
                case "Added":     text = name + " added to inventory"; dot = Theme.TEAL; break;
                case "Updated":   text = name + " stock updated"; dot = Theme.GOLD; break;
                case "Removed":   text = name + " removed"; dot = Theme.ORANGE; break;
                case "Stock In":  text = name + " stock in (+" + qty + ")"; dot = Theme.GREEN; break;
                case "Stock Out": text = name + " stock out (-" + qty + ")"; dot = Theme.RED; break;
                case "PUSH":      text = name + " batch pushed (+" + qty + ")"; dot = Theme.GREEN; break;
                case "POP":       text = name + " batch popped (-" + qty + ")"; dot = Theme.PURPLE; break;
                default:          text = name + " - " + Theme.esc(action); dot = Theme.MUTED;
            }
            JLabel l = new JLabel("<html><b>" + text + "</b><br><span style='color:#6B7A90;font-size:10px'>"
                    + ago(row.length > 6 ? (Timestamp) row[6] : null) + "</span></html>");
            l.setFont(Theme.BODY);
            l.setForeground(Theme.TEXT);
            JPanel dotPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Theme.antialias(g2);
                    g2.setColor(dot);
                    g2.fillOval(2, 8, 11, 11);
                    g2.dispose();
                }
            };
            dotPanel.setOpaque(false);
            dotPanel.setPreferredSize(new Dimension(18, 30));
            Anim.FadePanel p = new Anim.FadePanel(new BorderLayout(8, 0));
            p.setOpaque(false);
            p.add(dotPanel, BorderLayout.WEST);
            p.add(l, BorderLayout.CENTER);
            p.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            return p;
        }

        private static String ago(Timestamp t) {
            if (t == null) return "";
            long mins = (System.currentTimeMillis() - t.getTime()) / 60000;
            if (mins < 1) return "just now";
            if (mins < 60) return mins + (mins == 1 ? " min ago" : " mins ago");
            long hours = mins / 60;
            if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
            return new SimpleDateFormat("dd-MM-yyyy HH:mm").format(new Date(t.getTime()));
        }
    }
}
