import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;

/** Category Management (Admin): add, rename and delete product categories. */
public class CategoriesPanel extends JPanel implements Refreshable {
    private final ProductManager pm;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Category", "Description", "Products"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public CategoriesPanel(ProductManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel top = Theme.transparent(new BorderLayout());
        top.add(Theme.pageTitle("Category Management", "tag"), BorderLayout.WEST);
        JPanel tools = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton add = Theme.button("Add Category", Theme.GOLD, Theme.NAVY, "plus");
        JButton edit = Theme.button("Edit", Theme.TEAL, Color.WHITE, "edit");
        JButton del = Theme.button("Delete", Theme.RED, Color.WHITE, "trash");
        tools.add(add);
        tools.add(edit);
        tools.add(del);
        top.add(tools, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        RoundPanel card = new RoundPanel(new BorderLayout(), Color.WHITE, 18);
        card.setBorderColor(Theme.BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.add(sp, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        add.addActionListener(e -> form(null));
        edit.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) Theme.info(this, "Select a category first."); else form((String) model.getValueAt(r, 0));
        });
        del.addActionListener(e -> delete());
    }

    @Override
    public void refresh() {
        try {
            model.setRowCount(0);
            for (Object[] r : pm.getCategoryRows()) model.addRow(r);
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void form(String existing) {
        JTextField nameF = Theme.field();
        JTextField descF = Theme.field();
        if (existing != null) {
            nameF.setText(existing);
            int r = table.getSelectedRow();
            descF.setText(String.valueOf(model.getValueAt(r, 1)));
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(Theme.label("Category name"));
        form.add(nameF);
        form.add(Theme.label("Description"));
        form.add(descF);
        while (true) {
            int ans = JOptionPane.showConfirmDialog(this, form, existing == null ? "Add Category" : "Edit Category",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ans != JOptionPane.OK_OPTION) return;
            try {
                if (existing == null) pm.addCategory(nameF.getText(), descF.getText());
                else pm.updateCategory(existing, nameF.getText(), descF.getText());
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

    private void delete() {
        int r = table.getSelectedRow();
        if (r < 0) {
            Theme.info(this, "Select a category first.");
            return;
        }
        String name = (String) model.getValueAt(r, 0);
        if (JOptionPane.showConfirmDialog(this, "Delete category \"" + name + "\"?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            pm.deleteCategory(name);
            refresh();
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }
}
