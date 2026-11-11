import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.prefs.Preferences;

/** Admin login screen (credentials are checked against the MySQL users table). */
public class LoginFrame extends JFrame {
    private static final String PREF_USER = "rememberedUser";

    private final ProductManager pm = new ProductManager();
    private final Preferences prefs = Preferences.userNodeForPackage(LoginFrame.class);
    private final DarkInput userInput = new DarkInput("Username", "user", false);
    private final DarkInput passInput = new DarkInput("Password", "lock", true);
    private final JCheckBox remember = new JCheckBox("Remember Me");

    public LoginFrame() {
        super("Warehouse Inventory Management System - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(920, 560);
        setResizable(false);
        setLocationRelativeTo(null);
        BufferedImage logo = Images.get("logo");
        if (logo != null) setIconImage(logo);

        JPanel root = new JPanel(new BorderLayout());
        root.add(new PhotoPanel(), BorderLayout.WEST);
        root.add(buildForm(), BorderLayout.CENTER);
        setContentPane(root);

        String saved = prefs.get(PREF_USER, "");
        if (!saved.isEmpty()) {
            userInput.getField().setText(saved);
            remember.setSelected(true);
        }
    }

    // ------------------------------------------------------------- left photo
    private static class PhotoPanel extends JPanel {
        PhotoPanel() { setPreferredSize(new Dimension(390, 560)); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setColor(new Color(0x011329));
            g2.fillRect(0, 0, getWidth(), getHeight());
            BufferedImage img = Images.get("login_photo");
            if (img != null) {
                double s = Math.max(getWidth() / (double) img.getWidth(), getHeight() / (double) img.getHeight());
                int w = (int) Math.round(img.getWidth() * s), h = (int) Math.round(img.getHeight() * s);
                g2.drawImage(img, getWidth() - w, (getHeight() - h) / 2, w, h, null);   // keep the swoosh edge
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------- right form
    private JPanel buildForm() {
        JPanel right = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x01274C), 0, getHeight(), new Color(0x011329)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 60, 0, 60);
        int row = 0;

        BufferedImage logo = Images.get("logo");
        if (logo != null) {
            JLabel l = new JLabel(new javax.swing.ImageIcon(logo.getScaledInstance(92, 79, java.awt.Image.SCALE_SMOOTH)),
                    JLabel.CENTER);
            gc.gridy = row++;
            gc.insets = new Insets(0, 60, 6, 60);
            right.add(l, gc);
        }
        JLabel t1 = new JLabel("Warehouse", JLabel.CENTER);
        t1.setFont(new Font("Segoe UI", Font.BOLD, 40));
        t1.setForeground(Color.WHITE);
        gc.gridy = row++;
        gc.insets = new Insets(0, 40, 0, 40);
        right.add(t1, gc);
        JLabel t2 = new JLabel("Inventory Management System", JLabel.CENTER);
        t2.setFont(new Font("Segoe UI", Font.BOLD, 21));
        t2.setForeground(Theme.GOLD);
        gc.gridy = row++;
        right.add(t2, gc);
        JLabel t3 = new JLabel("Smart Inventory . Better Management.", JLabel.CENTER);
        t3.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        t3.setForeground(Color.WHITE);
        gc.gridy = row++;
        gc.insets = new Insets(8, 40, 22, 40);
        right.add(t3, gc);

        gc.insets = new Insets(7, 60, 7, 60);
        gc.gridy = row++;
        right.add(userInput, gc);
        gc.gridy = row++;
        right.add(passInput, gc);

        JButton login = Theme.button("Login", Theme.GOLD, Theme.NAVY, "lock");
        login.setFont(new Font("Segoe UI", Font.BOLD, 19));
        login.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        gc.gridy = row++;
        gc.insets = new Insets(14, 60, 10, 60);
        right.add(login, gc);

        remember.setOpaque(false);
        remember.setForeground(Color.WHITE);
        remember.setFont(Theme.SMALL);
        remember.setFocusPainted(false);
        JLabel forgot = new JLabel("<html><u>Forgot Password?</u></html>");
        forgot.setFont(Theme.SMALL);
        forgot.setForeground(Theme.GOLD);
        forgot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgot.setHorizontalAlignment(JLabel.RIGHT);
        JPanel opts = Theme.transparent(new BorderLayout());
        opts.add(remember, BorderLayout.WEST);
        opts.add(forgot, BorderLayout.EAST);
        gc.gridy = row++;
        gc.insets = new Insets(2, 56, 6, 60);
        right.add(opts, gc);

        getRootPane().setDefaultButton(login);
        login.addActionListener(e -> doLogin());
        forgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(LoginFrame.this,
                        "Passwords are reset by the system administrator.\n"
                                + "After you log in you can change your own password under Settings.",
                        "Forgot Password", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        return right;
    }

    private void doLogin() {
        String user = userInput.getField().getText().trim();
        String pass = new String(((JPasswordField) passInput.getField()).getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            Theme.error(this, "Please enter both username and password.");
            return;
        }
        try {
            User account = pm.login(user, pass);
            if (account != null) {
                if (remember.isSelected()) prefs.put(PREF_USER, user); else prefs.remove(PREF_USER);
                pm.setCurrentUser(account.getUsername());
                pm.setCurrentRole(account.getRole());
                new Dashboard(pm, account.getUsername()).setVisible(true);
                dispose();
            } else {
                ((JPasswordField) passInput.getField()).setText("");
                shake(() -> Theme.error(this, "Invalid username or password."));
            }
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    /** Wrong password: the window shakes left and right, then the message is shown. */
    private void shake(Runnable afterwards) {
        final java.awt.Point origin = getLocation();
        Anim.play(320, 0, p -> {
            setLocation(origin.x + (int) Math.round(Math.sin(p * Math.PI * 5) * 10 * (1 - p)), origin.y);
            if (p >= 1) {
                setLocation(origin);
                afterwards.run();
            }
        });
    }

    // ---------------------------------------------------- dark rounded input
    /** Rounded dark input with a leading icon, placeholder and (for passwords) a show/hide eye. */
    private static class DarkInput extends RoundPanel {
        private final JTextField field;

        DarkInput(String placeholder, String icon, boolean password) {
            super(new BorderLayout(10, 0), new Color(0x0F3A5F), 16);
            setShadow(false);
            setBorderColor(new Color(0x1E5A8A));
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 12));
            setPreferredSize(new Dimension(360, 50));
            field = password ? new JPasswordField() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    paintHint(this, g, placeholder);
                }
            } : new JTextField() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    paintHint(this, g, placeholder);
                }
            };
            field.setOpaque(false);
            field.setBorder(BorderFactory.createEmptyBorder());
            field.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            field.setForeground(Color.WHITE);
            field.setCaretColor(Color.WHITE);
            add(new JLabel(Icons.icon(icon, 22, Color.WHITE)), BorderLayout.WEST);
            add(field, BorderLayout.CENTER);
            if (password) {
                JPasswordField pf = (JPasswordField) field;
                char hidden = pf.getEchoChar();
                JLabel eye = new JLabel(Icons.icon("eyeoff", 22, Color.WHITE));
                eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                eye.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        boolean show = pf.getEchoChar() != 0;
                        pf.setEchoChar(show ? (char) 0 : hidden);
                        eye.setIcon(Icons.icon(show ? "eye" : "eyeoff", 22, Color.WHITE));
                    }
                });
                add(eye, BorderLayout.EAST);
            }
        }

        JTextField getField() { return field; }

        private static void paintHint(JTextField f, Graphics g, String text) {
            if (!f.getText().isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.antialias(g2);
            g2.setColor(new Color(0xB9C9DC));
            g2.setFont(f.getFont());
            g2.drawString(text, f.getInsets().left, (f.getHeight() + g2.getFontMetrics().getAscent()
                    - g2.getFontMetrics().getDescent()) / 2);
            g2.dispose();
        }
    }
}
