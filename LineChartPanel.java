import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Simple line chart with area fill, grid and month labels. */
public class LineChartPanel extends JPanel {
    private String[] labels = new String[0];
    private double[] values = new double[0];
    private double grow = 1;            // 0..1
    private javax.swing.Timer anim;

    public LineChartPanel() { setBackground(Theme.WHITE); }

    public void setData(String[] labels, double[] values) {
        this.labels = labels;
        this.values = values;
        if (anim != null) anim.stop();
        grow = 0;
        anim = Anim.play(600, 0, p -> {          // the line is drawn from left to right
            grow = p;
            repaint();
        });
    }

    static double niceStep(double max) {
        double raw = Math.max(1, max) / 3.0;
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / pow;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return nice * pow;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        if (values.length == 0) { g2.dispose(); return; }

        double max = 0;
        for (double v : values) max = Math.max(max, v);
        double step = niceStep(max);
        double top = Math.max(step, Math.ceil(max / step) * step);

        g2.setFont(Theme.SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int left = fm.stringWidth(String.format("%,.0f", top)) + 14;
        int bottom = getHeight() - 24, topY = 10, right = getWidth() - 14;
        int plotW = right - left, plotH = bottom - topY;

        for (double t = 0; t <= top + 0.001; t += step) {
            int y = bottom - (int) (t / top * plotH);
            g2.setColor(new Color(0xE3ECF6));
            g2.drawLine(left, y, right, y);
            g2.setColor(Theme.MUTED);
            String s = String.format("%,.0f", t);
            g2.drawString(s, left - 8 - fm.stringWidth(s), y + 4);
        }

        int n = values.length;
        double[] px = new double[n], py = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = left + (n == 1 ? plotW / 2.0 : 14 + i * (plotW - 28.0) / (n - 1));
            py[i] = bottom - values[i] / top * plotH;
        }
        // points rise from the axis and the area fades in while "grow" goes from 0 to 1
        double rise = Anim.easeOut(grow);
        for (int i = 0; i < n; i++) py[i] = bottom - (bottom - py[i]) * rise;
        Path2D.Double line = new Path2D.Double();
        Path2D.Double area = new Path2D.Double();
        area.moveTo(px[0], bottom);
        for (int i = 0; i < n; i++) {
            if (i == 0) line.moveTo(px[i], py[i]); else line.lineTo(px[i], py[i]);
            area.lineTo(px[i], py[i]);
        }
        area.lineTo(px[n - 1], bottom);
        area.closePath();
        java.awt.Shape oldClip = g2.getClip();                    // reveal the line from left to right
        g2.clipRect(0, 0, (int) (left + (right - left + 14) * rise), getHeight());
        g2.setPaint(new GradientPaint(0, topY, Theme.alpha(Theme.TEAL, (int) (70 * rise)), 0, bottom, Theme.alpha(Theme.TEAL, (int) (8 * rise))));
        g2.fill(area);
        g2.setColor(Theme.TEAL);
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        g2.setClip(oldClip);
        g2.setStroke(new BasicStroke(2f));
        for (int i = 0; i < n; i++) {
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(px[i] - 4, py[i] - 4, 8, 8));
            g2.setColor(Theme.TEAL);
            g2.draw(new Ellipse2D.Double(px[i] - 4, py[i] - 4, 8, 8));
            g2.setColor(Theme.MUTED);
            g2.drawString(labels[i], (int) px[i] - fm.stringWidth(labels[i]) / 2, getHeight() - 6);
        }
        g2.dispose();
    }
}
