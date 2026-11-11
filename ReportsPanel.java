import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Page 7: category chart, monthly stock movement, summary cards, PDF and CSV export. */
public class ReportsPanel extends JPanel implements Refreshable {
    private final ProductManager pm;
    private final PieChartPanel pie = new PieChartPanel(false);
    private final BarChartPanel bars = new BarChartPanel();
    private final JLabel[] summary = new JLabel[3];
    private final JPanel body = Theme.transparent(new BorderLayout(0, 16));
    private List<Product> cache = new ArrayList<>();

    public ReportsPanel(ProductManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel header = Theme.transparent(new BorderLayout());
        header.add(Theme.pageTitle("Analytics & Reports", "report"), BorderLayout.WEST);
        JButton pdf = Theme.button("Export PDF", Theme.TEAL, Color.WHITE, "download");
        JButton csv = Theme.button("Export CSV", Theme.NAVY, Color.WHITE, "download");
        JPanel btns = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.add(csv);
        btns.add(pdf);
        header.add(btns, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel charts = new JPanel(new GridBagLayout());
        charts.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        JPanel pieCard = Theme.card("Category Distribution");
        pieCard.add(pie, BorderLayout.CENTER);
        JPanel barCard = Theme.card("Stock Movement");
        barCard.add(bars, BorderLayout.CENTER);
        gc.gridx = 0; gc.weightx = 0.42; gc.insets = new Insets(0, 0, 0, 16);
        charts.add(pieCard, gc);
        gc.gridx = 1; gc.weightx = 0.58; gc.insets = new Insets(0, 0, 0, 0);
        charts.add(barCard, gc);

        JPanel cards = new JPanel(new GridLayout(1, 3, 16, 0));
        cards.setOpaque(false);
        cards.setPreferredSize(new Dimension(100, 96));
        cards.add(summaryCard(0, "Total Stock", "box", new Color(0xDCEBFC), Theme.BLUE));
        cards.add(summaryCard(1, "Low Stock", "warning", new Color(0xFFE6C9), Theme.ORANGE));
        cards.add(summaryCard(2, "Inventory Value", "money", new Color(0xD3F0EC), Theme.TEAL));

        body.add(charts, BorderLayout.CENTER);
        body.add(cards, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);

        pdf.addActionListener(e -> exportPdf());
        csv.addActionListener(e -> exportCsv());
    }

    private JPanel summaryCard(int index, String title, String icon, Color bg, Color accent) {
        RoundPanel card = new RoundPanel(new BorderLayout(14, 0), bg, 18);
        card.setShadow(false);
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 14));
        RoundPanel circle = new RoundPanel(new BorderLayout(), Color.WHITE, 40);
        circle.setShadow(false);
        circle.setPreferredSize(new Dimension(56, 56));
        circle.add(new JLabel(Icons.icon(icon, 30, accent), SwingConstants.CENTER), BorderLayout.CENTER);
        JPanel cw = Theme.transparent(new GridBagLayout());
        cw.add(circle);
        JLabel t = new JLabel(title);
        t.setFont(Theme.BOLD);
        t.setForeground(Theme.NAVY);
        summary[index] = new JLabel("-");
        summary[index].setFont(new Font("Segoe UI", Font.BOLD, 26));
        summary[index].setForeground(Theme.NAVY);
        JPanel text = Theme.transparent(new GridLayout(2, 1));
        text.add(t);
        text.add(summary[index]);
        card.add(cw, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void refresh() {
        try {
            cache = pm.getAll();
            int units = 0, low = 0;
            double value = 0;
            Map<String, Double> byCategory = new LinkedHashMap<>();
            for (Product p : cache) {
                units += p.getQuantity();
                value += p.getValue();
                byCategory.merge(p.getCategory(), 1.0, Double::sum);
                if (p.isLowStock()) low++;
            }
            summary[0].setText(String.format("%,d", units));
            summary[1].setText(String.valueOf(low));
            summary[2].setText(String.format("₨%,.0f", value));
            pie.setData(byCategory, Theme.TEAL, Theme.GOLD, Theme.NAVY, Theme.RED, Theme.ORANGE, Theme.BLUE);

            long[][] mv = pm.monthlyMovement(6);
            bars.setData(ProductManager.monthLabels(6),
                    new double[][]{toDouble(mv[0]), toDouble(mv[1])},
                    new String[]{"Stock In", "Stock Out"}, new Color[]{Theme.TEAL, Theme.GOLD});
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private static double[] toDouble(long[] a) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) d[i] = a[i];
        return d;
    }

    // ----------------------------------------------------------------- PDF
    private void exportPdf() {
        refresh();
        File f = chooseFile("inventory_report.pdf", ".pdf");
        if (f == null) return;
        int scale = 2;
        int w = body.getWidth(), h = body.getHeight();
        if (w <= 0 || h <= 0) {
            Theme.error(this, "Open this page first, then export.");
            return;
        }
        int head = 70;
        BufferedImage img = new BufferedImage(w * scale, (h + head) * scale, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        Theme.antialias(g);
        g.scale(scale, scale);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, w, h + head);
        g.setColor(Theme.NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g.drawString("Warehouse Inventory Report", 4, 34);
        g.setColor(Theme.MUTED);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.drawString("Generated by " + pm.getCurrentUser() + " on "
                + new SimpleDateFormat("dd-MM-yyyy HH:mm").format(new Date()), 4, 56);
        g.translate(0, head);
        body.paint(g);
        g.dispose();
        try {
            PdfExport.saveImage(f, img);
        } catch (IOException ex) {
            Theme.error(this, "Could not write the PDF: " + ex.getMessage());
            return;
        }
        Theme.info(this, "PDF report saved to:\n" + f.getAbsolutePath());
    }

    // ----------------------------------------------------------------- CSV
    private File chooseFile(String suggested, String ext) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(suggested));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(ext)) f = new File(f.getParentFile(), f.getName() + ext);
        if (f.exists() && JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?",
                "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return null;
        return f;
    }

