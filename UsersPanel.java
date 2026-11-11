import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** User Management (Admin): create Admin / Warehouse Staff accounts, change roles, reset passwords, delete. */
public class UsersPanel extends JPanel implements Refreshable {
    private final ProductManager pm;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Username", "Full Name", "Role"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<User> users = new ArrayList<>();

    public UsersPanel(ProductManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel top = Theme.transparent(new BorderLayout());
        top.add(Theme.pageTitle("User Management", "users"), BorderLayout.WEST);
        JPanel tools = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton add = Theme.button("Add User", Theme.GOLD, Theme.NAVY, "plus");
        JButton role = Theme.button("Change Role", Theme.TEAL, Color.WHITE, "edit");
        JButton reset = Theme.button("Reset Password", Theme.BLUE, Color.WHITE, "lock");
        JButton del = Theme.button("Delete", Theme.RED, Color.WHITE, "trash");
        tools.add(add);
        tools.add(role);
        tools.add(reset);
        tools.add(del);
        top.add(tools, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        Theme.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        RoundPanel card = new RoundPanel(new BorderLayout(), Color.WHITE, 18);
        card.setBorderColor(Theme.BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.add(sp, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        add.addActionListener(e -> addUser());
        role.addActionListener(e -> changeRole());
        reset.addActionListener(e -> resetPassword());
        del.addActionListener(e -> deleteUser());
    }

    @Override
    public void refresh() {
        try {
            users = pm.getUsers();
            model.setRowCount(0);
            for (User u : users) model.addRow(new Object[]{u.getUsername(), u.getFullName(), u.roleLabel()});
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private User selected() {
        int r = table.getSelectedRow();
        if (r < 0 || r >= users.size()) {
            Theme.info(this, "Select a user first.");
            return null;
        }
        return users.get(r);
    }

    private static JComboBox<String> roleBox() {
        JComboBox<String> b = new JComboBox<>(new String[]{"Warehouse Staff", "Admin"});
        b.setFont(Theme.BODY);
        return b;
    }

    private void addUser() {
        JTextField nameF = Theme.field();
        JTextField fullF = Theme.field();
        JPasswordField passF = Theme.passwordField();
        JComboBox<String> roleF = roleBox();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(Theme.label("Username"));
        form.add(nameF);
        form.add(Theme.label("Full name"));
        form.add(fullF);
        form.add(Theme.label("Password (6+)"));
        form.add(passF);
        form.add(Theme.label("Role"));
        form.add(roleF);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, "Add User", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            try {
                pm.addUser(nameF.getText(), new String(passF.getPassword()), fullF.getText(),
                        roleF.getSelectedIndex() == 1 ? User.ADMIN : User.STAFF);
                refresh();
                return;
            } catch (IllegalArgumentException ex) {
                Theme.error(this, ex.getMessage());
            } catch (ProductManager.DbException ex) {
                Theme.error(this, ex.getMessage());
                return;
            }
        }
    }

    private void changeRole() {
        User u = selected();
        if (u == null) return;
        JComboBox<String> roleF = roleBox();
        roleF.setSelectedIndex(u.isAdmin() ? 1 : 0);
        if (JOptionPane.showConfirmDialog(this, roleF, "Role of " + u.getUsername(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            pm.setUserRole(u.getUsername(), roleF.getSelectedIndex() == 1 ? User.ADMIN : User.STAFF);
            refresh();
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void resetPassword() {
        User u = selected();
        if (u == null) return;
        JPasswordField passF = Theme.passwordField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(Theme.label("New password (6+)"));
        form.add(passF);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, "Reset password of " + u.getUsername(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            try {
                pm.resetPassword(u.getUsername(), new String(passF.getPassword()));
                Theme.info(this, "Password of " + u.getUsername() + " was changed.");
                return;
            } catch (IllegalArgumentException ex) {
                Theme.error(this, ex.getMessage());
            } catch (ProductManager.DbException ex) {
                Theme.error(this, ex.getMessage());
                return;
            }
        }
    }

    private void deleteUser() {
        User u = selected();
        if (u == null) return;
        if (JOptionPane.showConfirmDialog(this, "Delete user \"" + u.getUsername() + "\"?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            pm.deleteUser(u.getUsername());
            refresh();
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }
}
