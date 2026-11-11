import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Page 3: form to add a new product, with a product picture. */
public class AddProductPanel extends JPanel implements Refreshable {
    private final ProductManager pm;
    private final Runnable afterAdd;

    private final JTextField idField = Theme.field();
    private final JTextField nameField = Theme.field();
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JTextField descField = Theme.field();
    private final JTextField qtyField = Theme.field();
    private final JTextField priceField = Theme.field();
    private final JTextField minField = Theme.field();
    private final JLabel preview = new JLabel("", SwingConstants.CENTER);
    private File chosenImage;

    public AddProductPanel(ProductManager pm, Runnable afterAdd) {
        this.pm = pm;
        this.afterAdd = afterAdd;
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));
        add(Theme.pageTitle("Add New Product", "add"), BorderLayout.NORTH);

        // ---- picture column
        RoundPanel pic = new RoundPanel(new BorderLayout(0, 12), new Color(0xDDE9F7), 18);
        pic.setShadow(false);
        pic.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        pic.setPreferredSize(new Dimension(230, 280));
        preview.setPreferredSize(new Dimension(200, 190));
        pic.add(preview, BorderLayout.CENTER);
        JButton choose = Theme.button("Choose Image", Theme.NAVY, Color.WHITE, "camera");
        pic.add(choose, BorderLayout.SOUTH);
        JPanel picWrap = Theme.transparent(new BorderLayout());
        picWrap.add(pic, BorderLayout.NORTH);

        // ---- form column
        categoryBox.setFont(Theme.BODY);
        categoryBox.setEditable(true);
        categoryBox.setPreferredSize(new Dimension(300, 40));
        idField.setPreferredSize(new Dimension(300, 40));
        for (JTextField f : new JTextField[]{nameField, descField, qtyField, priceField, minField}) {
            f.setPreferredSize(new Dimension(300, 40));
        }
        JPanel form = Theme.transparent(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(7, 6, 7, 6);
        row(form, gc, 0, "Product ID:", idField);
        row(form, gc, 1, "Product Name:", nameField);
        row(form, gc, 2, "Category:", categoryBox);
        row(form, gc, 3, "Description:", descField);
        qtyField.setToolTipText("Opening stock. It becomes the first batch (Opening Stock) on the stack.");
        row(form, gc, 4, "Opening Qty:", qtyField);
        row(form, gc, 5, "Price (PKR):", priceField);
        row(form, gc, 6, "Minimum Stock:", minField);

        JButton save = Theme.button("Add Product", Theme.GOLD, Theme.NAVY, "check");
        JButton reset = Theme.button("Reset", Theme.NAVY, Color.WHITE, "refresh");
        JPanel btns = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        btns.add(save);
        btns.add(reset);
        gc.gridx = 1;
        gc.gridy = 7;
        gc.insets = new Insets(16, 6, 4, 6);
        form.add(btns, gc);

        JPanel card = Theme.card(null);
        JPanel inner = Theme.transparent(new BorderLayout(26, 0));
        inner.add(picWrap, BorderLayout.WEST);
        inner.add(form, BorderLayout.CENTER);
        card.add(inner, BorderLayout.NORTH);
        JPanel holder = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.add(card);
        add(holder, BorderLayout.CENTER);

        choose.addActionListener(e -> chooseImage());
        save.addActionListener(e -> save());
        reset.addActionListener(e -> refresh());
        refresh();
    }

    private void row(JPanel form, GridBagConstraints gc, int r, String label, java.awt.Component field) {
        gc.gridx = 0;
        gc.gridy = r;
        gc.insets = new Insets(7, 6, 7, 14);
        JLabel l = Theme.label(label);
        l.setPreferredSize(new Dimension(120, 24));
        form.add(l, gc);
        gc.gridx = 1;
        gc.insets = new Insets(7, 6, 7, 6);
        form.add(field, gc);
    }

    @Override
    public void refresh() {
        try {
            idField.setText(pm.nextId());
        } catch (ProductManager.DbException ex) {
            idField.setText("");
            Theme.error(this, ex.getMessage());
        }
        nameField.setText("");
        descField.setText("");
        qtyField.setText("");
        priceField.setText("");
        minField.setText("10");
        try {
            categoryBox.removeAllItems();
            for (String c : pm.categoryNames()) categoryBox.addItem(c);
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
        if (categoryBox.getItemCount() > 0) categoryBox.setSelectedIndex(0);
        chosenImage = null;
        showPreview(Images.get("laptop"));
    }

    private void showPreview(BufferedImage img) {
        if (img == null) {
            preview.setIcon(null);
            preview.setText("No image");
        } else {
            preview.setText("");
            preview.setIcon(ImageUtil.fit(img, 200, 190));
        }
    }

    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images (png, jpg, gif)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        BufferedImage img = ImageUtil.load(f.getAbsolutePath());
        if (img == null) {
            Theme.error(this, "That file could not be read as an image.");
            return;
        }
        chosenImage = f;
        showPreview(img);
    }

    /** Copies the chosen picture into ./product_images so it stays available later. */
    private String storeImage(String productId) {
        if (chosenImage == null) return "";
        try {
            String name = chosenImage.getName();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";
            File dir = new File("product_images");
            if (!dir.exists() && !dir.mkdirs()) return chosenImage.getAbsolutePath();
            File target = new File(dir, productId + ext.toLowerCase());
            Files.copy(chosenImage.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target.getAbsolutePath();
        } catch (IOException | RuntimeException ex) {
            return chosenImage.getAbsolutePath();
        }
    }

    private void save() {
        try {
            Product p = Product.parse(idField.getText(), nameField.getText(),
                    String.valueOf(categoryBox.getSelectedItem()), qtyField.getText(), priceField.getText(),
                    descField.getText(), minField.getText(), "");
            if (pm.find(p.getId()) != null) {
                Theme.error(this, "A product with ID " + p.getId() + " already exists.");
                return;
            }
            p.setImagePath(storeImage(p.getId()));
            pm.add(p);
            Theme.info(this, "Product " + p.getName() + " was added successfully."
                    + (p.getQuantity() > 0 ? "\nIts opening stock was pushed onto the stack as a new batch." : ""));
            refresh();
            afterAdd.run();
        } catch (IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (ProductManager.DbException ex) {
            Theme.error(this, ex.getMessage());
        }
    }
}
