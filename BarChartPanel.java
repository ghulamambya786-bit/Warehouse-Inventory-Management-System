import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Grouped bar chart (one group per month) with a small legend. */
public class BarChartPanel extends JPanel {
    private String[] labels = new String[0];
    private double[][] series = new double[0][];
    private String[] names = new String[0];
    private Color[] colors = new Color[0];
    private double grow = 1;            // 0..1
    private javax.swing.Timer anim;

    public BarChartPanel() { setBackground(Theme.WHITE); }

    public void setData(String[] labels, double[][] series, String[] names, Color[] colors) {
        this.labels = labels;
        this.series = series;
        this.names = names;
        this.colors = colors;
        if (anim != null) anim.stop();
        grow = 0;
        anim = Anim.play(500, 0, p -> {          // bars rise from the axis
            grow = p;
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        if (labels.length == 0 || series.length == 0) { g2.dispose(); return; }

        double max = 0;
        for (double[] s : series) for (double v : s) max = Math.max(max, v);
        double step = LineChartPanel.niceStep(max);
        double top = Math.max(step, Math.ceil(max / step) * step);

        g2.setFont(Theme.SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int left = fm.stringWidth(String.format("%,.0f", top)) + 14;
        int legendH = 22;
        int bottom = getHeight() - 24, topY = 8 + legendH, right = getWidth() - 10;
        int plotW = right - left, plotH = bottom - topY;

        int lx = left;
        for (int s = 0; s < names.length; s++) {
            g2.setColor(colors[s]);
            g2.fillRoundRect(lx, 8, 11, 11, 3, 3);
            g2.setColor(Theme.TEXT);
            g2.drawString(names[s], lx + 16, 18);
            lx += 16 + fm.stringWidth(names[s]) + 18;
        }

        for (double t = 0; t <= top + 0.001; t += step) {
            int y = bottom - (int) (t / top * plotH);
            g2.setColor(new Color(0xE3ECF6));
            g2.drawLine(left, y, right, y);
            g2.setColor(Theme.MUTED);
            String str = String.format("%,.0f", t);
            g2.drawString(str, left - 8 - fm.stringWidth(str), y + 4);
        }

        int groups = labels.length;
        double groupW = plotW / (double) groups;
        double barW = Math.min(22, groupW * 0.7 / series.length);
        for (int i = 0; i < groups; i++) {
            double gx = left + i * groupW + (groupW - barW * series.length) / 2;
            for (int s = 0; s < series.length; s++) {
                int h = (int) (series[s][i] / top * plotH * Anim.easeOut(grow));
                g2.setColor(colors[s]);
                if (h > 0) g2.fillRoundRect((int) (gx + s * barW), bottom - h, (int) barW - 2, h, 5, 5);
            }
            g2.setColor(Theme.MUTED);
            int cx = (int) (left + i * groupW + groupW / 2);
            g2.drawString(labels[i], cx - fm.stringWidth(labels[i]) / 2, getHeight() - 6);
        }
        g2.dispose();
    }
}
