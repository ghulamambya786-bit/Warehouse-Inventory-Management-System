import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Page 9: account information and change password. */
public class SettingsPanel extends JPanel implements Refreshable {
    private final ProductManager pm;
    private final String user;
    private final JPasswordField oldPass = Theme.passwordField();
    private final JPasswordField newPass = Theme.passwordField();
    private final JPasswordField confirm = Theme.passwordField();

    public SettingsPanel(ProductManager pm, String user) {
        this.pm = pm;
        this.user = user;
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));
        add(Theme.pageTitle("Settings", "gear"), BorderLayout.NORTH);

        JPanel form = Theme.transparent(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        row(form, gc, 0, "Logged in as:", boldLabel(user));
        row(form, gc, 1, "Current password:", oldPass);
        row(form, gc, 2, "New password:", newPass);
        row(form, gc, 3, "Confirm password:", confirm);
        JButton save = Theme.button("Change Password", Theme.GOLD, Theme.NAVY, "lock");
        gc.gridx = 1;
        gc.gridy = 4;
        gc.insets = new Insets(14, 6, 4, 6);
        form.add(save, gc);

        JPanel card = Theme.card("Account & Password");
        card.add(form, BorderLayout.NORTH);
        JPanel holder = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        card.setPreferredSize(new Dimension(520, 330));
        holder.add(card);
        add(holder, BorderLayout.CENTER);
        save.addActionListener(e -> change());
    }

    private static JLabel boldLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(Theme.BOLD);
        l.setForeground(Theme.NAVY);
        return l;
    }

    private void row(JPanel form, GridBagConstraints gc, int r, String label, java.awt.Component field) {
        gc.gridx = 0;
        gc.gridy = r;
        gc.insets = new Insets(8, 6, 8, 14);
        JLabel l = Theme.label(label);
        l.setPreferredSize(new Dimension(140, 24));
        form.add(l, gc);
        gc.gridx = 1;
        gc.insets = new Insets(8, 6, 8, 6);
        form.add(field, gc);
    }

    @Override
    public void refresh() {
        oldPass.setText("");
        newPass.setText("");
        confirm.setText("");
    }

    private void change() {
        String o = new String(oldPass.getPassword());
        String n = new String(newPass.getPassword());
        String c = new String(confirm.getPassword());
        if (o.isEmpty() || n.isEmpty() || c.isEmpty()) {
            Theme.error(this, "Please fill in all three password fields.");
            return;
        }
        if (n.length() < 6) {
            Theme.error(this, "The new password must be at least 6 characters long.");
            return;
        }
        if (!n.equals(c)) {
            Theme.error(this, "The new password and the confirmation do not match.");
            return;
        }
        try {
            if (pm.changePassword(user, o, n)) {
                Theme.info(this, "Password changed successfully.");
                refresh();
            } else {
                Theme.error(this, "The current password is not correct.");
            }
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }
}
