/** Page: Stock Out - remove units (sale, damage, ...). Never goes below zero. */
public class StockOutPanel extends StockMovementPanel {
    public StockOutPanel(ProductManager pm) { super(pm, false); }
}
