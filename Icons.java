import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Small outline icons drawn with Java2D (no image files). Grid is 24 x 24. */
public final class Icons {
    private Icons() { }

    public static ImageIcon icon(String name, int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Theme.antialias(g);
        paint(g, name, 0, 0, size, color);
        g.dispose();
        return new ImageIcon(img);
    }

    public static void paint(Graphics2D g0, String name, double x, double y, double size, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "home":
                poly(g, false, 3, 11.5, 12, 3.5, 21, 11.5);
                poly(g, false, 5.5, 10, 5.5, 20.5, 18.5, 20.5, 18.5, 10);
                poly(g, false, 10, 20.5, 10, 14.5, 14, 14.5, 14, 20.5);
                break;
            case "box": case "cubes": cube(g); break;
            case "add":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Line2D.Double(12, 7.5, 12, 16.5));
                g.draw(new Line2D.Double(7.5, 12, 16.5, 12));
                break;
            case "in":
                poly(g, false, 4, 13, 4, 21, 20, 21, 20, 13);
                g.draw(new Line2D.Double(12, 2.5, 12, 14));
                poly(g, false, 8, 10, 12, 14, 16, 10);
                break;
            case "out":
                poly(g, false, 4, 13, 4, 21, 20, 21, 20, 13);
                g.draw(new Line2D.Double(12, 14, 12, 2.5));
                poly(g, false, 8, 6.5, 12, 2.5, 16, 6.5);
                break;
            case "layers":
                poly(g, true, 12, 3, 21, 8, 12, 13, 3, 8);
                poly(g, false, 3, 12, 12, 17, 21, 12);
                poly(g, false, 3, 16, 12, 21, 21, 16);
                break;
            case "report":
                g.draw(new RoundRectangle2D.Double(5, 3, 14, 18, 3, 3));
                g.draw(new Line2D.Double(9, 9, 15, 9));
                g.draw(new Line2D.Double(9, 13, 15, 13));
                g.draw(new Line2D.Double(9, 17, 13, 17));
                break;
            case "clock":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                poly(g, false, 12, 7, 12, 12, 16, 14);
                break;
            case "gear":
                g.draw(new Ellipse2D.Double(8, 8, 8, 8));
                g.draw(new Ellipse2D.Double(5, 5, 14, 14));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i, c = Math.cos(a), s = Math.sin(a);
                    g.draw(new Line2D.Double(12 + c * 7, 12 + s * 7, 12 + c * 10, 12 + s * 10));
                }
                break;
            case "logout":
                poly(g, false, 10, 4, 4, 4, 4, 20, 10, 20);
                g.draw(new Line2D.Double(9, 12, 20, 12));
                poly(g, false, 16, 8, 20, 12, 16, 16);
                break;
            case "user":
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                g.draw(new Arc2D.Double(4, 13, 16, 16, 0, 180, Arc2D.OPEN));
                break;
            case "lock":
                g.draw(new RoundRectangle2D.Double(5, 11, 14, 10, 3, 3));
                g.draw(new Arc2D.Double(8, 3, 8, 16, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 15, 12, 17));
                break;
            case "eye": case "eyeoff":
                g.draw(new Arc2D.Double(2.5, 5, 19, 14, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(2.5, 5, 19, 14, 180, 180, Arc2D.OPEN));
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                if (name.equals("eyeoff")) g.draw(new Line2D.Double(4, 20, 20, 4));
                break;
            case "search":
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(new Line2D.Double(14, 14, 20, 20));
                break;
            case "edit":
                poly(g, true, 4, 20, 5, 15, 15.5, 4.5, 19.5, 8.5, 9, 19);
                g.draw(new Line2D.Double(13, 7, 17, 11));
                break;
            case "trash":
                g.draw(new Line2D.Double(4, 6, 20, 6));
                poly(g, false, 9, 6, 9, 3, 15, 3, 15, 6);
                poly(g, false, 6, 6, 7, 21, 17, 21, 18, 6);
                g.draw(new Line2D.Double(10, 10, 10, 17));
                g.draw(new Line2D.Double(14, 10, 14, 17));
                break;
            case "warning":
                poly(g, true, 12, 3, 22, 20, 2, 20);
                g.draw(new Line2D.Double(12, 9, 12, 14));
                g.fill(new Ellipse2D.Double(11, 16, 2, 2));
                break;
            case "money":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.setFont(new Font("Segoe UI", Font.BOLD, 15));
                FontMetrics fm = g.getFontMetrics();
                g.drawString("₨", 12 - fm.stringWidth("₨") / 2f, 17.2f);
                break;
            case "bell":
                g.draw(new Arc2D.Double(6, 4, 12, 12, 0, 180, Arc2D.OPEN));
                poly(g, false, 6, 10, 4.5, 17, 19.5, 17, 18, 10);
                g.draw(new Arc2D.Double(9.5, 15, 5, 6, 180, 180, Arc2D.OPEN));
                break;
            case "check": poly(g, false, 5, 12.5, 10, 17.5, 19, 7); break;
            case "calendar":
                g.draw(new RoundRectangle2D.Double(4, 5, 16, 15, 3, 3));
                g.draw(new Line2D.Double(4, 10, 20, 10));
                g.draw(new Line2D.Double(8, 3, 8, 7));
                g.draw(new Line2D.Double(16, 3, 16, 7));
                break;
            case "download":
                g.draw(new Line2D.Double(12, 3, 12, 15));
                poly(g, false, 7, 10.5, 12, 15.5, 17, 10.5);
                g.draw(new Line2D.Double(4, 20, 20, 20));
                break;
            case "plus":
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
                break;
            case "refresh":
                g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 40, 280, Arc2D.OPEN));
                poly(g, false, 19.5, 3.5, 19.5, 8.5, 14.5, 8.5);
                break;
            case "camera":
                g.draw(new RoundRectangle2D.Double(3, 7, 18, 13, 4, 4));
                poly(g, false, 8, 7, 9.5, 4, 14.5, 4, 16, 7);
                g.draw(new Ellipse2D.Double(8.5, 10, 7, 7));
                break;
            case "tag":
                poly(g, true, 3, 12, 12, 3, 21, 3, 21, 12, 12, 21);
                g.fill(new Ellipse2D.Double(15, 6, 2.6, 2.6));
                break;
            case "users":
                g.draw(new Ellipse2D.Double(8, 4, 7, 7));
                g.draw(new Arc2D.Double(3.5, 13, 16, 16, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(15.5, 5.5, 5.5, 5.5, 90, -180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(16, 13.5, 6, 8, 0, 90, Arc2D.OPEN));
                break;
            case "list":
                g.draw(new Line2D.Double(9, 6, 20, 6));
                g.draw(new Line2D.Double(9, 12, 20, 12));
                g.draw(new Line2D.Double(9, 18, 20, 18));
                g.fill(new Ellipse2D.Double(3.5, 5, 2.4, 2.4));
                g.fill(new Ellipse2D.Double(3.5, 11, 2.4, 2.4));
                g.fill(new Ellipse2D.Double(3.5, 17, 2.4, 2.4));
                break;
            case "sort":
                g.draw(new Line2D.Double(8, 4, 8, 20));
                poly(g, false, 4, 8, 8, 4, 12, 8);
                g.draw(new Line2D.Double(16, 20, 16, 4));
                poly(g, false, 12, 16, 16, 20, 20, 16);
                break;
            case "chevron": poly(g, false, 7, 10, 12, 15, 17, 10); break;
            default: g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        }
        g.dispose();
    }

    private static void cube(Graphics2D g) {
        poly(g, true, 12, 3, 20, 7.5, 20, 16.5, 12, 21, 4, 16.5, 4, 7.5);
        poly(g, false, 4, 7.5, 12, 12, 20, 7.5);
        g.draw(new Line2D.Double(12, 12, 12, 21));
    }

    private static void poly(Graphics2D g, boolean closed, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        if (closed) path.closePath();
        g.draw(path);
    }
}
