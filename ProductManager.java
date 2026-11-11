import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-access layer: all JDBC work (products, batches/stack, categories, users, history).
 * The warehouse stack lives in memory ({@link CustomStack}) and is mirrored in the MySQL table "batches"
 * after every PUSH / POP, so it survives an application restart.
 */
public class ProductManager {
    /** Maximum number of batches that can be on the stack at the same time. */
    public static final int STACK_CAPACITY = 10;

    /** Unchecked wrapper so GUI code can catch one exception type for DB problems. */
    public static class DbException extends RuntimeException {
        public DbException(String message, Throwable cause) { super(message, cause); }
    }

    private String currentUser = "System";
    private String currentRole = User.ADMIN;
    private CustomStack stack;

    public void setCurrentUser(String user) { this.currentUser = user; }
    public String getCurrentUser() { return currentUser; }
    public void setCurrentRole(String role) { this.currentRole = User.STAFF.equalsIgnoreCase(role) ? User.STAFF : User.ADMIN; }
    public String getCurrentRole() { return currentRole; }
    public boolean isAdmin() { return User.ADMIN.equals(currentRole); }

    private void requireAdmin(String what) {
        if (!isAdmin()) throw new IllegalArgumentException("Only an Admin can " + what + ".");
    }

    // ---------------------------------------------------------------- login
    public boolean authenticate(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username=? AND password_hash=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, DBConnection.hash(password));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DbException("Login failed: " + e.getMessage(), e);
        }
    }

    /** @return the account if username and password are right, otherwise null */
    public User login(String username, String password) {
        if (!authenticate(username, password)) return null;
        String sql = "SELECT username, full_name, role FROM users WHERE username=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new User(rs.getString(1), rs.getString(2), rs.getString(3)) : null;
            }
        } catch (SQLException e) {
            throw new DbException("Login failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------- products
    private Product map(ResultSet rs) throws SQLException {
        return new Product(rs.getString("product_id"), rs.getString("name"),
                rs.getString("category"), rs.getInt("quantity"), rs.getDouble("price"),
                rs.getString("description"), rs.getInt("min_stock"), rs.getString("image_path"));
    }

    public List<Product> getAll() {
        return search("");
    }

    public List<Product> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        String sql = "SELECT * FROM products WHERE product_id LIKE ? OR name LIKE ? OR category LIKE ? "
                + "ORDER BY product_id";
        List<Product> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            String like = "%" + k + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DbException("Could not load products: " + e.getMessage(), e);
        }
        return list;
    }

    public Product find(String id) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM products WHERE product_id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DbException("Could not read product: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------- product changes
    /**
     * Adds a product. If it starts with stock, that stock becomes an "Opening Stock" batch pushed onto the stack,
     * so product quantity always equals the sum of its batches.
     */
    public void add(Product p) {
        int opening = p.getQuantity();
        if (opening > 0 && getStack().isFull()) {
            throw new IllegalArgumentException("The stack is full (" + STACK_CAPACITY
                    + " batches). POP a batch first, or add the product with quantity 0.");
        }
        String sql = "INSERT INTO products(product_id,name,category,quantity,price,description,min_stock,image_path) "
                + "VALUES(?,?,?,?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getId());
            ps.setString(2, p.getName());
            ps.setString(3, p.getCategory());
            ps.setInt(4, 0);                                  // stock comes from the batch below
            ps.setDouble(5, p.getPrice());
            ps.setString(6, p.getDescription());
            ps.setInt(7, p.getMinStock());
            ps.setString(8, p.getImagePath());
            ps.executeUpdate();
            ensureCategory(c, p.getCategory());
        } catch (SQLException e) {
            throw new DbException("Could not add product: " + e.getMessage(), e);
        }
        String batchId = null;
        if (opening > 0) {
            try {
                batchId = nextBatchId();
                pushInternal(batchId, p.getId(), opening, LocalDate.now(), "Opening Stock", p.getPrice(), false);
            } catch (RuntimeException ex) {
                deleteProductRow(p.getId());                  // undo the half-finished add
                throw ex;
            }
        }
        log(p.getName(), "Added", opening, null, batchId);
    }

    /** Updates the product details. Quantity is not touched here: it only changes through PUSH and POP. */
    public void update(Product p) {
        String sql = "UPDATE products SET name=?, category=?, price=?, description=?, "
                + "min_stock=?, image_path=? WHERE product_id=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, p.getCategory());
            ps.setDouble(3, p.getPrice());
            ps.setString(4, p.getDescription());
            ps.setInt(5, p.getMinStock());
            ps.setString(6, p.getImagePath());
            ps.setString(7, p.getId());
            ps.executeUpdate();
            ensureCategory(c, p.getCategory());
        } catch (SQLException e) {
            throw new DbException("Could not update product: " + e.getMessage(), e);
        }
        Product now = find(p.getId());
        log(p.getName(), "Updated", now == null ? 0 : now.getQuantity());
    }

    /** Deletes a product (Admin only) together with all of its batches; the other batches keep their order. */
    public void delete(String id) {
        requireAdmin("delete products");
        Product p = find(id);
        if (p == null) return;
        CustomStack s = getStack();
        int removedBatches = s.removeWhere(b -> b.getProductId().equals(id));   // stack-only removal
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM batches WHERE product_id=?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                rewritePositions(c, s.toArray());
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE product_id=?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            stack = null;                                     // reload the stack from the database next time
            throw new DbException("Could not delete product: " + e.getMessage(), e);
        }
        log(p.getName(), "Removed", p.getQuantity(), removedBatches > 0 ? removedBatches + " batch(es) removed" : null, null);
    }

    private void deleteProductRow(String id) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE product_id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    // ------------------------------------------------- batches (the stack)
    /** The shared in-memory stack; loaded from MySQL the first time it is needed. */
    public CustomStack getStack() {
        if (stack == null) stack = loadStack();
        return stack;
    }

    private CustomStack loadStack() {
        CustomStack s = new CustomStack(STACK_CAPACITY);
        String sql = "SELECT b.batch_id, b.product_id, p.name, b.quantity, b.batch_date, b.supplier, "
                + "b.unit_price, b.created_by FROM batches b LEFT JOIN products p ON p.product_id = b.product_id "
                + "ORDER BY b.position";
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next() && !s.isFull()) {
                java.sql.Date d = rs.getDate(5);
                s.push(new Batch(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        d == null ? LocalDate.now() : d.toLocalDate(), rs.getString(6), rs.getDouble(7), rs.getString(8)));
            }
        } catch (SQLException e) {
            throw new DbException("Could not load the stack: " + e.getMessage(), e);
        }
        return s;
    }

    /** Re-reads product names so renamed products show their new name on their batches. */
    private void syncBatchNames() {
        Map<String, String> names = new HashMap<>();
        for (Product p : getAll()) names.put(p.getId(), p.getName());
        for (Batch b : getStack().toArray()) {
            String n = names.get(b.getProductId());
            if (n != null) b.setProductName(n);
        }
    }

    /** All batches, TOP first (latest batch first). */
    public List<Batch> getBatches() {
        syncBatchNames();
        Batch[] arr = getStack().toArray();
        List<Batch> list = new ArrayList<>();
        for (int i = arr.length - 1; i >= 0; i--) list.add(arr[i]);
        return list;
    }

    /** Next free batch id such as B011 (never re-uses an id that appears in the history). */
    public String nextBatchId() {
        int max = 0;
        String sql = "SELECT batch_id FROM batches UNION SELECT batch_id FROM stock_history WHERE batch_id IS NOT NULL";
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id != null && id.matches("B\\d{1,9}")) max = Math.max(max, Integer.parseInt(id.substring(1)));
            }
        } catch (SQLException e) {
            throw new DbException("Could not create a batch id: " + e.getMessage(), e);
        }
        return String.format("B%03d", max + 1);
    }

    private boolean batchIdUsed(String id) {
        String sql = "SELECT 1 FROM batches WHERE batch_id=? UNION SELECT 1 FROM stock_history WHERE batch_id=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DbException("Could not check the batch id: " + e.getMessage(), e);
        }
    }

    /**
     * Stock In = PUSH: a new batch goes on top of the stack and the product quantity grows by the batch quantity.
     * @throws IllegalArgumentException with a user-friendly message
     * @throws CustomStack.StackOverflowException when the stack is full
     */
    public Batch pushBatch(String batchId, String productId, int qty, LocalDate date, String supplier, double unitPrice) {
        return pushInternal(batchId, productId, qty, date, supplier, unitPrice, true);
    }

    private Batch pushInternal(String batchId, String productId, int qty, LocalDate date, String supplier,
                               double unitPrice, boolean logPush) {
        batchId = batchId == null ? "" : batchId.trim().toUpperCase();
        supplier = supplier == null ? "" : supplier.trim();
        if (!batchId.matches("[A-Z0-9_-]{1,20}"))
            throw new IllegalArgumentException("Batch ID may contain only letters, digits, '-' or '_' (max 20 chars).");
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than 0.");
        if (qty > 10_000_000) throw new IllegalArgumentException("Quantity is too large.");
        if (unitPrice < 0 || Double.isNaN(unitPrice)) throw new IllegalArgumentException("Price cannot be negative.");
        if (date == null) throw new IllegalArgumentException("Batch date is required.");
        if (supplier.length() > 100) throw new IllegalArgumentException("Supplier name is too long (max 100).");
        Product product = find(productId);
        if (product == null) throw new IllegalArgumentException("Product not found.");
        if (batchIdUsed(batchId)) throw new IllegalArgumentException("Batch ID " + batchId + " has already been used.");

        CustomStack s = getStack();
        Batch b = new Batch(batchId, productId, product.getName(), qty, date, supplier, unitPrice, currentUser);
        s.push(b);                                            // PUSH (throws StackOverflowException when full)
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO batches(batch_id,product_id,quantity,batch_date,supplier,unit_price,position,created_by) "
                                + "VALUES(?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, batchId);
                    ps.setString(2, productId);
                    ps.setInt(3, qty);
                    ps.setDate(4, java.sql.Date.valueOf(date));
                    ps.setString(5, supplier);
                    ps.setDouble(6, unitPrice);
                    ps.setInt(7, s.getTop());
                    ps.setString(8, currentUser);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET quantity = quantity + ?, price = ? WHERE product_id=?")) {
                    ps.setInt(1, qty);
                    ps.setDouble(2, unitPrice);
                    ps.setString(3, productId);
                    ps.executeUpdate();
                }
                if (logPush) {
                    log(c, product.getName(), "PUSH", qty,
                            "Supplier: " + (supplier.isEmpty() ? "-" : supplier) + String.format(", price %.2f", unitPrice), batchId);
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            s.pop();                                          // undo the in-memory PUSH
            throw new DbException("Could not push the batch: " + e.getMessage(), e);
        }
        return b;
    }

    /**
     * Stock Out = POP: the latest (TOP) batch is removed and the product quantity drops by the batch quantity.
     * @throws CustomStack.StackUnderflowException when there is no batch
     */
    public Batch popBatch(String reason) {
        reason = reason == null || reason.trim().isEmpty() ? "Other" : reason.trim();
        if (reason.length() > 80) reason = reason.substring(0, 80);
        CustomStack s = getStack();
        Batch b = s.pop();                                    // POP (throws StackUnderflowException when empty)
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM batches WHERE batch_id=?")) {
                    ps.setString(1, b.getBatchId());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET quantity = CASE WHEN quantity >= ? THEN quantity - ? ELSE 0 END "
                                + "WHERE product_id=?")) {
                    ps.setInt(1, b.getQuantity());
                    ps.setInt(2, b.getQuantity());
                    ps.setString(3, b.getProductId());
                    ps.executeUpdate();
                }
                log(c, b.getProductName(), "POP", b.getQuantity(), "Reason: " + reason, b.getBatchId());
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            s.push(b);                                        // undo the in-memory POP
            throw new DbException("Could not pop the batch: " + e.getMessage(), e);
        }
        return b;
    }

    /** PEEK: the TOP batch, not removed. */
    public Batch peekBatch() {
        syncBatchNames();
        return getStack().peek();
    }

    /** Searches the stack (TOP to BOTTOM) for a batch id, product or supplier. */
    public CustomStack.SearchResult searchBatches(String keyword) {
        syncBatchNames();
        final String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return getStack().search(b -> b.getBatchId().toLowerCase().contains(k)
                || b.getProductId().toLowerCase().contains(k)
                || b.getProductName().toLowerCase().contains(k)
                || b.getSupplier().toLowerCase().contains(k));
    }

    /** Batch Management: change the date or the supplier of a batch (quantity stays, it is stock). */
    public void updateBatch(String batchId, LocalDate date, String supplier) {
        supplier = supplier == null ? "" : supplier.trim();
        if (date == null) throw new IllegalArgumentException("Batch date is required.");
        if (supplier.length() > 100) throw new IllegalArgumentException("Supplier name is too long (max 100).");
        Batch target = null;
        for (Batch b : getStack().toArray()) if (b.getBatchId().equals(batchId)) target = b;
        if (target == null) throw new IllegalArgumentException("Batch " + batchId + " is no longer on the stack.");
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE batches SET batch_date=?, supplier=? WHERE batch_id=?")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.setString(2, supplier);
            ps.setString(3, batchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Could not update the batch: " + e.getMessage(), e);
        }
        target.setDate(date);
        target.setSupplier(supplier);
    }

    private void rewritePositions(Connection c, Batch[] bottomToTop) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE batches SET position=? WHERE batch_id=?")) {
            for (int i = 0; i < bottomToTop.length; i++) {
                ps.setInt(1, i);
                ps.setString(2, bottomToTop[i].getBatchId());
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------ categories
    private void ensureCategory(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO categories(name) VALUES(?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    public List<String> categoryNames() {
        List<String> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM categories ORDER BY name")) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new DbException("Could not load categories: " + e.getMessage(), e);
        }
        return list;
    }

    /** @return rows {name, description, number of products} */
    public List<Object[]> getCategoryRows() {
        String sql = "SELECT c.name, c.description, COUNT(p.product_id) FROM categories c "
                + "LEFT JOIN products p ON p.category = c.name GROUP BY c.name, c.description ORDER BY c.name";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) rows.add(new Object[]{rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2), rs.getInt(3)});
        } catch (SQLException e) {
            throw new DbException("Could not load categories: " + e.getMessage(), e);
        }
        return rows;
    }

    private static String cleanCategory(String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Category name is required.");
        if (name.length() > 50) throw new IllegalArgumentException("Category name is too long (max 50).");
        return name;
    }

    public void addCategory(String name, String description) {
        requireAdmin("manage categories");
        name = cleanCategory(name);
        description = description == null ? "" : description.trim();
        if (description.length() > 255) throw new IllegalArgumentException("Description is too long (max 255).");
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO categories(name,description) VALUES(?,?)")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Category \"" + name + "\" already exists.");
            throw new DbException("Could not add the category: " + e.getMessage(), e);
        }
    }

    /** Renames a category (all its products follow) and/or changes its description. */
    public void updateCategory(String oldName, String newName, String description) {
        requireAdmin("manage categories");
        newName = cleanCategory(newName);
        description = description == null ? "" : description.trim();
        if (description.length() > 255) throw new IllegalArgumentException("Description is too long (max 255).");
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("UPDATE categories SET name=?, description=? WHERE name=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, description);
                    ps.setString(3, oldName);
                    ps.executeUpdate();
                }
                if (!newName.equals(oldName)) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE products SET category=? WHERE category=?")) {
                        ps.setString(1, newName);
                        ps.setString(2, oldName);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Category \"" + newName + "\" already exists.");
            throw new DbException("Could not update the category: " + e.getMessage(), e);
        }
    }

    public void deleteCategory(String name) {
        requireAdmin("manage categories");
        try (Connection c = DBConnection.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM products WHERE category=?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) throw new IllegalArgumentException("Category \"" + name + "\" is used by "
                            + rs.getInt(1) + " product(s). Move or delete those products first.");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE name=?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DbException("Could not delete the category: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------- users
    public List<User> getUsers() {
        List<User> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username, full_name, role FROM users ORDER BY role, username")) {
            while (rs.next()) list.add(new User(rs.getString(1), rs.getString(2), rs.getString(3)));
        } catch (SQLException e) {
            throw new DbException("Could not load users: " + e.getMessage(), e);
        }
        return list;
    }

    private int adminCount(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (password.length() > 100) throw new IllegalArgumentException("Password is too long.");
    }

    public void addUser(String username, String password, String fullName, String role) {
        requireAdmin("manage users");
        username = username == null ? "" : username.trim();
        fullName = fullName == null ? "" : fullName.trim();
        if (!username.matches("[A-Za-z0-9_.-]{3,30}"))
            throw new IllegalArgumentException("Username must be 3-30 characters: letters, digits, '.', '-' or '_'.");
        if (fullName.length() > 100) throw new IllegalArgumentException("Full name is too long (max 100).");
        checkPassword(password);
        String r = User.STAFF.equalsIgnoreCase(role) ? User.STAFF : User.ADMIN;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(username,password_hash,full_name,role) VALUES(?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, DBConnection.hash(password));
            ps.setString(3, fullName);
            ps.setString(4, r);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Username \"" + username + "\" already exists.");
            throw new DbException("Could not add the user: " + e.getMessage(), e);
        }
    }

    public void deleteUser(String username) {
        requireAdmin("manage users");
        if (username.equalsIgnoreCase(currentUser)) throw new IllegalArgumentException("You cannot delete the account you are logged in with.");
        try (Connection c = DBConnection.getConnection()) {
            String role = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT role FROM users WHERE username=?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) role = rs.getString(1);
                }
            }
            if (role == null) return;
            if (User.ADMIN.equals(role) && adminCount(c) <= 1) throw new IllegalArgumentException("The last Admin cannot be deleted.");
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE username=?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DbException("Could not delete the user: " + e.getMessage(), e);
        }
    }

    public void setUserRole(String username, String role) {
        requireAdmin("manage users");
        String r = User.STAFF.equalsIgnoreCase(role) ? User.STAFF : User.ADMIN;
        try (Connection c = DBConnection.getConnection()) {
            if (User.STAFF.equals(r)) {
                try (PreparedStatement ps = c.prepareStatement("SELECT role FROM users WHERE username=?")) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && User.ADMIN.equals(rs.getString(1)) && adminCount(c) <= 1)
                            throw new IllegalArgumentException("There must be at least one Admin.");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET role=? WHERE username=?")) {
                ps.setString(1, r);
                ps.setString(2, username);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DbException("Could not change the role: " + e.getMessage(), e);
        }
    }

    public void resetPassword(String username, String newPassword) {
        requireAdmin("manage users");
        checkPassword(newPassword);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash=? WHERE username=?")) {
            ps.setString(1, DBConnection.hash(newPassword));
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Could not reset the password: " + e.getMessage(), e);
        }
    }

    /** Next free ID such as P011, based on the highest existing P### id. */
    public String nextId() {
        int max = 0;
        for (Product p : getAll()) {
            if (p.getId().matches("P\\d+")) {
                try {
                    max = Math.max(max, Integer.parseInt(p.getId().substring(1)));
                } catch (NumberFormatException ignored) { }
            }
        }
        return String.format("P%03d", max + 1);
    }

    // -------------------------------------------------------------- history
    public void log(String productName, String action, int quantity) {
        log(productName, action, quantity, null, null);
    }

    public void log(String productName, String action, int quantity, String note) {
        log(productName, action, quantity, note, null);
    }

    public void log(String productName, String action, int quantity, String note, String batchId) {
        try (Connection c = DBConnection.getConnection()) {
            log(c, productName, action, quantity, note, batchId);
        } catch (SQLException e) {
            throw new DbException("Could not write history: " + e.getMessage(), e);
        }
    }

    private void log(Connection c, String productName, String action, int quantity, String note, String batchId)
            throws SQLException {
        String sql = "INSERT INTO stock_history(event_time,product_name,action,quantity,performed_by,note,batch_id) "
                + "VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setString(2, productName);
            ps.setString(3, action);
            ps.setInt(4, quantity);
            ps.setString(5, currentUser);
            ps.setString(6, note != null && note.length() > 100 ? note.substring(0, 100) : note);
            ps.setString(7, batchId);
            ps.executeUpdate();
        }
    }

    private static final String HISTORY_COLUMNS =
            "event_time,product_name,action,quantity,performed_by,note,batch_id";

    private List<Object[]> readHistory(ResultSet rs) throws SQLException {
        SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Timestamp t = rs.getTimestamp(1);
            rows.add(new Object[]{t == null ? "-" : fmt.format(t), rs.getString(2), rs.getString(3), rs.getInt(4),
                    rs.getString(5), rs.getString(6) == null ? "" : rs.getString(6), t,
                    rs.getString(7) == null ? "" : rs.getString(7)});
        }
        return rows;
    }

    /**
     * @param filter 0 = all, 1 = inventory actions (Added/Updated/Removed), 2 or 3 = stack actions (PUSH/POP,
     *               including the old "Stock In" / "Stock Out" rows)
     * @return rows: {date-time, product, action, quantity, performed by, note, raw Timestamp, batch id}, newest first
     */
    public List<Object[]> getHistory(int filter, int limit) {
        String where = filter == 1 ? " WHERE action IN ('Added','Updated','Removed')"
                : (filter == 2 || filter == 3) ? " WHERE action IN ('PUSH','POP','Stock In','Stock Out')" : "";
        String sql = "SELECT " + HISTORY_COLUMNS + " FROM stock_history" + where
                + " ORDER BY id DESC LIMIT " + Math.max(1, limit);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return readHistory(rs);
        } catch (SQLException e) {
            throw new DbException("Could not load history: " + e.getMessage(), e);
        }
    }

    /**
     * Transaction history / reports: filter by action list, date range and a keyword
     * (product, batch id, user or note). Any filter can be null / empty.
     */
    public List<Object[]> getTransactions(String[] actions, LocalDate from, LocalDate to, String keyword, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + HISTORY_COLUMNS + " FROM stock_history WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (actions != null && actions.length > 0) {
            sql.append(" AND action IN (");
            for (int i = 0; i < actions.length; i++) {
                sql.append(i == 0 ? "?" : ",?");
                args.add(actions[i]);
            }
            sql.append(")");
        }
        if (from != null) {
            sql.append(" AND event_time >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND event_time < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        String k = keyword == null ? "" : keyword.trim();
        if (!k.isEmpty()) {
            sql.append(" AND (product_name LIKE ? OR batch_id LIKE ? OR performed_by LIKE ? OR note LIKE ?)");
            for (int i = 0; i < 4; i++) args.add("%" + k + "%");
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(Math.max(1, limit));
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return readHistory(rs);
            }
        } catch (SQLException e) {
            throw new DbException("Could not load transactions: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------ monthly figures
    /** Labels (Jan, Feb, ...) for the last {@code months} months, oldest first. */
    public static String[] monthLabels(int months) {
        String[] out = new String[months];
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.add(java.util.Calendar.MONTH, -(months - 1));
        SimpleDateFormat f = new SimpleDateFormat("MMM", java.util.Locale.ENGLISH);
        for (int i = 0; i < months; i++) {
            out[i] = f.format(cal.getTime());
            cal.add(java.util.Calendar.MONTH, 1);
        }
        return out;
    }

    /**
     * Units received and removed per month for the last {@code months} months (oldest first).
     * "In" = Added + PUSH (+ legacy Stock In), "Out" = POP + Removed (+ legacy Stock Out).
     * @return {in[], out[]}
     */
    public long[][] monthlyMovement(int months) {
        long[][] r = new long[2][months];
        java.util.Calendar start = java.util.Calendar.getInstance();
        start.set(java.util.Calendar.DAY_OF_MONTH, 1);
        start.set(java.util.Calendar.HOUR_OF_DAY, 0);
        start.set(java.util.Calendar.MINUTE, 0);
        start.set(java.util.Calendar.SECOND, 0);
        start.set(java.util.Calendar.MILLISECOND, 0);
        start.add(java.util.Calendar.MONTH, -(months - 1));
        java.util.Calendar now = java.util.Calendar.getInstance();
        String sql = "SELECT event_time, action, quantity FROM stock_history WHERE event_time >= ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(start.getTimeInMillis()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp t = rs.getTimestamp(1);
                    if (t == null) continue;
                    java.util.Calendar ev = java.util.Calendar.getInstance();
                    ev.setTime(t);
                    int idx = (ev.get(java.util.Calendar.YEAR) - start.get(java.util.Calendar.YEAR)) * 12
                            + ev.get(java.util.Calendar.MONTH) - start.get(java.util.Calendar.MONTH);
                    if (idx < 0 || idx >= months) continue;
                    String a = rs.getString(2);
                    int q = rs.getInt(3);
                    if ("Added".equals(a) || "PUSH".equals(a) || "Stock In".equals(a)) r[0][idx] += q;
                    else if ("POP".equals(a) || "Stock Out".equals(a) || "Removed".equals(a)) r[1][idx] += q;
                }
            }
        } catch (SQLException e) {
            throw new DbException("Could not load monthly figures: " + e.getMessage(), e);
        }
        return r;
    }

    /** Total units in stock at the end of each of the last {@code months} months (rebuilt from history). */
    public double[] monthlyStockTrend(int months) {
        long total = 0;
        for (Product p : getAll()) total += p.getQuantity();
        long[][] mv = monthlyMovement(months);
        double[] v = new double[months];
        v[months - 1] = total;
        for (int i = months - 1; i > 0; i--) {
            v[i - 1] = Math.max(0, v[i] - (mv[0][i] - mv[1][i]));
        }
        return v;
    }

    // ------------------------------------------------------------- password
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!authenticate(username, oldPassword)) return false;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash=? WHERE username=?")) {
            ps.setString(1, DBConnection.hash(newPassword));
            ps.setString(2, username.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new DbException("Could not change password: " + e.getMessage(), e);
        }
    }
}
