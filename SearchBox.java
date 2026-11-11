import javax.swing.BorderFactory;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Rounded search box with a magnifier icon and placeholder text. */
public class SearchBox extends RoundPanel {
    private final JTextField field;

    public SearchBox(String placeholder, int width) {
        super(new BorderLayout(6, 0), Color.WHITE, 14);
        setShadow(false);
        setBorderColor(Theme.BORDER);
        setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));
        setPreferredSize(new Dimension(width, 40));
        field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Theme.antialias(g2);
                    g2.setColor(Theme.MUTED);
                    g2.setFont(getFont());
                    g2.drawString(placeholder, getInsets().left, (getHeight() + g2.getFontMetrics().getAscent()
                            - g2.getFontMetrics().getDescent()) / 2);
                    g2.dispose();
                }
            }
        };
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setFont(Theme.BODY);
        field.setForeground(Theme.TEXT);
        javax.swing.JLabel ic = new javax.swing.JLabel(Icons.icon("search", 18, Theme.MUTED));
        add(ic, BorderLayout.WEST);
        add(field, BorderLayout.CENTER);
    }

    public JTextField getField() { return field; }
}
