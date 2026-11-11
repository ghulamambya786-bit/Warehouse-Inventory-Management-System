import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Small helper that saves a table as a CSV file (opens in Excel). */
public final class CsvUtil {
    private CsvUtil() { }

    public static void export(Component parent, String defaultName, String[] headers, List<Object[]> rows) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) f = new File(f.getParentFile(), f.getName() + ".csv");
        if (f.exists() && JOptionPane.showConfirmDialog(parent, "File already exists. Overwrite?",
                "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            out.print('\uFEFF');
            out.println(join(headers));
            for (Object[] r : rows) out.println(join(r));
            if (out.checkError()) throw new IOException("write error");
        } catch (IOException ex) {
            Theme.error(parent, "Could not write the file: " + ex.getMessage());
            return;
        }
        Theme.info(parent, "Saved " + rows.size() + " row(s) to:\n" + f.getAbsolutePath());
    }

    private static String join(Object[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i] == null ? "" : String.valueOf(cells[i])));
        }
        return sb.toString();
    }

    private static String cell(String s) {
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s; // no formulas
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
