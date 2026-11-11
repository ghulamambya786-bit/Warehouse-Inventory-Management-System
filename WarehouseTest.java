import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

/**
 * Simple self-check program (no JUnit needed).
 *   Part 1: the CustomStack alone (no database).
 *   Part 2: PUSH / POP / PEEK, product quantities and persistence against MySQL.
 * Part 2 uses its own database "warehouse_test_db", so your real data in "warehouse_db" is never touched.
 *
 * Run:  java -cp ".:lib/*" WarehouseTest      (Windows: java -cp ".;lib/*" WarehouseTest)
 */
public class WarehouseTest {
    private static int passed, failed;

    private static void check(String what, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what);
    }

    private static Batch batch(String id, int qty) {
        return new Batch(id, "P001", "Laptop", qty, LocalDate.now(), "Supplier");
    }

    private interface Action { void run() throws Exception; }

    private static boolean throwsIllegal(Action a) {
        try { a.run(); return false; } catch (IllegalArgumentException e) { return true; } catch (Exception e) { return false; }
    }

    public static void main(String[] args) throws Exception {
        stackOnly();
        database();
        System.out.println("\nResult: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------ part 1
    private static void stackOnly() {
        System.out.println("PART 1 - CustomStack (LIFO)");
        CustomStack s = new CustomStack(3);
        check("new stack is empty, TOP == -1", s.isEmpty() && s.getTop() == -1 && s.size() == 0);
        s.push(batch("B001", 10));
        s.push(batch("B002", 20));
        s.push(batch("B003", 15));
        check("after 3 PUSH: size 3, TOP == 2, isFull", s.size() == 3 && s.getTop() == 2 && s.isFull());
        check("PEEK shows B003 (latest) and does not remove it", s.peek().getBatchId().equals("B003") && s.size() == 3);
        check("toArray is BOTTOM to TOP: B001, B002, B003",
                s.toArray()[0].getBatchId().equals("B001") && s.toArray()[2].getBatchId().equals("B003"));
        boolean overflow = false;
        try { s.push(batch("B004", 1)); } catch (CustomStack.StackOverflowException e) { overflow = true; }
        check("PUSH on a full stack -> StackOverflowException", overflow && s.size() == 3);
        check("POP #1 returns B003", s.pop().getBatchId().equals("B003"));
        check("POP #2 returns B002", s.pop().getBatchId().equals("B002"));
        check("POP #3 returns B001, stack empty again", s.pop().getBatchId().equals("B001") && s.isEmpty());
        boolean underflow = false, underflowPeek = false;
        try { s.pop(); } catch (CustomStack.StackUnderflowException e) { underflow = true; }
        try { s.peek(); } catch (CustomStack.StackUnderflowException e) { underflowPeek = true; }
        check("POP / PEEK on an empty stack -> StackUnderflowException", underflow && underflowPeek);
    }

    // ------------------------------------------------------------ part 2
    private static String ids(List<Batch> top2bottom) {
        StringBuilder sb = new StringBuilder();
        for (Batch b : top2bottom) sb.append(sb.length() == 0 ? "" : ",").append(b.getBatchId());
        return sb.toString();
    }

    private static int qty(ProductManager pm, String productId) { return pm.find(productId).getQuantity(); }

    private static int count(String sql) throws Exception {
        try (Connection c = DBConnection.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void database() throws Exception {
        System.out.println("\nPART 2 - MySQL (database warehouse_test_db)");
        DBConnection.useDatabase("warehouse_test_db");
        DBConnection.initialize();
        try (Connection c = DBConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("SET FOREIGN_KEY_CHECKS=0");
            st.executeUpdate("TRUNCATE TABLE batches");
            st.executeUpdate("TRUNCATE TABLE stock_history");
            st.executeUpdate("TRUNCATE TABLE products");
            st.executeUpdate("SET FOREIGN_KEY_CHECKS=1");
        }
        DBConnection.initialize();          // seeds the sample data again
        ProductManager pm = new ProductManager();
        pm.setCurrentUser("tester");
        LocalDate today = LocalDate.now();

        System.out.println("Sample data");
        check("5 products, 3 batches, 45 units", pm.totalProducts() == 5 && pm.totalBatches() == 3 && pm.totalStockUnits() == 45);
        check("stack TOP to BOTTOM = B003,B002,B001", ids(pm.getBatches()).equals("B003,B002,B001"));
        check("Laptop quantity = 10+20+15 = 45", qty(pm, "P001") == 45);

        System.out.println("PEEK");
        check("PEEK = B003 and nothing is removed", pm.peekBatch().getBatchId().equals("B003") && pm.totalBatches() == 3);

        System.out.println("PUSH");
        Batch b4 = pm.pushBatch("b004", "P002", 30, today, "Mouse Co");
        check("PUSH B004 (30 Mouse): it is now the TOP", b4.getBatchId().equals("B004") && pm.peekBatch().getBatchId().equals("B004"));
        check("Mouse quantity 0 -> 30, total units 75", qty(pm, "P002") == 30 && pm.totalStockUnits() == 75);
        pm.pushBatch("B005", "P002", 5, today, "Mouse Co");
        check("second Mouse batch on the same day is allowed (qty 35)", qty(pm, "P002") == 35 && pm.totalBatches() == 5);
        check("stack = B005,B004,B003,B002,B001", ids(pm.getBatches()).equals("B005,B004,B003,B002,B001"));
        check("database positions are 0..4 in push order",
                count("SELECT COUNT(*) FROM batches WHERE (batch_id='B001' AND position=0) OR (batch_id='B002' AND position=1) "
                        + "OR (batch_id='B003' AND position=2) OR (batch_id='B004' AND position=3) OR (batch_id='B005' AND position=4)") == 5);
        check("memory stack == database", pm.isStackInSync());

        System.out.println("Stock alert");
        check("Laptop (45, min 20) is In Stock; Mouse (35, min 20) is In Stock", pm.find("P001").getStatus().equals("In Stock")
                && pm.find("P002").getStatus().equals("In Stock"));
        check("Keyboard / Monitor / Notebook are Out of Stock and counted as low", pm.getLowStock().size() == 3
                && pm.find("P003").getStatus().equals("Out of Stock"));
        check("inventory value = 45*800 + 35*25 = 36875", Math.abs(pm.inventoryValue() - 36875.0) < 0.001);
        check("bad minimum stock is rejected", throwsIllegal(() -> Product.parse("P020", "X", "Y", "1", "abc"))
                && throwsIllegal(() -> Product.parse("P020", "X", "Y", "1", "-1")));
        check("stack search finds B005 on top (0 below TOP) and Laptop batches",
                pm.searchBatches("b005").matches.size() == 1 && pm.searchBatches("b005").depths.get(0) == 0
                        && pm.searchBatches("laptop").matches.size() == 3 && pm.searchBatches("zzz").matches.isEmpty());

        System.out.println("PUSH validation");
        check("duplicate Batch ID is rejected", throwsIllegal(() -> pm.pushBatch("B004", "P002", 1, today, "X")));
        check("unknown product is rejected", throwsIllegal(() -> pm.pushBatch("B100", "P999", 1, today, "X")));
        check("quantity 0 is rejected", throwsIllegal(() -> pm.pushBatch("B100", "P002", 0, today, "X")));
        check("negative quantity is rejected", throwsIllegal(() -> pm.pushBatch("B100", "P002", -5, today, "X")));
        check("future date is rejected", throwsIllegal(() -> pm.pushBatch("B100", "P002", 1, today.plusDays(1), "X")));
        check("empty supplier is rejected", throwsIllegal(() -> pm.pushBatch("B100", "P002", 1, today, "  ")));
        check("bad Batch ID text is rejected", throwsIllegal(() -> pm.pushBatch("B 1;", "P002", 1, today, "X")));
        check("nothing changed after the failed pushes", pm.totalBatches() == 5 && pm.isStackInSync());

        System.out.println("Delete / update rules");
        check("product with batches cannot be deleted (no removal from the middle)", throwsIllegal(() -> pm.delete("P001")));
        check("product still exists", pm.find("P001") != null && pm.totalBatches() == 5);
        Product renamed = Product.parse("P002", "Wireless Mouse", "Electronics", "27.5", "20");
        pm.update(renamed);
        check("rename keeps quantity (35) and updates batch names", qty(pm, "P002") == 35
                && pm.peekBatch().getProductName().equals("Wireless Mouse"));

        System.out.println("Restart (persistence)");
        pm.reloadStack();
        check("stack reloaded from MySQL has the same order", ids(pm.getBatches()).equals("B005,B004,B003,B002,B001"));
        ProductManager pm2 = new ProductManager();
        check("a brand-new ProductManager restores TOP = B005", pm2.peekBatch().getBatchId().equals("B005") && pm2.totalBatches() == 5);

        System.out.println("POP");
        Batch p1 = pm.popBatch();
        check("POP #1 = B005 (last in, first out)", p1.getBatchId().equals("B005") && ids(pm.getBatches()).equals("B004,B003,B002,B001"));
        check("Mouse quantity 35 -> 30 automatically", qty(pm, "P002") == 30);
        check("Mouse (30, min 20) is still In Stock", pm.find("P002").getStatus().equals("In Stock"));
        Batch p2 = pm.popBatch();
        check("POP #2 = B004, Mouse quantity 0", p2.getBatchId().equals("B004") && qty(pm, "P002") == 0);
        check("Mouse status is Out of Stock and the alert counts it", pm.find("P002").getStatus().equals("Out of Stock")
                && pm.find("P002").isLowStock());
        pm.pushBatch("Z900", "P002", 10, today, "Small Co");
        check("Mouse with 10 units (min 20) is Low Stock, not Out of Stock", pm.find("P002").getStatus().equals("Low Stock"));
        pm.popBatch();                                      // removes Z900 again
        Batch p3 = pm.popBatch();
        check("POP #3 = B003 (15 Laptops), Laptop quantity 45 -> 30", p3.getBatchId().equals("B003") && qty(pm, "P001") == 30);
        check("database now has exactly B002,B001 and matches memory", count("SELECT COUNT(*) FROM batches") == 2 && pm.isStackInSync());
        check("a new ProductManager sees the same stack after POPs", ids(new ProductManager().getBatches()).equals("B002,B001"));

        System.out.println("Unique ids and history");
        check("popped Batch IDs are never reused", throwsIllegal(() -> pm.pushBatch("B005", "P002", 1, today, "X")));
        check("next free Batch ID is B006", pm.nextBatchId().equals("B006"));
        check("history has 6 PUSH and 4 POP records",
                count("SELECT COUNT(*) FROM stock_history WHERE action='PUSH'") == 6
                        && count("SELECT COUNT(*) FROM stock_history WHERE action='POP'") == 4);
        check("newest history row is the last POP (B003)", pm.getHistory(10).get(0)[1].equals("POP") && pm.getHistory(10).get(0)[2].equals("B003"));

        System.out.println("Empty stack and delete");
        pm.popBatch();
        pm.popBatch();
        check("stack is empty, 0 batches, 0 units", pm.getStack().isEmpty() && pm.totalBatches() == 0 && pm.totalStockUnits() == 0);
        boolean underflow = false;
        try { pm.popBatch(); } catch (CustomStack.StackUnderflowException e) { underflow = true; }
        check("POP on empty stack -> StackUnderflowException", underflow);
        underflow = false;
        try { pm.peekBatch(); } catch (CustomStack.StackUnderflowException e) { underflow = true; }
        check("PEEK on empty stack -> StackUnderflowException", underflow);
        pm.delete("P005");
        check("product without batches can be deleted", pm.find("P005") == null && pm.totalProducts() == 4);
        Product added = Product.parse("P010", "Desk", "Furniture", "99", "5");
        pm.add(added);
        check("new product starts with quantity 0", qty(pm, "P010") == 0 && pm.totalProducts() == 5);
        check("duplicate product ID is rejected", throwsIllegal(() -> pm.add(Product.parse("P010", "Other", "Furniture", "1", "5"))));
        check("search finds the product by name", pm.search("desk").size() == 1);

        System.out.println("Stack capacity (" + ProductManager.STACK_CAPACITY + ")");
        for (int i = 1; i <= ProductManager.STACK_CAPACITY; i++) pm.pushBatch(String.format("C%03d", i), "P010", 1, today, "Bulk");
        check("stack is full: 100 batches, 100 units", pm.getStack().isFull() && pm.totalBatches() == 100 && qty(pm, "P010") == 100);
        boolean overflow = false;
        try { pm.pushBatch("C999", "P010", 1, today, "Bulk"); } catch (CustomStack.StackOverflowException e) { overflow = true; }
        check("101st PUSH -> StackOverflowException, database unchanged",
                overflow && count("SELECT COUNT(*) FROM batches") == 100 && pm.isStackInSync());
        check("restart with a full stack: TOP = C100", new ProductManager().peekBatch().getBatchId().equals("C100"));

        System.out.println("Out-of-sync protection");
        try (Connection c = DBConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM batches WHERE batch_id='C100'");   // someone changes the database behind our back
        }
        boolean caught = false;
        try { pm.popBatch(); } catch (ProductManager.DbException e) { caught = true; }
        check("POP detects the mismatch instead of corrupting data", caught);
        check("stack reloaded from the database: TOP = C099", pm.peekBatch().getBatchId().equals("C099") && pm.isStackInSync());
    }
}
