import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.sql.SQLException;

/** Entry point: prepares the database, then opens the login window. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            javax.swing.UIManager.put("ComboBox.background", java.awt.Color.WHITE);
            javax.swing.UIManager.put("ComboBox.selectionBackground", Theme.TEAL);
            javax.swing.UIManager.put("ComboBox.selectionForeground", java.awt.Color.WHITE);
            javax.swing.UIManager.put("OptionPane.background", Theme.LIGHT);
            javax.swing.UIManager.put("Panel.background", Theme.LIGHT);
            try {
                DBConnection.initialize();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null,
                        "Could not connect to MySQL.\n\n" + e.getMessage()
                                + "\n\nCheck that MySQL is running and that the username/password in "
                                + "DBConnection.java are correct.",
                        "Database Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            new LoginFrame().setVisible(true);
        });
    }
}
