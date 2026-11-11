import java.time.LocalDate;

/** One batch of stock (a pallet) that sits on the warehouse stack: Batch ID, product, quantity, date and supplier. */
public class Batch {
    private final String batchId;
    private final String productId;
    private String productName;
    private final int quantity;
    private LocalDate date;
    private String supplier;
    private final double unitPrice;
    private final String createdBy;

    public Batch(String batchId, String productId, String productName, int quantity,
                 LocalDate date, String supplier, double unitPrice, String createdBy) {
        this.batchId = batchId;
        this.productId = productId;
        this.productName = productName == null ? productId : productName;
        this.quantity = quantity;
        this.date = date;
        this.supplier = supplier == null ? "" : supplier;
        this.unitPrice = unitPrice;
        this.createdBy = createdBy == null ? "" : createdBy;
    }

    public String getBatchId() { return batchId; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public LocalDate getDate() { return date; }
    public String getSupplier() { return supplier; }
    public double getUnitPrice() { return unitPrice; }
    public String getCreatedBy() { return createdBy; }
    public double getValue() { return quantity * unitPrice; }

    public void setProductName(String name) { this.productName = name; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setSupplier(String supplier) { this.supplier = supplier == null ? "" : supplier; }

    @Override
    public String toString() { return batchId + " - " + productName + " x" + quantity; }
}
