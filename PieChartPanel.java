import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.util.LinkedHashMap;
import java.util.Map;

/** Custom-painted pie / donut chart with a legend (no external libraries). */
public class PieChartPanel extends JPanel {
    private static final Color[] PALETTE = {Theme.TEAL, Theme.GOLD, Theme.NAVY, Theme.RED,
            Theme.ORANGE, Theme.BLUE};

    private final Map<String, Double> data = new LinkedHashMap<>();
    private Color[] colors = PALETTE;
    private final boolean donut;
    private double grow = 1;            // 0..1, the slices sweep open while this goes up
    private javax.swing.Timer anim;

    public PieChartPanel(boolean donut) {
        this.donut = donut;
        setOpaque(true);
        setBackground(Theme.WHITE);
    }

    /** @param customColors optional colors, same order as the data entries */
    public void setData(Map<String, Double> values, Color... customColors) {
        data.clear();
        data.putAll(values);
        colors = customColors.length > 0 ? customColors : PALETTE;
        if (anim != null) anim.stop();
        grow = 0;
        anim = Anim.play(500, 0, p -> {          // the pie draws itself clockwise
            grow = Anim.easeOut(p);
            repaint();
        });
    }

    private Color colorAt(int i) { return colors[i % colors.length]; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);

        double total = 0;
        for (double v : data.values()) total += v;
        if (total <= 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.BODY);
            g2.drawString("No data to display", 20, 30);
            g2.dispose();
            return;
        }

        int size = Math.max(60, Math.min(getHeight() - 10, getWidth() - 160));
        int x = 4;
        int y = (getHeight() - size) / 2;
        double start = 90;
        int i = 0;
        for (Map.Entry<String, Double> e : data.entrySet()) {
            double extent = -e.getValue() / total * 360.0 * grow;
            g2.setColor(colorAt(i));
            g2.fill(new Arc2D.Double(x, y, size, size, start, extent, Arc2D.PIE));
            start += extent;
            i++;
        }
        g2.setColor(Theme.WHITE);                       // thin separators between slices
        start = 90;
        for (double v : data.values()) {
            double rad = Math.toRadians(start);
            int cx = x + size / 2, cy = y + size / 2;
            g2.drawLine(cx, cy, cx + (int) (Math.cos(rad) * size / 2), cy - (int) (Math.sin(rad) * size / 2));
            start += -v / total * 360.0 * grow;
        }
        if (donut) {
            int hole = (int) (size * 0.52);
            g2.setColor(getBackground());
            g2.fillOval(x + (size - hole) / 2, y + (size - hole) / 2, hole, hole);
        }

        java.awt.Composite old = g2.getComposite();      // the legend fades in with the pie
        g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, grow))));
        int lx = x + size + 22;
        int right = getWidth() - 6;
        int rowH = 28;
        int ly = Math.max(10, (getHeight() - data.size() * rowH) / 2);
        i = 0;
        for (Map.Entry<String, Double> e : data.entrySet()) {
            g2.setColor(colorAt(i));
            g2.fillRoundRect(lx, ly + 3, 12, 12, 4, 4);
            g2.setColor(Theme.TEXT);
            g2.setFont(Theme.SMALL);
            g2.drawString(e.getKey(), lx + 20, ly + 14);
            String pct = String.format("%.0f%%", e.getValue() / total * 100);
            g2.setFont(Theme.BOLD.deriveFont(12f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(pct, Math.max(lx + 100, right - fm.stringWidth(pct)), ly + 14);
            ly += rowH;
            i++;
        }
        g2.setComposite(old);
        g2.dispose();
    }
}
