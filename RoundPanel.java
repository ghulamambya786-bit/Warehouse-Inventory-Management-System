import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/** Panel with rounded corners, optional gradient, border and soft shadow. */
public class RoundPanel extends JPanel {
    private Color fill;
    private Color fillTo;
    private Color borderColor;
    private final int arc;
    private boolean shadow = true;

    public RoundPanel(LayoutManager lm, Color fill, int arc) {
        super(lm);
        this.fill = fill;
        this.arc = arc;
        setOpaque(false);
    }

    public void setFill(Color c) { fill = c; repaint(); }
    public void setGradient(Color top, Color bottom) { fill = top; fillTo = bottom; repaint(); }
    public void setBorderColor(Color c) { borderColor = c; }
    public void setShadow(boolean s) { shadow = s; }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        int w = getWidth() - 1, h = getHeight() - 1;
        int hh = shadow ? h - 3 : h;
        if (shadow) {
            g2.setColor(new Color(8, 43, 73, 14));
            g2.fillRoundRect(1, 3, w - 1, hh, arc, arc);
        }
        g2.setPaint(fillTo == null ? fill : new GradientPaint(0, 0, fill, 0, hh, fillTo));
        g2.fillRoundRect(0, 0, w, hh, arc, arc);
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, w, hh, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
