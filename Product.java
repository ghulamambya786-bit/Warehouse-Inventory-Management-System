import java.io.Serializable;

/** Model class for one warehouse product. */
public class Product implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int LOW_STOCK_LIMIT = 30;

    private String id;
    private String name;
    private String category;
    private int quantity;
    private double price;
    private String description = "";
    private int minStock = LOW_STOCK_LIMIT;
    private String imagePath = "";

    public Product(String id, String name, String category, int quantity, double price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.price = price;
    }

    public Product(String id, String name, String category, int quantity, double price,
                   String description, int minStock, String imagePath) {
        this(id, name, category, quantity, price);
        this.description = description == null ? "" : description;
        this.minStock = minStock;
        this.imagePath = imagePath == null ? "" : imagePath;
    }

    public String getDescription() { return description; }
    public int getMinStock() { return minStock; }
    public String getImagePath() { return imagePath; }
    public void setDescription(String d) { this.description = d == null ? "" : d; }
    public void setMinStock(int m) { this.minStock = m; }
    public void setImagePath(String p) { this.imagePath = p == null ? "" : p; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }

    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(double price) { this.price = price; }

    public double getValue() { return quantity * price; }

    public boolean isLowStock() { return quantity <= minStock; }

    public String getStatus() {
        if (quantity == 0) return "Out of Stock";
        if (quantity <= minStock) return "Low Stock";
        return "In Stock";
    }

    /** Snapshot copy, used when a product is pushed onto the stack. */
    public Product copy() {
        return new Product(id, name, category, quantity, price, description, minStock, imagePath);
    }

    /**
     * Validates raw text from the forms and builds a Product.
     * @throws IllegalArgumentException with a user-friendly message
     */
    public static Product parse(String id, String name, String category, String qty, String price) {
        id = id == null ? "" : id.trim();
        name = name == null ? "" : name.trim();
        category = category == null ? "" : category.trim();
        qty = qty == null ? "" : qty.trim();
        price = price == null ? "" : price.trim();

        if (id.isEmpty()) throw new IllegalArgumentException("Product ID is required.");
        if (!id.matches("[A-Za-z0-9_-]{1,20}"))
            throw new IllegalArgumentException("Product ID may contain only letters, digits, '-' or '_' (max 20 chars).");
        if (name.isEmpty()) throw new IllegalArgumentException("Product name is required.");
        if (name.length() > 100) throw new IllegalArgumentException("Product name is too long (max 100).");
        if (category.isEmpty()) throw new IllegalArgumentException("Category is required.");
        if (category.length() > 50) throw new IllegalArgumentException("Category is too long (max 50).");

        int q;
        try {
            q = Integer.parseInt(qty);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a whole number.");
        }
        if (q < 0) throw new IllegalArgumentException("Quantity cannot be negative.");

        double p;
        try {
            p = Double.parseDouble(price);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Price must be a valid number.");
        }
        if (Double.isNaN(p) || Double.isInfinite(p) || p < 0 || p > 99_999_999)
            throw new IllegalArgumentException("Price must be between 0 and 99,999,999.");

        return new Product(id.toUpperCase(), name, category, q, Math.round(p * 100.0) / 100.0);
    }

    /** Same as {@link #parse(String, String, String, String, String)} plus the extra form fields. */
    public static Product parse(String id, String name, String category, String qty, String price,
                                String description, String minStock, String imagePath) {
        Product p = parse(id, name, category, qty, price);
        String d = description == null ? "" : description.trim();
        if (d.length() > 255) throw new IllegalArgumentException("Description is too long (max 255).");
        int m;
        try {
            m = Integer.parseInt(minStock == null ? "" : minStock.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Minimum stock must be a whole number.");
        }
        if (m < 0) throw new IllegalArgumentException("Minimum stock cannot be negative.");
        p.setDescription(d);
        p.setMinStock(m);
        p.setImagePath(imagePath);
        return p;
    }

    @Override
    public String toString() { return id + " - " + name; }
}