    private void exportCsv() {
        refresh();
        File f = chooseFile("inventory_report.csv", ".csv");
        if (f == null) return;
        int units = 0, low = 0;
        double value = 0;
        Map<String, Integer> cat = new LinkedHashMap<>();
        for (Product p : cache) {
            units += p.getQuantity();
            value += p.getValue();
            if (p.isLowStock()) low++;
            cat.merge(p.getCategory(), 1, Integer::sum);
        }
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            out.print('\uFEFF');                       // BOM so Excel reads UTF-8 correctly
            out.println("WAREHOUSE INVENTORY REPORT");
            out.println("Generated by," + InventoryPanel.csv(pm.getCurrentUser()));
            out.println();
            out.println("SUMMARY");
            out.println("Total Products," + cache.size());
            out.println("Total Stock Units," + units);
            out.println("Inventory Value," + String.format("%.2f", value));
            out.println("Low Stock Products," + low);
            out.println();
            out.println("CATEGORY DISTRIBUTION");
            out.println("Category,Products");
            for (Map.Entry<String, Integer> e : cat.entrySet()) {
                out.println(InventoryPanel.csv(e.getKey()) + "," + e.getValue());
            }
            out.println();
            out.println("PRODUCTS");
            out.println("Product ID,Product Name,Category,Quantity,Price,Total Value,Status");
            for (Product p : cache) {
                out.println(InventoryPanel.csv(p.getId()) + "," + InventoryPanel.csv(p.getName()) + ","
                        + InventoryPanel.csv(p.getCategory()) + "," + p.getQuantity() + ","
                        + String.format("%.2f", p.getPrice()) + "," + String.format("%.2f", p.getValue()) + ","
                        + InventoryPanel.csv(p.getStatus()));
            }
            if (out.checkError()) throw new IOException("write error");
        } catch (IOException ex) {
            Theme.error(this, "Could not write the file: " + ex.getMessage());
            return;
        }
        Theme.info(this, "Report exported to:\n" + f.getAbsolutePath());
    }
}
